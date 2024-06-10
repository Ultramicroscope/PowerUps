package wtf.ultra.mixin;

import net.minecraft.client.renderer.ActiveRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.FloatBuffer;

@Mixin({ActiveRenderInfo.class})
public interface IActiveRenderInfoMixin {
    @Accessor static FloatBuffer getMODELVIEW() { return null; }
    @Accessor static FloatBuffer getPROJECTION() { return null; }
}
