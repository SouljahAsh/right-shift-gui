package com.example.almatyclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftRightClickAccessor {
    @Accessor("rightClickDelay")
    int almatyclient$getRightClickDelay();

    @Accessor("rightClickDelay")
    void almatyclient$setRightClickDelay(int delay);
}
