package io.github.tt432.codeclens.schema;

/**
 * Record / object 中的一个字段。
 *
 * @param name        字段名（JSON key）
 * @param valueSchema 字段值的 schema
 * @param optional    是否为 optional 字段（来自 {@code optionalFieldOf}）
 */
public record FieldEntry(String name, CodecSchema valueSchema, boolean optional) {
}
