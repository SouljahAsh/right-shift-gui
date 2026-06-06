package com.example.almatyclient.mixin;

import com.example.almatyclient.AltManagerScreen;
import com.example.almatyclient.ChangelogScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private static final Identifier ALMATY_LOGO = Identifier.fromNamespaceAndPath(
            "almatyclient",
            "textures/gui/almaty_client_logo.png"
    );
    private static final int LOGO_TEXTURE_WIDTH = 1024;
    private static final int LOGO_TEXTURE_HEIGHT = 214;

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

        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.changelogs"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new ChangelogScreen(this));
                    }
                }
        ).bounds(buttonX, Math.max(4, y - 24), buttonWidth, 20).build());
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
        int logoWidth = Math.min(520, Math.max(300, screenWidth - 40));
        int logoHeight = logoWidth * LOGO_TEXTURE_HEIGHT / LOGO_TEXTURE_WIDTH;
        int logoX = (screenWidth - logoWidth) / 2;
        int logoY = 18;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                ALMATY_LOGO,
                logoX,
                logoY,
                0.0F,
                0.0F,
                logoWidth,
                logoHeight,
                LOGO_TEXTURE_WIDTH,
                LOGO_TEXTURE_HEIGHT,
                LOGO_TEXTURE_WIDTH,
                LOGO_TEXTURE_HEIGHT
        );
    }
}
