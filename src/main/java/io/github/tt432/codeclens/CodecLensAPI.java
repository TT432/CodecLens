package io.github.tt432.codeclens;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import io.github.tt432.codeclens.error.CodecDiagnostics;
import io.github.tt432.codeclens.error.DiagnosticFormatter;
import io.github.tt432.codeclens.error.FieldDiagnostic;
import io.github.tt432.codeclens.extract.CodecSchemaExtractor;
import io.github.tt432.codeclens.format.CodecSchemaFormatter;
import io.github.tt432.codeclens.schema.CodecSchema;

/**
 * CodecLens 对外统一入口。
 * <p>
 * 提供三类能力：
 * <ul>
 *   <li>Schema 提取 — 将 Codec 反射为结构描述树</li>
 *   <li>Schema 格式化 — 将结构树渲染为人类可读文本</li>
 *   <li>错误诊断 — 在 decode 失败时生成带结构上下文的错误报告</li>
 * </ul>
 *
 * 自定义 Codec 作者可通过
 * {@link io.github.tt432.codeclens.extract.SchemaExtractorRegistry} 注册扩展。
 */
public final class CodecLensAPI {

    private CodecLensAPI() {}

    // ── Schema 提取 ──────────────────────────────────────────

    /**
     * 提取 Codec 的结构描述。
     */
    public static CodecSchema schemaOf(Codec<?> codec) {
        return CodecSchemaExtractor.extract(codec);
    }

    /**
     * 提取 MapCodec 的结构描述。
     */
    public static CodecSchema schemaOf(MapCodec<?> mapCodec) {
        return CodecSchemaExtractor.extract(mapCodec);
    }

    // ── Schema 格式化 ────────────────────────────────────────

    /**
     * 提取并格式化 Codec 的结构为可读文本。
     * <p>
     * 示例输出：
     * <pre>
     * {
     *   endoBase: int,
     *   creditBase: int
     * }
     * </pre>
     */
    public static String describeSchema(Codec<?> codec) {
        return CodecSchemaFormatter.format(schemaOf(codec));
    }

    /**
     * 格式化已有的 schema 为可读文本。
     */
    public static String formatSchema(CodecSchema schema) {
        return CodecSchemaFormatter.format(schema);
    }

    // ── 错误诊断 ─────────────────────────────────────────────

    /**
     * 解码并在失败时返回带结构化诊断的错误消息。
     * <p>
     * 成功时直接返回原始 DataResult。
     * 失败时，错误消息会被替换为结构化的诊断报告。
     *
     * @return 原始 DataResult（成功时）或带增强错误消息的 DataResult（失败时）
     */
    @SuppressWarnings("unchecked")
    public static <T, A> DataResult<A> decodeWithDiagnostics(
            Codec<A> codec, DynamicOps<T> ops, T input) {
        DataResult<A> result = codec.parse(ops, input);
        if (result.isSuccess()) {
            return result;
        }

        // 失败 — 生成结构化诊断
        String report = diagnose(codec, ops, input);
        return DataResult.error(() -> report);
    }

    /**
     * 对给定 codec 和输入生成结构化错误诊断报告。
     * <p>
     * 示例输出：
     * <pre>
     * Codec decode failed:
     * {
     *   ✗ endoBase: int  ← Expected int, got "abc" (got abc)
     *     creditBase: int  ✓
     * }
     * </pre>
     */
    public static <T> String diagnose(Codec<?> codec, DynamicOps<T> ops, T input) {
        FieldDiagnostic diag = CodecDiagnostics.diagnose(codec, ops, input);
        return "Codec decode failed:\n" + DiagnosticFormatter.format(diag);
    }

    /**
     * 获取原始诊断结果树（供程序化处理）。
     */
    public static <T> FieldDiagnostic diagnoseRaw(Codec<?> codec, DynamicOps<T> ops, T input) {
        return CodecDiagnostics.diagnose(codec, ops, input);
    }
}
