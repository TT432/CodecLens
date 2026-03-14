package io.github.tt432.codeclens.error;

import io.github.tt432.codeclens.schema.CodecSchema;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 单个 schema 节点的诊断结果。
 */
public sealed interface FieldDiagnostic {

    /**
     * 该节点是否有错误（递归检查子节点）。
     */
    boolean hasError();

    /**
     * 该节点对应的 schema。
     */
    CodecSchema schema();

    /**
     * 字段解析成功。
     */
    record Ok(CodecSchema schema) implements FieldDiagnostic {
        @Override
        public boolean hasError() { return false; }
    }

    /**
     * 字段解析失败。
     *
     * @param schema  该字段的 schema
     * @param message DFU 返回的原始错误消息
     * @param input   导致错误的输入值的字符串表示
     */
    record Fail(CodecSchema schema, String message, @Nullable String input) implements FieldDiagnostic {
        @Override
        public boolean hasError() { return true; }
    }

    /**
     * Record 结构的诊断：包含每个字段的诊断结果。
     */
    record RecordDiag(CodecSchema schema, List<FieldResult> fields) implements FieldDiagnostic {
        @Override
        public boolean hasError() {
            return fields.stream().anyMatch(f -> f.diagnostic().hasError());
        }
    }

    /**
     * Either 结构的诊断：left 和 right 各自的结果。
     */
    record EitherDiag(CodecSchema schema, FieldDiagnostic left, FieldDiagnostic right) implements FieldDiagnostic {
        @Override
        public boolean hasError() {
            return left.hasError() && right.hasError();
        }
    }

    /**
     * RecordDiag 中的单个字段结果。
     */
    record FieldResult(String name, boolean optional, FieldDiagnostic diagnostic) {}
}
