package com.example.almatyclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ChangelogScreen extends Screen {
    private static final String[] LINES = {
            "1.0.0",
            "- ESP labels now show Name | HP in real time",
            "- AutoSprint no longer changes movement velocity",
            "- Entity Overlay was removed",
            "- GUI resize and text clipping were improved",
            "- Added Reset GUI action"
    };

    private final Screen parent;

    public ChangelogScreen(Screen parent) {
        super(Component.translatable("screen.almatyclient.changelogs"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.alt_manager.back"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this.parent);
                    }
                }
        ).bounds(this.width / 2 - 50, this.height - 34, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xA5000000);

        int panelWidth = Math.min(360, this.width - 24);
        int panelHeight = Math.min(190, this.height - 64);
        int x = this.width / 2 - panelWidth / 2;
        int y = Math.max(18, this.height / 2 - panelHeight / 2);

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xDD0B1220);
        graphics.fill(x, y, x + panelWidth, y + 2, AlmatyClient.accentColor(255));
        graphics.drawCenteredString(this.font, Component.translatable("screen.almatyclient.changelogs"), this.width / 2, y + 14, 0xFFFFFFFF);

        int lineY = y + 38;
        for (String line : LINES) {
            graphics.drawString(this.font, fit(line, panelWidth - 28), x + 14, lineY, line.startsWith("-") ? 0xFFBFD7FF : 0xFFFFFFFF, false);
            lineY += 14;
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String fit(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(1, maxWidth - this.font.width("..."))) + "...";
    }
}
