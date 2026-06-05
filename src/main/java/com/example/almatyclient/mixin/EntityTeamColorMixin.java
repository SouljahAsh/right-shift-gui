package com.example.almatyclient.mixin;

import com.example.almatyclient.AlmatyClient;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityTeamColorMixin {
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void almatyclient$entityOverlayColor(CallbackInfoReturnable<Integer> cir) {
        Entity entity = (Entity) (Object) this;
        if (AlmatyClient.shouldUseEntityOverlayColor(entity)) {
            cir.setReturnValue(AlmatyClient.entityOverlayColorRgb());
        }
    }
}
