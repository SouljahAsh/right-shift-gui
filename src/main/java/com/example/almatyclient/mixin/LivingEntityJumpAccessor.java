package com.example.almatyclient.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityJumpAccessor {
    @Accessor("noJumpDelay")
    void almatyclient$setNoJumpDelay(int delay);
}
