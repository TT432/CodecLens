package io.github.tt432.codeclens.format;

import io.github.tt432.codeclens.schema.CodecSchema;
import io.github.tt432.codeclens.schema.CodecSchema.*;
import io.github.tt432.codeclens.schema.FieldEntry;

/**
 * 将 {@link CodecSchema} 格式化为人类可读的文本。
 */
public final class CodecSchemaFormatter {

    private CodecSchemaFormatter() {}

    /**
     * 格式化 schema 为可读文本。
     */
    public static String format(CodecSchema schema) {
        var sb = new StringBuilder();
        format(schema, sb, 0);
        return sb.toString();
    }

    private static void format(CodecSchema schema, StringBuilder sb, int indent) {
        switch (schema) {
            case PrimitiveSchema p -> sb.append(p.type());

            case RecordSchema r -> {
                sb.append("{\n");
                for (int i = 0; i < r.fields().size(); i++) {
                    FieldEntry f = r.fields().get(i);
                    appendIndent(sb, indent + 1);
                    sb.append(f.name()).append(": ");
                    format(f.valueSchema(), sb, indent + 1);
                    if (f.optional()) sb.append('?');
                    if (i < r.fields().size() - 1) sb.append(',');
                    sb.append('\n');
                }
                appendIndent(sb, indent);
                sb.append('}');
            }

            case EitherSchema e -> {
                formatInline(e.left(), sb, indent);
                sb.append(" | ");
                formatInline(e.right(), sb, indent);
            }

            case XorSchema x -> {
                formatInline(x.left(), sb, indent);
                sb.append(" ^ ");
                formatInline(x.right(), sb, indent);
            }

            case ListSchema l -> {
                sb.append('[');
                formatInline(l.element(), sb, indent);
                sb.append(']');
            }

            case MapSchema m -> {
                sb.append("map<");
                formatInline(m.key(), sb, indent);
                sb.append(", ");
                formatInline(m.value(), sb, indent);
                sb.append('>');
            }

            case DispatchSchema d -> {
                sb.append("dispatch(\"").append(d.typeKey()).append("\": ");
                formatInline(d.keySchema(), sb, indent);
                sb.append(')');
            }

            case PairSchema p -> {
                sb.append("pair<");
                formatInline(p.first(), sb, indent);
                sb.append(", ");
                formatInline(p.second(), sb, indent);
                sb.append('>');
            }

            case UnknownSchema u -> sb.append(u.description());
        }
    }

    /**
     * 内联格式化：RecordSchema 也展开为多行，其他类型直接内联。
     */
    private static void formatInline(CodecSchema schema, StringBuilder sb, int indent) {
        format(schema, sb, indent);
    }

    private static void appendIndent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append("  ");
    }
}
