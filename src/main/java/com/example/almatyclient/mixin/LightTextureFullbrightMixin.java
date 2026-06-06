package com.example.almatyclient.mixin;

import com.example.almatyclient.AlmatyClient;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public abstract class LightTextureFullbrightMixin {
    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 0), index = 0)
    private float almatyclient$fullAmbient(float value) {
        return AlmatyClient.isFullbrightEnabled() ? 1.0F : value;
    }

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 1), index = 0)
    private float almatyclient$fullSkyLight(float value) {
        return AlmatyClient.isFullbrightEnabled() ? 1.0F : value;
    }

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 2), index = 0)
    private float almatyclient$fullBlockLight(float value) {
        return AlmatyClient.isFullbrightEnabled() ? 1.5F : value;
    }

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 3), index = 0)
    private float almatyclient$nightVision(float value) {
        return AlmatyClient.isFullbrightEnabled() ? 1.0F : value;
    }

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 4), index = 0)
    private float almatyclient$removeDarkness(float value) {
        return AlmatyClient.isFullbrightEnabled() ? 0.0F : value;
    }

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 5), index = 0)
    private float almatyclient$removeWorldDarken(float value) {
        return AlmatyClient.isFullbrightEnabled() ? 0.0F : value;
    }

    @ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;", ordinal = 6), index = 0)
    private float almatyclient$fullGamma(float value) {
        return AlmatyClient.isFullbrightEnabled() ? 1.0F : value;
    }

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
