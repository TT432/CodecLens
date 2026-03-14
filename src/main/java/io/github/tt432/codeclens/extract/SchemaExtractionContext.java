package io.github.tt432.codeclens.extract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.tt432.codeclens.schema.CodecSchema;

/**
 * 提取上下文，传递给 {@link SchemaExtractor} 以支持递归提取。
 */
public interface SchemaExtractionContext {

    /**
     * 递归提取子 Codec 的 schema。
     */
    CodecSchema extract(Codec<?> codec);

    /**
     * 递归提取子 MapCodec 的 schema。
     */
    CodecSchema extract(MapCodec<?> mapCodec);

    /**
     * 递归提取任意对象（Decoder / Encoder / 其他包装）。
     * 用于反射拆解内部结构时的通用入口。
     */
    CodecSchema extractObject(Object obj);
}
