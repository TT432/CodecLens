package io.github.tt432.codeclens.extract;

import com.mojang.serialization.MapCodec;
import io.github.tt432.codeclens.schema.CodecSchema;
import org.jetbrains.annotations.Nullable;

/**
 * 自定义 MapCodec 的 schema 提取器。
 * <p>
 * 与 {@link SchemaExtractor} 类似，但针对 {@link MapCodec} 类型。
 *
 * @see SchemaExtractorRegistry#register(MapCodecSchemaExtractor)
 */
@FunctionalInterface
public interface MapCodecSchemaExtractor {

    /**
     * 尝试从给定 mapCodec 提取 schema。
     *
     * @param mapCodec 待提取的 MapCodec
     * @param ctx      提取上下文
     * @return 提取到的 schema，或 {@code null} 表示不处理
     */
    @Nullable
    CodecSchema extract(MapCodec<?> mapCodec, SchemaExtractionContext ctx);
}
