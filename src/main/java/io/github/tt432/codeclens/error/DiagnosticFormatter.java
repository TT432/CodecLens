package io.github.tt432.codeclens.error;

import io.github.tt432.codeclens.format.CodecSchemaFormatter;
import io.github.tt432.codeclens.schema.CodecSchema;

/**
 * 将 {@link FieldDiagnostic} 格式化为人类可读的错误报告。
 * <p>
 * 输出风格参考 Zod / TypeScript 的结构化错误：
 * <pre>
 * {
 *   [FAIL] endoBase: int          ← Expected int, got "abc"
 *          creditBase: int        ✓
 * }
 * </pre>
 */
public final class DiagnosticFormatter {

    private static final String INDENT = "  ";
    private static final String MARKER_OK = "  ";
    private static final String MARKER_FAIL = "✗ ";

    private DiagnosticFormatter() {}

    /**
     * 格式化诊断结果为可读文本。
     */
    public static String format(FieldDiagnostic diagnostic) {
        var sb = new StringBuilder();
        format(diagnostic, sb, 0);
        return sb.toString();
    }

    private static void format(FieldDiagnostic diag, StringBuilder sb, int indent) {
        switch (diag) {
            case FieldDiagnostic.Ok ok -> {
                sb.append(MARKER_OK);
                sb.append(CodecSchemaFormatter.format(ok.schema()));
                sb.append("  ✓");
            }

            case FieldDiagnostic.Fail fail -> {
                sb.append(MARKER_FAIL);
                sb.append(CodecSchemaFormatter.format(fail.schema()));
                sb.append("  ← ").append(fail.message());
                if (fail.input() != null) {
                    sb.append(" (got ").append(truncate(fail.input(), 50)).append(')');
                }
            }

            case FieldDiagnostic.RecordDiag record -> formatRecord(record, sb, indent);

            case FieldDiagnostic.EitherDiag either -> formatEither(either, sb, indent);
        }
    }

    private static void formatRecord(FieldDiagnostic.RecordDiag record, StringBuilder sb, int indent) {
        sb.append("{\n");
        for (var field : record.fields()) {
            appendIndent(sb, indent + 1);

            boolean hasError = field.diagnostic().hasError();

            if (field.diagnostic() instanceof FieldDiagnostic.RecordDiag nested) {
                // 嵌套 record
                if (hasError) sb.append(MARKER_FAIL);
                else sb.append(MARKER_OK);
                sb.append(field.name()).append(": ");
                if (field.optional()) sb.append('?');
                formatRecord(nested, sb, indent + 1);
            } else if (field.diagnostic() instanceof FieldDiagnostic.EitherDiag either) {
                // Either 字段
                if (hasError) sb.append(MARKER_FAIL);
                else sb.append(MARKER_OK);
                sb.append(field.name()).append(": ");
                if (field.optional()) sb.append('?');
                formatEither(either, sb, indent + 1);
            } else if (field.diagnostic() instanceof FieldDiagnostic.Fail fail) {
                sb.append(MARKER_FAIL);
                sb.append(field.name()).append(": ");
                sb.append(CodecSchemaFormatter.format(fail.schema()));
                if (field.optional()) sb.append('?');
                sb.append("  ← ").append(fail.message());
                if (fail.input() != null) {
                    sb.append(" (got ").append(truncate(fail.input(), 50)).append(')');
                }
            } else {
                // Ok
                sb.append(MARKER_OK);
                sb.append(field.name()).append(": ");
                sb.append(CodecSchemaFormatter.format(field.diagnostic().schema()));
                if (field.optional()) sb.append('?');
                sb.append("  ✓");
            }
            sb.append('\n');
        }
        appendIndent(sb, indent);
        sb.append('}');
    }

    private static void formatEither(FieldDiagnostic.EitherDiag either, StringBuilder sb, int indent) {
        boolean leftFail = either.left().hasError();
        boolean rightFail = either.right().hasError();

        if (!leftFail || !rightFail) {
            // 至少一个成功 — 简洁显示
            formatBranch(either.left(), sb, indent, "left");
            sb.append(" | ");
            formatBranch(either.right(), sb, indent, "right");
        } else {
            // 都失败 — 展开显示每个分支的错误
            sb.append('\n');
            appendIndent(sb, indent);
            sb.append("├─ ");
            formatBranchDetail(either.left(), sb, indent + 1);
            sb.append('\n');
            appendIndent(sb, indent);
            sb.append("└─ ");
            formatBranchDetail(either.right(), sb, indent + 1);
        }
    }

    private static void formatBranch(FieldDiagnostic diag, StringBuilder sb, int indent, String label) {
        if (diag.hasError()) {
            sb.append(MARKER_FAIL);
        }
        sb.append(CodecSchemaFormatter.format(diag.schema()));
    }

    private static void formatBranchDetail(FieldDiagnostic diag, StringBuilder sb, int indent) {
        if (diag instanceof FieldDiagnostic.Fail fail) {
            sb.append(MARKER_FAIL);
            sb.append(CodecSchemaFormatter.format(fail.schema()));
            sb.append("  ← ").append(fail.message());
            if (fail.input() != null) {
                sb.append(" (got ").append(truncate(fail.input(), 50)).append(')');
            }
        } else {
            format(diag, sb, indent);
        }
    }

    private static void appendIndent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) sb.append(INDENT);
    }

    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
