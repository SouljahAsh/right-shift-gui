package com.example.almatyclient.mixin;

import com.example.almatyclient.AltManagerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createNormalMenuOptions", at = @At("RETURN"))
    private void almatyclient$addAltManagerButton(int y, int spacingY, CallbackInfoReturnable<Integer> cir) {
        int buttonWidth = 98;
        int buttonX = this.width / 2 + 104;

        if (buttonX + buttonWidth > this.width - 4) {
            buttonWidth = Math.max(74, this.width - buttonX - 4);
        }

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.alt_manager"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AltManagerScreen(this));
                    }
                }
        ).bounds(buttonX, y, buttonWidth, 20).build());
    }
}
