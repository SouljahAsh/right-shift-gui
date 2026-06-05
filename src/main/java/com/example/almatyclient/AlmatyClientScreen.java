package com.example.almatyclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class AlmatyClientScreen extends Screen {
    private static final int MIN_PANEL_WIDTH = 260;
    private static final int MIN_PANEL_HEIGHT = 190;
    private static final int RESIZE_HANDLE = 7;
    private static final int RESIZE_LEFT = 1;
    private static final int RESIZE_RIGHT = 2;
    private static final int RESIZE_TOP = 4;
    private static final int RESIZE_BOTTOM = 8;

    private final Screen parent;
    private final long openedAt;
    private boolean layoutInitialized;
    private int panelX;
    private int panelY;
    private int panelWidth = 320;
    private int panelHeight = 198;
    private int resizeMode;
    private double lastMouseX;
    private double lastMouseY;
    private Button autoSprintButton;
    private Button closeButton;

    public AlmatyClientScreen(Screen parent) {
        super(Component.translatable("screen.almatyclient.title"));
        this.parent = parent;
        this.openedAt = Util.getMillis();
    }

    @Override
    protected void init() {
        if (!this.layoutInitialized) {
            this.panelX = this.width / 2 - this.panelWidth / 2;
            this.panelY = this.height / 2 - this.panelHeight / 2;
            this.layoutInitialized = true;
        } else {
            clampPanelToScreen();
        }

        this.autoSprintButton = this.addRenderableWidget(Button.builder(
                autoSprintText(),
                button -> {
                    AlmatyClient.toggleAutoSprint();
                    button.setMessage(autoSprintText());
                }
        ).bounds(0, 0, 180, 20).build());

        this.closeButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.close"),
                button -> this.onClose()
        ).bounds(0, 0, 180, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);

        float progress = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 180.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        int panelY = this.panelY + Math.round((1.0F - eased) * 12.0F);
        int alpha = Math.round(210.0F * eased);
        int panelColor = (alpha << 24) | 0x101820;

        updateButtonAnimation(eased, panelY);

        graphics.fill(this.panelX + 5, panelY + 6, this.panelX + this.panelWidth + 5, panelY + this.panelHeight + 6, 0x66000000);
        graphics.fill(this.panelX, panelY, this.panelX + this.panelWidth, panelY + this.panelHeight, panelColor);
        graphics.fill(this.panelX, panelY, this.panelX + this.panelWidth, panelY + 2, 0xFF4BA3FF);
        graphics.fill(this.panelX, panelY + 2, this.panelX + this.panelWidth, panelY + 4, 0xFF2DD4BF);
        graphics.fill(this.panelX, panelY + this.panelHeight - 2, this.panelX + this.panelWidth, panelY + this.panelHeight, 0xFF4BA3FF);
        graphics.fill(this.panelX, panelY, this.panelX + 2, panelY + this.panelHeight, 0xFF4BA3FF);
        graphics.fill(this.panelX + this.panelWidth - 2, panelY, this.panelX + this.panelWidth, panelY + this.panelHeight, 0xFF2DD4BF);
        drawResizeHandles(graphics, panelY);

        int statusColor = AlmatyClient.isAutoSprintEnabled() ? 0xFF52FFA8 : 0xFFFF7272;
        String statusText = AlmatyClient.isAutoSprintEnabled() ? "ON" : "OFF";

        graphics.drawCenteredString(this.font, this.title, this.panelX + this.panelWidth / 2, panelY + 18, 0xFFFFFFFF);
        graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.almatyclient.description"),
                this.panelX + this.panelWidth / 2,
                panelY + 42,
                0xFFBFD7FF
        );
        graphics.drawCenteredString(
                this.font,
                Component.translatable("screen.almatyclient.autosprint.status", statusText),
                this.panelX + this.panelWidth / 2,
                panelY + 64,
                statusColor
        );

        super.render(graphics, mouseX, mouseY, delta);

        if (this.autoSprintButton != null && this.autoSprintButton.isHoveredOrFocused()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("screen.almatyclient.autosprint.hint"),
                    this.panelX + this.panelWidth / 2,
                    panelY + this.panelHeight - 18,
                    0xFFD6E4FF
            );
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        this.resizeMode = findResizeMode(event.x(), event.y());

        if (this.resizeMode != 0) {
            this.lastMouseX = event.x();
            this.lastMouseY = event.y();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.resizeMode == 0) {
            return super.mouseDragged(event, dragX, dragY);
        }

        int dx = (int) Math.round(event.x() - this.lastMouseX);
        int dy = (int) Math.round(event.y() - this.lastMouseY);

        resizePanel(dx, dy);

        this.lastMouseX = event.x();
        this.lastMouseY = event.y();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.resizeMode != 0) {
            this.resizeMode = 0;
            return true;
        }

        return super.mouseReleased(event);
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
        int buttonWidth = Math.max(140, Math.min(220, this.panelWidth - 48));
        int centerX = this.panelX + this.panelWidth / 2 - buttonWidth / 2;

        if (this.autoSprintButton != null) {
            this.autoSprintButton.setWidth(buttonWidth);
            this.autoSprintButton.setX(centerX);
            this.autoSprintButton.setY(panelY + 86);
            this.autoSprintButton.setAlpha(eased);
        }

        if (this.closeButton != null) {
            this.closeButton.setWidth(buttonWidth);
            this.closeButton.setX(centerX);
            this.closeButton.setY(panelY + 114);
            this.closeButton.setAlpha(eased);
        }
    }

    private void drawResizeHandles(GuiGraphics graphics, int panelY) {
        int right = this.panelX + this.panelWidth;
        int bottom = panelY + this.panelHeight;

        graphics.fill(right - 16, bottom - 4, right - 4, bottom - 2, 0xFFBFD7FF);
        graphics.fill(right - 12, bottom - 8, right - 4, bottom - 6, 0xFFBFD7FF);
        graphics.fill(right - 8, bottom - 12, right - 4, bottom - 10, 0xFFBFD7FF);
    }

    private int findResizeMode(double mouseX, double mouseY) {
        boolean left = mouseX >= this.panelX - RESIZE_HANDLE && mouseX <= this.panelX + RESIZE_HANDLE;
        boolean right = mouseX >= this.panelX + this.panelWidth - RESIZE_HANDLE
                && mouseX <= this.panelX + this.panelWidth + RESIZE_HANDLE;
        boolean top = mouseY >= this.panelY - RESIZE_HANDLE && mouseY <= this.panelY + RESIZE_HANDLE;
        boolean bottom = mouseY >= this.panelY + this.panelHeight - RESIZE_HANDLE
                && mouseY <= this.panelY + this.panelHeight + RESIZE_HANDLE;

        int mode = 0;
        if (left) {
            mode |= RESIZE_LEFT;
        }
        if (right) {
            mode |= RESIZE_RIGHT;
        }
        if (top) {
            mode |= RESIZE_TOP;
        }
        if (bottom) {
            mode |= RESIZE_BOTTOM;
        }

        return mode;
    }

    private void resizePanel(int dx, int dy) {
        if ((this.resizeMode & RESIZE_LEFT) != 0) {
            int newWidth = this.panelWidth - dx;
            if (newWidth >= MIN_PANEL_WIDTH) {
                this.panelX += dx;
                this.panelWidth = newWidth;
            }
        }

        if ((this.resizeMode & RESIZE_RIGHT) != 0) {
            this.panelWidth = Math.max(MIN_PANEL_WIDTH, this.panelWidth + dx);
        }

        if ((this.resizeMode & RESIZE_TOP) != 0) {
            int newHeight = this.panelHeight - dy;
            if (newHeight >= MIN_PANEL_HEIGHT) {
                this.panelY += dy;
                this.panelHeight = newHeight;
            }
        }

        if ((this.resizeMode & RESIZE_BOTTOM) != 0) {
            this.panelHeight = Math.max(MIN_PANEL_HEIGHT, this.panelHeight + dy);
        }

        clampPanelToScreen();
    }

    private void clampPanelToScreen() {
        this.panelWidth = Math.min(Math.max(this.panelWidth, MIN_PANEL_WIDTH), Math.max(MIN_PANEL_WIDTH, this.width - 12));
        this.panelHeight = Math.min(Math.max(this.panelHeight, MIN_PANEL_HEIGHT), Math.max(MIN_PANEL_HEIGHT, this.height - 12));
        this.panelX = Math.max(6, Math.min(this.panelX, this.width - this.panelWidth - 6));
        this.panelY = Math.max(6, Math.min(this.panelY, this.height - this.panelHeight - 6));
    }
}
