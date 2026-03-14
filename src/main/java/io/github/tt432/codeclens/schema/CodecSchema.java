package io.github.tt432.codeclens.schema;

import java.util.List;

/**
 * Codec 结构的不可变描述树。
 * <p>
 * 每个节点对应一种 Codec 组合方式。自定义 Codec 作者可通过
 * {@link io.github.tt432.codeclens.extract.SchemaExtractorRegistry} 注册自己的提取逻辑，
 * 并返回这些节点的组合，或使用 {@link UnknownSchema} / {@link PrimitiveSchema} 兜底。
 */
public sealed interface CodecSchema {

    /**
     * 基本类型：int, float, double, long, short, byte, bool, string 等。
     */
    record PrimitiveSchema(String type) implements CodecSchema {}

    /**
     * Record / object 结构，由 {@code RecordCodecBuilder} 产生。
     */
    record RecordSchema(List<FieldEntry> fields) implements CodecSchema {
        public RecordSchema {
            fields = List.copyOf(fields);
        }
    }

    /**
     * Either 二选一（先尝试 left，失败再尝试 right）。
     * 对应 {@code Codec.either(a, b)} 和 {@code Codec.withAlternative(a, b)}。
     */
    record EitherSchema(CodecSchema left, CodecSchema right) implements CodecSchema {}

    /**
     * Xor 互斥二选一（恰好一个成功）。
     * 对应 {@code Codec.xor(a, b)}。
     */
    record XorSchema(CodecSchema left, CodecSchema right) implements CodecSchema {}

    /**
     * 列表。对应 {@code codec.listOf()}。
     */
    record ListSchema(CodecSchema element) implements CodecSchema {}

    /**
     * 无界 Map。对应 {@code Codec.unboundedMap(k, v)}。
     */
    record MapSchema(CodecSchema key, CodecSchema value) implements CodecSchema {}

    /**
     * Dispatch（类型分发）。对应 {@code KeyDispatchCodec}。
     * <p>
     * 只记录 typeKey 和 key 的 schema，因为 value 的 codec 在运行时由 key 决定。
     */
    record DispatchSchema(String typeKey, CodecSchema keySchema) implements CodecSchema {}

    /**
     * Pair 组合。对应 {@code Codec.pair(a, b)}。
     */
    record PairSchema(CodecSchema first, CodecSchema second) implements CodecSchema {}

    /**
     * 兜底：无法识别的 Codec。description 通常取自 {@code codec.toString()}。
     */
    record UnknownSchema(String description) implements CodecSchema {}
}
