package io.github.tt432.codeclens.extract;

import com.mojang.serialization.Codec;
import io.github.tt432.codeclens.schema.CodecSchema;
import org.jetbrains.annotations.Nullable;

/**
 * 自定义 Codec 的 schema 提取器。
 * <p>
 * 实现此接口并通过 {@link SchemaExtractorRegistry#register} 注册，
 * 即可让 CodecLens 识别你的自定义 Codec 类型。
 * <p>
 * 示例：
 * <pre>{@code
 * SchemaExtractorRegistry.register(new SchemaExtractor() {
 *     @Override
 *     public @Nullable CodecSchema extract(Codec<?> codec, SchemaExtractionContext ctx) {
 *         if (codec instanceof MyCustomCodec<?> custom) {
 *             return new CodecSchema.ListSchema(ctx.extract(custom.innerCodec()));
 *         }
 *         return null; // 不认识，交给下一个
 *     }
 * });
 * }</pre>
 */
@FunctionalInterface
public interface SchemaExtractor {

    /**
     * 尝试从给定 codec 提取 schema。
     *
     * @param codec 待提取的 codec
     * @param ctx   提取上下文，可用于递归提取子 codec
     * @return 提取到的 schema，或 {@code null} 表示此提取器不处理该 codec
     */
    @Nullable
    CodecSchema extract(Codec<?> codec, SchemaExtractionContext ctx);
}
