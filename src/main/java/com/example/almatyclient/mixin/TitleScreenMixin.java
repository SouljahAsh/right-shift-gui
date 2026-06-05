package com.example.almatyclient.mixin;

import com.example.almatyclient.AltManagerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    @Inject(method = "render", at = @At("TAIL"))
    private void almatyclient$renderLogo(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int logoY = 28;
        int centerX = this.width / 2;
        int logoWidth = 236;

        graphics.fill(centerX - logoWidth / 2 - 8, logoY - 8, centerX + logoWidth / 2 + 8, logoY + 42, 0xD0000000);
        graphics.fill(centerX - logoWidth / 2 - 8, logoY + 42, centerX + logoWidth / 2 + 8, logoY + 45, 0xFF143D7A);

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, logoY);
        graphics.pose().scale(2.15F, 2.15F);

        drawLogoText(graphics, "AlmatyClient", 2, 2, 0xFF061833);
        drawLogoText(graphics, "AlmatyClient", 1, 1, 0xFF0A2B5F);
        drawLogoText(graphics, "AlmatyClient", 0, 0, 0xFF5FB9FF);
        drawLogoText(graphics, "AlmatyClient", 0, -1, 0xFFAEDCFF);

        graphics.pose().popMatrix();
    }

    private void drawLogoText(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawCenteredString(this.font, text, x, y, color);
    }
}
