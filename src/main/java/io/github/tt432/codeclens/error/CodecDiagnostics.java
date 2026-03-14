package io.github.tt432.codeclens.error;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.*;
import io.github.tt432.codeclens.extract.CodecSchemaExtractor;
import io.github.tt432.codeclens.extract.ReflectUtil;
import io.github.tt432.codeclens.schema.CodecSchema;
import io.github.tt432.codeclens.schema.CodecSchema.*;
import io.github.tt432.codeclens.schema.FieldEntry;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * Codec 解析错误的结构化诊断。
 * <p>
 * 在 decode 失败后，利用 schema 树对输入数据逐字段重新检测，
 * 定位具体哪些字段出了问题。
 */
public final class CodecDiagnostics {

    private static final String FIELD_DECODER_CLASS = "com.mojang.serialization.codecs.FieldDecoder";

    private CodecDiagnostics() {}

    /**
     * 对给定 codec 和输入进行诊断。
     *
     * @return 诊断结果树，如果 codec 无法提取 schema 则返回简单的 Fail
     */
    public static <T> FieldDiagnostic diagnose(Codec<?> codec, DynamicOps<T> ops, T input) {
        CodecSchema schema = CodecSchemaExtractor.extract(codec);
        return diagnoseWithSchema(schema, codec, ops, input);
    }

