package com.example.almatyclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class AlmatyClientScreen extends Screen {
    private final Screen parent;
    private final long openedAt;
    private Button autoSprintButton;
    private Button closeButton;

    public AlmatyClientScreen(Screen parent) {
        super(Component.translatable("screen.almatyclient.title"));
        this.parent = parent;
        this.openedAt = Util.getMillis();
    }

    @Override
    protected void init() {
        int buttonWidth = 180;
        int buttonHeight = 20;
        int centerX = this.width / 2 - buttonWidth / 2;
        int centerY = this.height / 2;

        this.autoSprintButton = this.addRenderableWidget(Button.builder(
                autoSprintText(),
                button -> {
                    AlmatyClient.toggleAutoSprint();
                    button.setMessage(autoSprintText());
                }
        ).bounds(
                centerX,
                centerY + 10,
                buttonWidth,
                buttonHeight
        ).build());

        this.closeButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.close"),
                button -> this.onClose()
        ).bounds(
                centerX,
                centerY + 38,
                buttonWidth,
                buttonHeight
        ).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);

        float progress = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 180.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        int panelWidth = 300;
        int panelHeight = 154;
        int panelX = this.width / 2 - panelWidth / 2;
        int panelY = this.height / 2 - panelHeight / 2 + Math.round((1.0F - eased) * 12.0F);
        int alpha = Math.round(210.0F * eased);
        int panelColor = (alpha << 24) | 0x101820;

        updateButtonAnimation(eased, panelY);

        graphics.fill(panelX + 5, panelY + 6, panelX + panelWidth + 5, panelY + panelHeight + 6, 0x66000000);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, panelColor);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFF4BA3FF);
        graphics.fill(panelX, panelY + 2, panelX + panelWidth, panelY + 4, 0xFF2DD4BF);
        graphics.fill(panelX, panelY + panelHeight - 2, panelX + panelWidth, panelY + panelHeight, 0xFF4BA3FF);
        graphics.fill(panelX, panelY, panelX + 2, panelY + panelHeight, 0xFF4BA3FF);
        graphics.fill(panelX + panelWidth - 2, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF2DD4BF);

        int statusColor = AlmatyClient.isAutoSprintEnabled() ? 0xFF52FFA8 : 0xFFFF7272;
        String statusText = AlmatyClient.isAutoSprintEnabled() ? "ON" : "OFF";

        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 18, 0xFFFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.almatyclient.description"),
                this.width / 2,
                panelY + 42,
                0xFFBFD7FF
        );
        graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.almatyclient.autosprint.status", statusText),
                this.width / 2,
                panelY + 64,
                statusColor
        );

        super.render(graphics, mouseX, mouseY, delta);

        if (this.autoSprintButton != null && this.autoSprintButton.isHoveredOrFocused()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("screen.almatyclient.autosprint.hint"),
                    this.width / 2,
                    panelY + panelHeight - 18,
                    0xFFD6E4FF
            );
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static Component autoSprintText() {
        return Component.translatable(
                AlmatyClient.isAutoSprintEnabled()
                        ? "screen.almatyclient.autosprint.enabled"
                        : "screen.almatyclient.autosprint.disabled"
        );
    }

    private void updateButtonAnimation(float eased, int panelY) {
        int buttonWidth = 180;
        int centerX = this.width / 2 - buttonWidth / 2;

        if (this.autoSprintButton != null) {
            this.autoSprintButton.setX(centerX);
            this.autoSprintButton.setY(panelY + 86);
            this.autoSprintButton.setAlpha(eased);
        }

        if (this.closeButton != null) {
            this.closeButton.setX(centerX);
            this.closeButton.setY(panelY + 114);
            this.closeButton.setAlpha(eased);
        }
    }
}
