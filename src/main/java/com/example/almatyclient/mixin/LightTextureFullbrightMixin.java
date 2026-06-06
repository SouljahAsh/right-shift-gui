package com.example.almatyclient.mixin;

import com.example.almatyclient.AlmatyClient;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public abstract class LightTextureFullbrightMixin {
    @Inject(method = "getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F", at = @At("HEAD"), cancellable = true)
    private static void almatyclient$fullbrightDimension(DimensionType type, int lightLevel, CallbackInfoReturnable<Float> cir) {
        if (AlmatyClient.isFullbrightEnabled()) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "getBrightness(FI)F", at = @At("HEAD"), cancellable = true)
    private static void almatyclient$fullbright(float ambientLight, int lightLevel, CallbackInfoReturnable<Float> cir) {
        if (AlmatyClient.isFullbrightEnabled()) {
            cir.setReturnValue(1.0F);
        }
    }
}
