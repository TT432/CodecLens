package io.github.tt432.codeclens;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.codeclens.error.CodecDiagnostics;
import io.github.tt432.codeclens.error.DiagnosticFormatter;
import io.github.tt432.codeclens.error.FieldDiagnostic;
import io.github.tt432.codeclens.schema.CodecSchema;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 手动运行的测试入口。在 NeoForge 环境中调用 {@link #runAll()} 即可。
 * <p>
 * 因为依赖 Minecraft 的 ResourceLocation 等类，需要在游戏启动后调用。
 * 可以在 {@code FMLCommonSetupEvent} 中调用。
 */
public class CodecLensTest {

    // ══════════════════════════════════════════════════════════
    //  测试用数据类 & Codec
    // ══════════════════════════════════════════════════════════

    // ── 1. 简单 record（2 字段）──
    record ModRarity(int endoBase, int creditBase) {
        static final Codec<ModRarity> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("endoBase").forGetter(ModRarity::endoBase),
                Codec.INT.fieldOf("creditBase").forGetter(ModRarity::creditBase)
        ).apply(i, ModRarity::new));
    }

    // ── 2. 带 optional 字段 ──
    record ModPolarity(String texture, String nameKey, Optional<Boolean> matchesUniversal, Optional<Boolean> isUniversal) {
        static final Codec<ModPolarity> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("texture").forGetter(ModPolarity::texture),
                Codec.STRING.fieldOf("nameKey").forGetter(ModPolarity::nameKey),
                Codec.BOOL.optionalFieldOf("matchesUniversal").forGetter(ModPolarity::matchesUniversal),
                Codec.BOOL.optionalFieldOf("isUniversal").forGetter(ModPolarity::isUniversal)
        ).apply(i, ModPolarity::new));
    }

    // ── 3. 带 either / withAlternative ──
    record FlexValue(int value) {
        static final Codec<FlexValue> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.withAlternative(
                        Codec.INT,
                        Codec.STRING.xmap(Integer::valueOf, Objects::toString)
                ).fieldOf("value").forGetter(FlexValue::value)
        ).apply(i, FlexValue::new));
    }

    // ── 4. 嵌套 record ──
    record ClientDisplay(Optional<String> texture, Optional<String> borderTexture, Optional<Integer> color) {
        static final Codec<ClientDisplay> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("texture").forGetter(ClientDisplay::texture),
                Codec.STRING.optionalFieldOf("borderTexture").forGetter(ClientDisplay::borderTexture),
                Codec.INT.optionalFieldOf("color").forGetter(ClientDisplay::color)
        ).apply(i, ClientDisplay::new));
    }

    record ModEntry(String id, int priority, Optional<ClientDisplay> client) {
        static final Codec<ModEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(ModEntry::id),
                Codec.INT.fieldOf("priority").forGetter(ModEntry::priority),
                ClientDisplay.CODEC.optionalFieldOf("client").forGetter(ModEntry::client)
        ).apply(i, ModEntry::new));
    }

    // ── 5. list + map 组合 ──
    record Inventory(List<String> items, Map<String, Integer> counts) {
        static final Codec<Inventory> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("items").forGetter(Inventory::items),
                Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("counts").forGetter(Inventory::counts)
        ).apply(i, Inventory::new));
    }

    // ── 6. either 顶层（非字段内） ──
    static final Codec<Object> STRING_OR_INT = Codec.either(Codec.STRING, Codec.INT)
            .xmap(e -> e.map(s -> s, i -> i), v -> {
                throw new UnsupportedOperationException();
            });

    // ── 7. 多层嵌套 + 6 字段（触发 lift1 二叉树） ──
    record BigRecord(String a, int b, float c, double d, long e, boolean f) {
        static final Codec<BigRecord> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("a").forGetter(BigRecord::a),
                Codec.INT.fieldOf("b").forGetter(BigRecord::b),
                Codec.FLOAT.fieldOf("c").forGetter(BigRecord::c),
                Codec.DOUBLE.fieldOf("d").forGetter(BigRecord::d),
                Codec.LONG.fieldOf("e").forGetter(BigRecord::e),
                Codec.BOOL.fieldOf("f").forGetter(BigRecord::f)
        ).apply(i, BigRecord::new));
    }

    // ══════════════════════════════════════════════════════════
    //  测试执行
    // ══════════════════════════════════════════════════════════

    public static void runAll() {
        separator("1. Schema: 简单 record (ModRarity)");
        printSchema(ModRarity.CODEC);

        separator("2. Schema: 带 optional (ModPolarity)");
        printSchema(ModPolarity.CODEC);

        separator("3. Schema: 带 withAlternative (FlexValue)");
        printSchema(FlexValue.CODEC);

        separator("4. Schema: 嵌套 record (ModEntry → ClientDisplay)");
        printSchema(ModEntry.CODEC);

        separator("5. Schema: list + map (Inventory)");
        printSchema(Inventory.CODEC);

        separator("6. Schema: 顶层 either (String | Int)");
        printSchema(STRING_OR_INT);

        separator("7. Schema: 6 字段 lift1 二叉树 (BigRecord)");
        printSchema(BigRecord.CODEC);

        // ── 诊断测试 ──

        separator("8. Diagnose: ModRarity — 正确输入");
        diagnoseJson(ModRarity.CODEC, """
                {"endoBase": 10, "creditBase": 20}
                """);

        separator("9. Diagnose: ModRarity — endoBase 类型错误");
        diagnoseJson(ModRarity.CODEC, """
                {"endoBase": "abc", "creditBase": 20}
                """);

        separator("10. Diagnose: ModRarity — 缺少字段");
        diagnoseJson(ModRarity.CODEC, """
                {"creditBase": 20}
                """);

        separator("11. Diagnose: ModPolarity — optional 字段缺失（应该 OK）");
        diagnoseJson(ModPolarity.CODEC, """
                {"texture": "foo:bar", "nameKey": "test"}
                """);

        separator("12. Diagnose: ModPolarity — required 字段类型错误");
        diagnoseJson(ModPolarity.CODEC, """
                {"texture": 123, "nameKey": "test"}
                """);

        separator("13. Diagnose: FlexValue — int 输入（应该 OK）");
        diagnoseJson(FlexValue.CODEC, """
                {"value": 42}
                """);

        separator("14. Diagnose: FlexValue — string 输入（应该 OK，走 alternative）");
        diagnoseJson(FlexValue.CODEC, """
                {"value": "42"}
                """);

        separator("15. Diagnose: FlexValue — boolean 输入（两个 alternative 都失败）");
        diagnoseJson(FlexValue.CODEC, """
                {"value": true}
                """);

        separator("16. Diagnose: ModEntry — 嵌套 client 中 color 类型错误");
        diagnoseJson(ModEntry.CODEC, """
                {"id": "test", "priority": 1, "client": {"texture": "a", "color": "not_int"}}
                """);

        separator("17. Diagnose: BigRecord — 多字段错误");
        diagnoseJson(BigRecord.CODEC, """
                {"a": 123, "b": "wrong", "c": 1.0, "d": true, "e": 100, "f": false}
                """);

        separator("18. Diagnose: Inventory — list 字段正确");
        diagnoseJson(Inventory.CODEC, """
                {"items": ["sword", "shield"], "counts": {"sword": 1, "shield": 2}}
                """);

        separator("19. Diagnose: Inventory — counts 值类型错误");
        diagnoseJson(Inventory.CODEC, """
                {"items": ["sword"], "counts": {"sword": "one"}}
                """);
    }

    // ══════════════════════════════════════════════════════════
    //  辅助方法
    // ══════════════════════════════════════════════════════════

    private static void printSchema(Codec<?> codec) {
        CodecSchema schema = CodecLensAPI.schemaOf(codec);
        System.out.println("[Schema Tree]");
        System.out.println(schema);
        System.out.println();
        System.out.println("[Formatted]");
        System.out.println(CodecLensAPI.formatSchema(schema));
    }

    private static void diagnoseJson(Codec<?> codec, String json) {
        JsonElement element = JsonParser.parseString(json.strip());

        // 先尝试正常 decode
        DataResult<?> result = codec.parse(JsonOps.INSTANCE, element);
        if (result.isSuccess()) {
            System.out.println("[Decode OK] " + result.getOrThrow());
        } else {
            System.out.println("[Decode FAILED] " + result.error().map(e -> ((DataResult.Error<?>) e).message()).orElse("?"));
        }
        System.out.println();

        // 结构化诊断
        System.out.println("[Diagnostic Report]");
        FieldDiagnostic diag = CodecDiagnostics.diagnose(codec, JsonOps.INSTANCE, element);
        System.out.println(DiagnosticFormatter.format(diag));
    }

    private static void separator(String title) {
        System.out.println();
        System.out.println("═".repeat(60));
        System.out.println("  " + title);
        System.out.println("═".repeat(60));
    }
}
