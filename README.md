# Codec Lens

**Codec Lens** makes Mojang's `Codec` (DataFixerUpper) errors human-readable. Install the mod — that's it. When a data pack fails to parse, you get a structured diagnostic in the log instead of a cryptic one-liner.

**Codec Lens** 让 Mojang `Codec`（DataFixerUpper）的报错变得人类可读。装上 mod 就行，不需要任何配置。数据包解析失败时，日志中会自动输出结构化的诊断信息，而不是一行难以理解的错误。

---

## For Modpack Authors / 整合包作者

Just drop the jar into your `mods/` folder. No config needed.

把 jar 丢进 `mods/` 文件夹即可，无需配置。

When a data pack entry fails to parse, the original Minecraft error still appears as usual. Codec Lens adds an extra block right after it:

当数据包条目解析失败时，Minecraft 原有的报错照常输出。Codec Lens 会在其后追加一段额外的诊断块：

```
╔══ CodecLens Diagnostic ══════════════════════════════
║ Registry entry: minecraft:worldgen/biome / minecraft:plains
║
║ Expected schema:
║   {
║     temperature: float,
║     downfall: float,
║     effects: {
║       fog_color: int,
║       sky_color: int,
║       water_color: int,
║       water_fog_color: int?,
║       ...
║     }
║   }
║
║ Diagnostic:
║   {
║     ✗ temperature: float  ← Expected float, got "warm" (got warm)
║       downfall: float  ✓
║       effects: { ... }  ✓
║   }
╚═════════════════════════════════════════════════════
```

- `✓` = field parsed OK / 字段解析成功
- `✗` = field failed / 字段解析失败，后面跟着原因
- `?` after a type = optional field / 类型后的 `?` 表示可选字段
- `|` between types = either/alternative (tries left first, then right) / 类型间的 `|` 表示二选一

---

## For Mod Developers / Mod 开发者

### Passive mode (zero code) / 无感模式（零代码）

Codec Lens works out of the box for all `RegistryDataLoader` codecs (worldgen, recipes, damage types, etc.). Your users install it and get better errors for your data formats automatically.

Codec Lens 对所有通过 `RegistryDataLoader` 加载的 Codec（世界生成、配方、伤害类型等）开箱即用。你的用户装上它，就能自动获得更好的数据格式报错。

### API usage / API 使用

You can also use Codec Lens programmatically:

你也可以在代码中主动使用 Codec Lens：

```java
import io.github.tt432.codeclens.CodecLensAPI;

// Inspect a codec's structure / 查看 Codec 的结构
String schema = CodecLensAPI.describeSchema(MyRecord.CODEC);
// → "{\n  name: string,\n  value: int\n}"

// Decode with automatic diagnostics on failure / 解码并在失败时自动诊断
DataResult<MyRecord> result = CodecLensAPI.decodeWithDiagnostics(
    MyRecord.CODEC, JsonOps.INSTANCE, jsonElement);

// Get a raw diagnostic tree for programmatic use / 获取原始诊断树供程序化处理
FieldDiagnostic diag = CodecLensAPI.diagnoseRaw(
    MyRecord.CODEC, JsonOps.INSTANCE, jsonElement);
```

### Custom codec support / 自定义 Codec 支持

If you have a custom `Codec` implementation that Codec Lens can't automatically reflect, register a `SchemaExtractor`:

如果你有自定义的 `Codec` 实现，Codec Lens 无法自动反射识别，可以注册一个 `SchemaExtractor`：

```java
import io.github.tt432.codeclens.extract.SchemaExtractorRegistry;
import io.github.tt432.codeclens.extract.SchemaExtractor;
import io.github.tt432.codeclens.schema.CodecSchema;

// Register during mod init / 在 mod 初始化时注册
SchemaExtractorRegistry.register((codec, ctx) -> {
    if (codec instanceof MyWeightedListCodec<?> weighted) {
        // Describe the structure using built-in schema nodes
        // 用内置的 schema 节点描述结构
        return new CodecSchema.ListSchema(
            new CodecSchema.RecordSchema(List.of(
                new FieldEntry("data", ctx.extract(weighted.elementCodec()), false),
                new FieldEntry("weight", new CodecSchema.PrimitiveSchema("int"), false)
            ))
        );
    }
    return null; // Not ours, pass to next / 不认识，交给下一个
});
```

There is also `MapCodecSchemaExtractor` for custom `MapCodec` types — register it the same way via `SchemaExtractorRegistry.register(...)`.

对于自定义 `MapCodec` 类型，还有 `MapCodecSchemaExtractor` 接口，注册方式相同。

### Schema node types / Schema 节点类型

| Node / 节点 | Meaning / 含义 | Formatted as / 格式化为 |
|---|---|---|
| `PrimitiveSchema("int")` | Primitive type / 基本类型 | `int` |
| `RecordSchema(fields)` | Object with named fields / 带命名字段的对象 | `{ name: type, ... }` |
| `EitherSchema(l, r)` | Try left, fallback to right / 先尝试左，失败尝试右 | `int \| string` |
| `XorSchema(l, r)` | Exactly one must match / 恰好一个匹配 | `int ^ string` |
| `ListSchema(elem)` | List / 列表 | `[int]` |
| `MapSchema(k, v)` | Map / 映射 | `map<string, int>` |
| `DispatchSchema(key, ks)` | Type dispatch / 类型分发 | `dispatch("type": string)` |
| `PairSchema(a, b)` | Pair / 对 | `pair<string, int>` |
| `UnknownSchema(desc)` | Unrecognized codec / 无法识别的 Codec | raw toString |

---

## Compatibility / 兼容性

- Minecraft 1.21.1
- NeoForge 21.1.x
- DFU 8.0.16
- Java 21+
