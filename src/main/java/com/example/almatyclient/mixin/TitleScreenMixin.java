package com.example.almatyclient.mixin;

import com.example.almatyclient.AltManagerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"
            ),
            require = 0
    )
    private void almatyclient$replaceLogo(LogoRenderer renderer, GuiGraphics graphics, int screenWidth, float alpha) {
        drawAlmatyLogo(graphics, screenWidth);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V"
            ),
            require = 0
    )
    private void almatyclient$replaceLogoWithY(LogoRenderer renderer, GuiGraphics graphics, int screenWidth, float alpha, int y) {
        drawAlmatyLogo(graphics, screenWidth);
    }

    private void drawAlmatyLogo(GuiGraphics graphics, int screenWidth) {
        int logoY = 30;
        int centerX = screenWidth / 2;
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
