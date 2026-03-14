package io.github.tt432.codeclens.extract;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.*;
import io.github.tt432.codeclens.schema.CodecSchema;
import io.github.tt432.codeclens.schema.CodecSchema.*;
import io.github.tt432.codeclens.schema.FieldEntry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;

/**
 * 通过运行时反射将 {@link Codec} 拆解为 {@link CodecSchema} 树。
 * <p>
 * 内置支持 DFU 8.0.16 的所有标准组合器。自定义 Codec 可通过
 * {@link SchemaExtractorRegistry} 扩展。
 */
public final class CodecSchemaExtractor {

    // DFU 内部类名常量
    private static final String FIELD_DECODER_CLASS = "com.mojang.serialization.codecs.FieldDecoder";
    private static final String RCB_CLASS = "com.mojang.serialization.codecs.RecordCodecBuilder";

    private CodecSchemaExtractor() {}

    /**
     * 提取 Codec 的 schema。入口方法。
     */
    public static CodecSchema extract(Codec<?> codec) {
        var ctx = new ExtractionCtx();
        return ctx.extract(codec);
    }

    /**
     * 提取 MapCodec 的 schema。入口方法。
     */
    public static CodecSchema extract(MapCodec<?> mapCodec) {
        var ctx = new ExtractionCtx();
        return ctx.extract(mapCodec);
    }

    // ── 内部实现 ──────────────────────────────────────────────

    private static final class ExtractionCtx implements SchemaExtractionContext {
        private final IdentityHashMap<Object, CodecSchema> visited = new IdentityHashMap<>();

        @Override
        public CodecSchema extract(Codec<?> codec) {
            return extractCodec(codec);
        }

        @Override
        public CodecSchema extract(MapCodec<?> mapCodec) {
            return extractMapCodec(mapCodec);
        }

        @Override
        public CodecSchema extractObject(Object obj) {
            return extractAny(obj);
        }

        CodecSchema extractCodec(Codec<?> codec) {
            if (codec == null) return new UnknownSchema("null");
            if (visited.containsKey(codec)) return visited.get(codec);

            // 占位防循环
            var placeholder = new UnknownSchema("<circular>");
            visited.put(codec, placeholder);

            CodecSchema result = doExtractCodec(codec);
            visited.put(codec, result);
            return result;
        }

        private CodecSchema doExtractCodec(Codec<?> codec) {
            // 1. 自定义提取器优先
            CodecSchema custom = SchemaExtractorRegistry.tryCustomCodecExtractors(codec, this);
            if (custom != null) return custom;

            // 2. 基本类型单例
            CodecSchema prim = tryPrimitive(codec);
            if (prim != null) return prim;

            // 3. MapCodecCodec → 解包
            if (codec instanceof MapCodec.MapCodecCodec<?> mcc) {
                return extractMapCodec(mcc.codec());
            }

            // 4. EitherCodec (record)
            if (codec instanceof EitherCodec<?, ?> either) {
                return new EitherSchema(extractCodec(either.first()), extractCodec(either.second()));
            }

            // 5. ListCodec (record)
            if (codec instanceof ListCodec<?> list) {
                return new ListSchema(extractCodec(list.elementCodec()));
            }

            // 6. UnboundedMapCodec (record)
            if (codec instanceof UnboundedMapCodec<?, ?> map) {
                return new MapSchema(extractCodec(map.keyCodec()), extractCodec(map.elementCodec()));
            }

            // 7. PairCodec
            if (codec instanceof PairCodec<?, ?>) {
                var first = ReflectUtil.getFieldValue(codec, "first");
                var second = ReflectUtil.getFieldValue(codec, "second");
                return new PairSchema(extractAny(first), extractAny(second));
            }

            // 8. XorCodec (record)
            // XorCodec 不一定在所有 DFU 版本都是 public，用反射兜底
            CodecSchema xor = tryXorCodec(codec);
            if (xor != null) return xor;

            // 9. xmap / flatXmap 包装 — 尝试解包内部 codec
            CodecSchema unwrapped = tryUnwrapMapped(codec);
            if (unwrapped != null) return unwrapped;

            // 10. 兜底
            return new UnknownSchema(codec.toString());
        }

        CodecSchema extractMapCodec(MapCodec<?> mapCodec) {
            if (mapCodec == null) return new UnknownSchema("null");
            if (visited.containsKey(mapCodec)) return visited.get(mapCodec);

            var placeholder = new UnknownSchema("<circular>");
            visited.put(mapCodec, placeholder);

            CodecSchema result = doExtractMapCodec(mapCodec);
            visited.put(mapCodec, result);
            return result;
        }

