package io.github.tt432.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Decoder;
import io.github.tt432.codeclens.error.CodecDiagnostics;
import io.github.tt432.codeclens.error.DiagnosticFormatter;
import io.github.tt432.codeclens.error.FieldDiagnostic;
import io.github.tt432.codeclens.extract.CodecSchemaExtractor;
import io.github.tt432.codeclens.format.CodecSchemaFormatter;
import io.github.tt432.codeclens.schema.CodecSchema;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.Reader;
import java.util.Map;

/**
 * 拦截 RegistryDataLoader 的数据包加载流程。
 * 在 JSON 解析失败时，自动输出结构化的 Codec 诊断信息到日志。
 * <p>
 * 用户只需安装 mod，无需任何配置。
 */
@Mixin(RegistryDataLoader.class)
public abstract class RegistryDataLoaderMixin {

    @Unique
    private static final Logger CODECLENS_LOGGER = LoggerFactory.getLogger("CodecLens");

    // ── ThreadLocal：在 loadElementFromResource 入口捕获参数，
    //    在 loadContentsFromManager / loadContentsFromNetwork 的 catch 块中消费。──

    @Unique
    private static final ThreadLocal<Decoder<?>> codeclens$codec = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<RegistryOps<?>> codeclens$ops = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Resource> codeclens$resource = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<ResourceKey<?>> codeclens$key = new ThreadLocal<>();

    /**
     * 在 loadElementFromResource 入口捕获参数。
     * 无论调用者是 loadContentsFromManager 还是 loadContentsFromNetwork，都会经过这里。
     */
    @Inject(method = "loadElementFromResource", at = @At("HEAD"))
    private static <E> void codeclens$onLoadElement(
            WritableRegistry<E> registry,
            Decoder<E> codec,
            RegistryOps<JsonElement> ops,
            ResourceKey<E> resourceKey,
            Resource resource,
            RegistrationInfo registrationInfo,
            CallbackInfo ci
    ) {
        codeclens$codec.set(codec);
        codeclens$ops.set(ops);
        codeclens$resource.set(resource);
        codeclens$key.set(resourceKey);
    }

    /**
     * loadContentsFromManager 的 catch 块中，拦截 loadingErrors.put() 调用。
     */
    @Inject(
            method = "loadContentsFromManager",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
            )
    )
    private static <E> void codeclens$onManagerError(
            ResourceManager resourceManager,
            RegistryOps.RegistryInfoLookup registryInfoLookup,
            WritableRegistry<E> registry,
            Decoder<E> codec,
            Map<ResourceKey<?>, Exception> loadingErrors,
            CallbackInfo ci
    ) {
        codeclens$emitDiagnostic();
    }

    /**
     * 从 ThreadLocal 取出捕获的参数，重新读取 JSON 并输出结构化诊断。
     */
    @Unique
    @SuppressWarnings("unchecked")
    private static void codeclens$emitDiagnostic() {
        Decoder<?> capturedCodec = codeclens$codec.get();
        RegistryOps<?> capturedOps = codeclens$ops.get();
        Resource capturedResource = codeclens$resource.get();
        ResourceKey<?> capturedKey = codeclens$key.get();

        // 清理
        codeclens$codec.remove();
        codeclens$ops.remove();
        codeclens$resource.remove();
        codeclens$key.remove();

        if (capturedCodec == null || capturedResource == null || capturedOps == null) return;
        if (!(capturedCodec instanceof Codec<?> realCodec)) return;

        try {
            // 重新读取 JSON
            JsonElement json;
            try (Reader reader = capturedResource.openAsReader()) {
                json = JsonParser.parseReader(reader);
            }

            // Schema
            CodecSchema schema = CodecSchemaExtractor.extract(realCodec);
            String schemaText = CodecSchemaFormatter.format(schema);

            // 诊断
            FieldDiagnostic diag = CodecDiagnostics.diagnose(
                    (Codec<Object>) realCodec,
                    (RegistryOps<JsonElement>) capturedOps,
                    json
            );
            String report = DiagnosticFormatter.format(diag);

            CODECLENS_LOGGER.error(
                    "\n" +
                    "╔══ CodecLens Diagnostic ══════════════════════════════\n" +
                    "║ Registry entry: {}\n" +
                    "║\n" +
                    "║ Expected schema:\n{}\n" +
                    "║\n" +
                    "║ Diagnostic:\n{}\n" +
                    "╚═════════════════════════════════════════════════════",
                    capturedKey,
                    codeclens$indent(schemaText),
                    codeclens$indent(report)
            );
        } catch (Exception e) {
            CODECLENS_LOGGER.debug("CodecLens diagnostic failed for {}", capturedKey, e);
        }
    }

    @Unique
    private static String codeclens$indent(String text) {
        return text.lines()
                .map(line -> "║   " + line)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
