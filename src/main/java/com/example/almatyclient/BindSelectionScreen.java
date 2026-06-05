package com.example.almatyclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class BindSelectionScreen extends Screen {
    private final Screen parent;
    private final ClientModule module;

    public BindSelectionScreen(Screen parent, ClientModule module) {
        super(Component.literal("Bind " + module.title()));
        this.parent = parent;
        this.module = module;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xE9000000);
        graphics.drawCenteredString(this.font, "ВЫБИРИТЕ КЛАВИШУ", this.width / 2, this.height / 2 - 8, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, this.module.title() + " bind: " + BindManager.keyName(this.module), this.width / 2, this.height / 2 + 18, 0xFF9BA3B7);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            BindManager.unbind(this.module);
            closeToParent();
            return true;
        }

        BindManager.bind(this.module, event.key());
        closeToParent();
        return true;
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void closeToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