        private CodecSchema doExtractMapCodec(MapCodec<?> mapCodec) {
            // 自定义提取器
            CodecSchema custom = SchemaExtractorRegistry.tryCustomMapCodecExtractors(mapCodec, this);
            if (custom != null) return custom;

            // OptionalFieldCodec
            if (mapCodec instanceof OptionalFieldCodec<?>) {
                String name = (String) ReflectUtil.getFieldValue(mapCodec, "name");
                Object elementCodec = ReflectUtil.getFieldValue(mapCodec, "elementCodec");
                CodecSchema inner = extractAny(elementCodec);
                // OptionalFieldCodec 本身就是一个字段，但在 record 上下文中会被收集为 FieldEntry
                // 如果单独提取，返回 RecordSchema 包含单个 optional 字段
                return new RecordSchema(List.of(new FieldEntry(
                        name != null ? name : "<unknown>", inner, true)));
            }

            // FieldDecoder
            if (mapCodec.getClass().getName().equals(FIELD_DECODER_CLASS)) {
                String name = (String) ReflectUtil.getFieldValue(mapCodec, "name");
                Object elementCodec = ReflectUtil.getFieldValue(mapCodec, "elementCodec");
                CodecSchema inner = extractAny(elementCodec);
                return new RecordSchema(List.of(new FieldEntry(
                        name != null ? name : "<unknown>", inner, false)));
            }

            // EitherMapCodec
            if (mapCodec.getClass().getName().contains("EitherMapCodec")) {
                var first = ReflectUtil.getFieldValue(mapCodec, "first");
                var second = ReflectUtil.getFieldValue(mapCodec, "second");
                return new EitherSchema(extractAny(first), extractAny(second));
            }

            // KeyDispatchCodec
            if (mapCodec instanceof KeyDispatchCodec<?, ?>) {
                String typeKey = (String) ReflectUtil.getFieldValue(mapCodec, "typeKey");
                Object keyCodec = ReflectUtil.getFieldValue(mapCodec, "keyCodec");
                return new DispatchSchema(
                        typeKey != null ? typeKey : "type",
                        extractAny(keyCodec));
            }

            // RecordCodecBuilder 产物 — 收集叶子字段
            List<FieldEntry> fields = collectRecordFields(mapCodec);
            if (!fields.isEmpty()) {
                return new RecordSchema(fields);
            }

            return new UnknownSchema(mapCodec.toString());
        }

        /**
         * 从 RecordCodecBuilder 产物中收集所有叶子字段。
         */
        private List<FieldEntry> collectRecordFields(Object root) {
            var leaves = new ArrayList<Object>();
            var leafVisited = new IdentityHashMap<Object, Boolean>();
            collectLeaves(root, leaves, leafVisited);

            var fields = new ArrayList<FieldEntry>();
            for (Object leaf : leaves) {
                FieldEntry entry = leafToFieldEntry(leaf);
                if (entry != null) fields.add(entry);
            }
            return fields;
        }

