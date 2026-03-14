package io.github.tt432.codeclens.extract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.tt432.codeclens.schema.CodecSchema;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 自定义 {@link SchemaExtractor} 的注册表。
 * <p>
 * 自定义提取器优先于内置逻辑执行。第一个返回非 null 结果的提取器胜出。
 * <p>
 * 同时支持 {@link MapCodecSchemaExtractor} 用于自定义 MapCodec 类型。
 */
public final class SchemaExtractorRegistry {

    private static final CopyOnWriteArrayList<SchemaExtractor> CODEC_EXTRACTORS = new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<MapCodecSchemaExtractor> MAP_CODEC_EXTRACTORS = new CopyOnWriteArrayList<>();

    private SchemaExtractorRegistry() {}

    /**
     * 注册一个自定义 Codec 提取器。后注册的优先级更高。
     */
    public static void register(SchemaExtractor extractor) {
        CODEC_EXTRACTORS.add(0, extractor);
    }

    /**
     * 注册一个自定义 MapCodec 提取器。后注册的优先级更高。
     */
    public static void register(MapCodecSchemaExtractor extractor) {
        MAP_CODEC_EXTRACTORS.add(0, extractor);
    }

    @Nullable
    static CodecSchema tryCustomCodecExtractors(Codec<?> codec, SchemaExtractionContext ctx) {
        for (SchemaExtractor ext : CODEC_EXTRACTORS) {
            CodecSchema result = ext.extract(codec, ctx);
            if (result != null) return result;
        }
        return null;
    }

    @Nullable
    static CodecSchema tryCustomMapCodecExtractors(MapCodec<?> mapCodec, SchemaExtractionContext ctx) {
        for (MapCodecSchemaExtractor ext : MAP_CODEC_EXTRACTORS) {
            CodecSchema result = ext.extract(mapCodec, ctx);
            if (result != null) return result;
        }
        return null;
    }
}
