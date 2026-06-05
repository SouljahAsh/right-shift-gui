package com.example.almatyclient.mixin;

import com.example.almatyclient.AlmatyClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void almatyclient$applyEspNameTag(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        Component label = AlmatyClient.espNameTag(entity);
        if (label == null) {
            return;
        }

        state.nameTag = label;
        state.nameTagAttachment = entity.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(tickDelta));
        if (state.nameTagAttachment == null) {
            state.nameTagAttachment = new Vec3(0.0D, entity.getBbHeight() + 0.5D, 0.0D);
        }
    }
}