        private void collectLeaves(Object obj, List<Object> result, IdentityHashMap<Object, Boolean> leafVisited) {
            if (obj == null || leafVisited.containsKey(obj)) return;
            leafVisited.put(obj, Boolean.TRUE);

            String cn = obj.getClass().getName();

            // 叶子：FieldDecoder
            if (cn.equals(FIELD_DECODER_CLASS)) {
                result.add(obj);
                return;
            }

            // 叶子：OptionalFieldCodec
            if (obj instanceof OptionalFieldCodec<?>) {
                result.add(obj);
                return;
            }

            // MapCodecCodec → 解包
            if (obj instanceof MapCodec.MapCodecCodec<?> mcc) {
                collectLeaves(mcc.codec(), result, leafVisited);
                return;
            }

            // RecordCodecBuilder → decoder 字段
            if (cn.equals(RCB_CLASS)) {
                collectLeaves(ReflectUtil.getFieldValue(obj, "decoder"), result, leafVisited);
                return;
            }

            // MapDecoder / MapCodec — 遍历所有字段找子节点
            if (obj instanceof MapDecoder<?> || obj instanceof MapCodec<?>) {
                for (Field f : ReflectUtil.allFields(obj.getClass())) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(obj);
                        if (v == null || v == obj) continue;
                        String vn = v.getClass().getName();
                        if (vn.equals(RCB_CLASS)
                                || v instanceof MapDecoder<?>
                                || v instanceof MapCodec<?>) {
                            collectLeaves(v, result, leafVisited);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        @Nullable
        private FieldEntry leafToFieldEntry(Object leaf) {
            String cn = leaf.getClass().getName();

            if (cn.equals(FIELD_DECODER_CLASS)) {
                String name = (String) ReflectUtil.getFieldValue(leaf, "name");
                Object elementCodec = ReflectUtil.getFieldValue(leaf, "elementCodec");
                return new FieldEntry(
                        name != null ? name : "<unknown>",
                        extractAny(elementCodec),
                        false);
            }

            if (leaf instanceof OptionalFieldCodec<?>) {
                String name = (String) ReflectUtil.getFieldValue(leaf, "name");
                Object elementCodec = ReflectUtil.getFieldValue(leaf, "elementCodec");
                return new FieldEntry(
                        name != null ? name : "<unknown>",
                        extractAny(elementCodec),
                        true);
            }

            return null;
        }

        /**
         * 通用入口：根据对象实际类型分发到 extractCodec / extractMapCodec。
         */
        CodecSchema extractAny(@Nullable Object obj) {
            if (obj == null) return new UnknownSchema("null");
            if (obj instanceof Codec<?> c) return extractCodec(c);
            if (obj instanceof MapCodec<?> mc) return extractMapCodec(mc);
            if (obj instanceof MapDecoder<?>) {
                // 可能是 FieldDecoder 等非 MapCodec 的 MapDecoder
                String cn = obj.getClass().getName();
                if (cn.equals(FIELD_DECODER_CLASS)) {
                    String name = (String) ReflectUtil.getFieldValue(obj, "name");
                    Object elementCodec = ReflectUtil.getFieldValue(obj, "elementCodec");
                    return new RecordSchema(List.of(new FieldEntry(
                            name != null ? name : "<unknown>",
                            extractAny(elementCodec),
                            false)));
                }
            }
            // Decoder 但不是 Codec — 尝试反射找内部 codec
            if (obj instanceof Decoder<?>) {
                Object inner = ReflectUtil.getFieldValue(obj, "codec");
                if (inner instanceof Codec<?> c) return extractCodec(c);
                inner = ReflectUtil.getFieldValue(obj, "elementCodec");
                if (inner instanceof Codec<?> c) return extractCodec(c);
            }
            return new UnknownSchema(obj.toString());
        }

        // ── 辅助方法 ──────────────────────────────────────────

        @Nullable
        private static CodecSchema tryPrimitive(Codec<?> codec) {
            if (codec == Codec.INT) return new PrimitiveSchema("int");
            if (codec == Codec.FLOAT) return new PrimitiveSchema("float");
            if (codec == Codec.DOUBLE) return new PrimitiveSchema("double");
            if (codec == Codec.LONG) return new PrimitiveSchema("long");
            if (codec == Codec.SHORT) return new PrimitiveSchema("short");
            if (codec == Codec.BYTE) return new PrimitiveSchema("byte");
            if (codec == Codec.BOOL) return new PrimitiveSchema("bool");
            if (codec == Codec.STRING) return new PrimitiveSchema("string");
            if (codec == ResourceLocation.CODEC) return new PrimitiveSchema("resource_location");

            // toString 启发式
            String s = codec.toString();
            if (s.equals("int")) return new PrimitiveSchema("int");
            if (s.equals("float")) return new PrimitiveSchema("float");
            if (s.equals("double")) return new PrimitiveSchema("double");
            if (s.equals("long")) return new PrimitiveSchema("long");
            if (s.equals("bool")) return new PrimitiveSchema("bool");
            if (s.equals("string")) return new PrimitiveSchema("string");

            return null;
        }

        @Nullable
        private CodecSchema tryXorCodec(Codec<?> codec) {
            String cn = codec.getClass().getName();
            if (cn.contains("XorCodec")) {
                // XorCodec 是 record，尝试 accessor
                var first = ReflectUtil.getFieldValue(codec, "first");
                var second = ReflectUtil.getFieldValue(codec, "second");
                if (first != null && second != null) {
                    return new XorSchema(extractAny(first), extractAny(second));
                }
            }
            return null;
        }

        /**
         * 尝试解包 xmap / flatXmap / comapFlatMap 等包装。
         * 这些包装类通常持有一个内部 codec 字段。
         */
        @Nullable
        private CodecSchema tryUnwrapMapped(Codec<?> codec) {
            // MappedCodec 模式：内部有 codec / elementCodec 字段
            for (String fieldName : List.of("codec", "elementCodec", "wrapped", "delegate")) {
                Object inner = ReflectUtil.getFieldValue(codec, fieldName);
                if (inner != null && inner != codec) {
                    if (inner instanceof Codec<?> c) return extractCodec(c);
                    if (inner instanceof MapCodec<?> mc) return extractMapCodec(mc);
                }
            }
            return null;
        }
    }
}
