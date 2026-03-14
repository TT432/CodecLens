package io.github.tt432;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = CodecLens.MOD_ID, dist = Dist.CLIENT)
public class CodecLensClient {
    public CodecLensClient(ModContainer container) {
    }
}
