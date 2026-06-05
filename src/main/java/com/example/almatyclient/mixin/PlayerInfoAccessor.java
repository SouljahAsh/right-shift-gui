package com.example.almatyclient.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInfo.class)
public interface PlayerInfoAccessor {
    @Mutable
    @Accessor("profile")
    void almatyclient$setProfile(GameProfile profile);
}