    /**
     * 使用已有 schema 进行诊断。
     */
    public static <T> FieldDiagnostic diagnoseWithSchema(
            CodecSchema schema, Codec<?> codec, DynamicOps<T> ops, T input) {
        return doDiagnose(schema, codec, ops, input);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> FieldDiagnostic doDiagnose(
            CodecSchema schema, @Nullable Object codec, DynamicOps<T> ops, T input) {

        if (schema instanceof RecordSchema record && codec != null) {
            return diagnoseRecord(record, codec, ops, input);
        }

        if (schema instanceof EitherSchema either && codec instanceof EitherCodec<?, ?> eitherCodec) {
            return diagnoseEither(either, eitherCodec, ops, input);
        }

        // 对于其他类型，直接尝试 decode
        if (codec instanceof Codec<?> c) {
            DataResult<?> result = ((Codec) c).parse(ops, input);
            if (result.isSuccess()) {
                return new FieldDiagnostic.Ok(schema);
            }
            String msg = result.error().map(e -> ((DataResult.Error<?>) e).message()).orElse("Unknown error");
            return new FieldDiagnostic.Fail(schema, msg, ops.getStringValue(input).result().orElse(String.valueOf(input)));
        }

        return new FieldDiagnostic.Ok(schema);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> FieldDiagnostic diagnoseRecord(
            RecordSchema schema, Object codec, DynamicOps<T> ops, T input) {

        // 获取 input 作为 map
        Optional<MapLike<T>> mapOpt = ops.getMap(input).result();
        if (mapOpt.isEmpty()) {
            return new FieldDiagnostic.Fail(schema, "Expected an object/map", String.valueOf(input));
        }
        MapLike<T> map = mapOpt.get();

        // 收集原始 codec 中的叶子节点，按字段名映射到对应的 elementCodec
        Map<String, LeafInfo> leafMap = collectLeafCodecs(codec);

        var fieldResults = new ArrayList<FieldDiagnostic.FieldResult>();

        for (FieldEntry field : schema.fields()) {
            T fieldValue = map.get(field.name());

            if (fieldValue == null) {
                if (field.optional()) {
                    fieldResults.add(new FieldDiagnostic.FieldResult(
                            field.name(), true, new FieldDiagnostic.Ok(field.valueSchema())));
                } else {
                    fieldResults.add(new FieldDiagnostic.FieldResult(
                            field.name(), false,
                            new FieldDiagnostic.Fail(field.valueSchema(), "Missing required field", null)));
                }
                continue;
            }

            // 找到该字段对应的 elementCodec，尝试单独 decode
            LeafInfo leaf = leafMap.get(field.name());
            if (leaf != null && leaf.elementCodec instanceof Codec<?> elemCodec) {
                FieldDiagnostic diag = doDiagnose(field.valueSchema(), elemCodec, ops, fieldValue);
                fieldResults.add(new FieldDiagnostic.FieldResult(field.name(), field.optional(), diag));
            } else {
                // 没有找到 elementCodec，无法单独诊断
                fieldResults.add(new FieldDiagnostic.FieldResult(
                        field.name(), field.optional(), new FieldDiagnostic.Ok(field.valueSchema())));
            }
        }

        return new FieldDiagnostic.RecordDiag(schema, fieldResults);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> FieldDiagnostic diagnoseEither(
            EitherSchema schema, EitherCodec<?, ?> eitherCodec, DynamicOps<T> ops, T input) {

        Codec<?> first = eitherCodec.first();
        Codec<?> second = eitherCodec.second();

        FieldDiagnostic leftDiag = doDiagnose(schema.left(), first, ops, input);
        FieldDiagnostic rightDiag = doDiagnose(schema.right(), second, ops, input);

        return new FieldDiagnostic.EitherDiag(schema, leftDiag, rightDiag);
    }

    // ── 叶子节点收集 ──────────────────────────────────────────

    private record LeafInfo(String name, Object elementCodec, boolean optional) {}

    private static Map<String, LeafInfo> collectLeafCodecs(Object codec) {
        var result = new LinkedHashMap<String, LeafInfo>();
        var leaves = new ArrayList<Object>();
        var visited = new IdentityHashMap<Object, Boolean>();

        // 解包 MapCodecCodec
        Object target = codec;
        if (codec instanceof MapCodec.MapCodecCodec<?> mcc) {
            target = mcc.codec();
        }

        collectRawLeaves(target, leaves, visited);

        for (Object leaf : leaves) {
            String cn = leaf.getClass().getName();
            if (cn.equals(FIELD_DECODER_CLASS)) {
                String name = (String) ReflectUtil.getFieldValue(leaf, "name");
                Object elemCodec = ReflectUtil.getFieldValue(leaf, "elementCodec");
                if (name != null && elemCodec != null) {
                    result.put(name, new LeafInfo(name, elemCodec, false));
                }
            } else if (leaf instanceof OptionalFieldCodec<?>) {
                String name = (String) ReflectUtil.getFieldValue(leaf, "name");
                Object elemCodec = ReflectUtil.getFieldValue(leaf, "elementCodec");
                if (name != null && elemCodec != null) {
                    result.put(name, new LeafInfo(name, elemCodec, true));
                }
            }
        }

        return result;
    }

    private static void collectRawLeaves(Object obj, List<Object> result, IdentityHashMap<Object, Boolean> visited) {
        if (obj == null || visited.containsKey(obj)) return;
        visited.put(obj, Boolean.TRUE);

        String cn = obj.getClass().getName();

        if (cn.equals(FIELD_DECODER_CLASS) || obj instanceof OptionalFieldCodec<?>) {
            result.add(obj);
            return;
        }

        if (obj instanceof MapCodec.MapCodecCodec<?> mcc) {
            collectRawLeaves(mcc.codec(), result, visited);
            return;
        }

        if (cn.equals("com.mojang.serialization.codecs.RecordCodecBuilder")) {
            collectRawLeaves(ReflectUtil.getFieldValue(obj, "decoder"), result, visited);
            return;
        }

        if (obj instanceof MapDecoder<?> || obj instanceof MapCodec<?>) {
            for (var f : ReflectUtil.allFields(obj.getClass())) {
                f.setAccessible(true);
                try {
                    Object v = f.get(obj);
                    if (v == null || v == obj) continue;
                    String vn = v.getClass().getName();
                    if (vn.equals("com.mojang.serialization.codecs.RecordCodecBuilder")
                            || v instanceof MapDecoder<?>
                            || v instanceof MapCodec<?>) {
                        collectRawLeaves(v, result, visited);
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
