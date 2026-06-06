package com.example.almatyclient.mixin;

import com.example.almatyclient.BindCommand;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void almatyclient$handleBindCommand(String message, boolean addToHistory, CallbackInfo ci) {
        if (BindCommand.handle(message)) {
            ci.cancel();
        }
    }
}
