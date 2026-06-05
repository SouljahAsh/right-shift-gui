package com.example.almatyclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

public final class AlmatyClientScreen extends Screen {
    private static final int MIN_PANEL_WIDTH = 720;
    private static final int MIN_PANEL_HEIGHT = 420;
    private static final int RESIZE_HANDLE = 8;
    private static final int RESIZE_LEFT = 1;
    private static final int RESIZE_RIGHT = 2;
    private static final int RESIZE_TOP = 4;
    private static final int RESIZE_BOTTOM = 8;
    private static final int RESET_BUTTON_HEIGHT = 28;
    private static final float GUI_SCALE = 2.0F / 3.0F;

    private final Screen parent;
    private final long openedAt;

    private Tab activeTab = Tab.MOVEMENT;
    private boolean layoutInitialized;
    private boolean dragging;
    private boolean closing;
    private int activeSlider = -1;
    private int resizeMode;
    private int panelX;
    private int panelY;
    private int panelWidth = 860;
    private int panelHeight = 500;
    private int resizeStartPanelX;
    private int resizeStartPanelY;
    private int resizeStartPanelWidth;
    private int resizeStartPanelHeight;
    private int maxScroll;
    private long closingAt;
    private double contentScroll;
    private double resizeStartMouseX;
    private double resizeStartMouseY;
    private double dragOffsetX;
    private double dragOffsetY;

    public AlmatyClientScreen(Screen parent) {
        super(Component.literal("AlmatyClient"));
        this.parent = parent;
        this.openedAt = Util.getMillis();
    }

    @Override
    protected void init() {
        if (!this.layoutInitialized) {
            this.panelWidth = AlmatyConfig.getInt("gui.width", this.panelWidth);
            this.panelHeight = AlmatyConfig.getInt("gui.height", this.panelHeight);
            this.panelX = AlmatyConfig.getInt("gui.x", this.width / 2 - scaled(this.panelWidth) / 2);
            this.panelY = AlmatyConfig.getInt("gui.y", this.height / 2 - scaled(this.panelHeight) / 2);
            this.activeTab = readSavedTab();
            this.layoutInitialized = true;
        }
        clampPanelToScreen();
        clampScroll();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float progress = openProgress();
        int backgroundAlpha = Math.round(165.0F * progress);
        graphics.fill(0, 0, this.width, this.height, colorWithAlpha(backgroundAlpha, 0, 0, 0));

        int animatedY = this.panelY + Math.round((1.0F - progress) * 18.0F);
        int animatedHeight = this.panelHeight;

        graphics.pose().pushMatrix();
        graphics.pose().translate(this.panelX, animatedY);
        graphics.pose().scale(GUI_SCALE, GUI_SCALE);
        graphics.pose().translate(-this.panelX, -animatedY);
        try {
            drawShell(graphics, animatedY, animatedHeight);
            if (progress > 0.04F) {
                drawSidebar(graphics, animatedY);
                drawTopBar(graphics, animatedY);
                drawContent(graphics, animatedY);
                drawResizeHandles(graphics, animatedY);
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double physicalMouseX = event.x();
        double physicalMouseY = event.y();
        double mouseX = toLogicalX(physicalMouseX);
        double mouseY = toLogicalY(physicalMouseY);

        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && openBindSelection(mouseX, mouseY)) {
            return true;
        }

        this.resizeMode = findResizeMode(physicalMouseX, physicalMouseY);
        if (this.resizeMode != 0) {
            this.resizeStartMouseX = physicalMouseX;
            this.resizeStartMouseY = physicalMouseY;
            this.resizeStartPanelX = this.panelX;
            this.resizeStartPanelY = this.panelY;
            this.resizeStartPanelWidth = this.panelWidth;
            this.resizeStartPanelHeight = this.panelHeight;
            return true;
        }

        if (isInClose(mouseX, mouseY)) {
            beginClose();
            return true;
        }

        Tab clickedTab = tabAt(mouseX, mouseY);
        if (clickedTab != null) {
            this.activeTab = clickedTab;
            this.contentScroll = 0;
            saveSelectedTab();
            return true;
        }

        int slider = sliderAt(mouseX, mouseY);
        if (slider >= 0) {
            this.activeSlider = slider;
            updateColorSlider(slider, mouseX);
            return true;
        }

        if (clickActiveContent(mouseX, mouseY)) {
            return true;
        }

        if (isInHeader(mouseX, mouseY)) {
            this.dragging = true;
            this.dragOffsetX = physicalMouseX - this.panelX;
            this.dragOffsetY = physicalMouseY - this.panelY;
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.activeSlider >= 0) {
            updateColorSlider(this.activeSlider, toLogicalX(event.x()));
            return true;
        }

        if (this.resizeMode != 0) {
            resizePanel(event.x(), event.y());
            return true;
        }

        if (this.dragging) {
            this.panelX = (int) Math.round(event.x() - this.dragOffsetX);
            this.panelY = (int) Math.round(event.y() - this.dragOffsetY);
            clampPanelToScreen();
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.resizeMode != 0 || this.dragging || this.activeSlider >= 0) {
            this.resizeMode = 0;
            this.dragging = false;
            this.activeSlider = -1;
            savePanel();
            return true;
        }

        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double logicalMouseX = toLogicalX(mouseX);
        double logicalMouseY = toLogicalY(mouseY);
        if (!inside(logicalMouseX, logicalMouseY, contentViewportX(), contentViewportY(), contentViewportWidth(), contentViewportHeight())) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }

        this.contentScroll -= scrollY * 28.0D;
        clampScroll();
        return true;
    }

    @Override
    public void tick() {
        if (this.closing && Util.getMillis() - this.closingAt >= 150L && this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        beginClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawShell(GuiGraphics graphics, int y, int animatedHeight) {
        int bottom = y + animatedHeight;
        graphics.fill(this.panelX + 6, y + 8, this.panelX + this.panelWidth + 6, bottom + 8, 0x77000000);
        graphics.fill(this.panelX, y, this.panelX + this.panelWidth, bottom, 0xEA080D17);
        graphics.fill(this.panelX, y, this.panelX + this.panelWidth, y + 1, 0xFF20283A);
        graphics.fill(this.panelX, bottom - 1, this.panelX + this.panelWidth, bottom, 0xFF171D2B);
        graphics.fill(this.panelX, y, this.panelX + 1, bottom, 0xFF20283A);
        graphics.fill(this.panelX + this.panelWidth - 1, y, this.panelX + this.panelWidth, bottom, 0xFF20283A);
    }

    private void drawSidebar(GuiGraphics graphics, int y) {
        int sidebar = sidebarWidth();
        int accent = AlmatyClient.accentColor(255);
        graphics.fill(this.panelX, y, this.panelX + sidebar, y + this.panelHeight, 0x77050A12);
        graphics.fill(this.panelX + sidebar, y + 1, this.panelX + sidebar + 1, y + this.panelHeight - 1, 0xFF171D2B);

        graphics.drawString(this.font, "A", this.panelX + 24, y + 25, accent, false);
        drawStringFit(graphics, "AlmatyClient", this.panelX + 42, y + 25, sidebar - 58, 0xFFFFFFFF);

        int tabY = y + 74;
        for (Tab tab : Tab.values()) {
            boolean active = tab == this.activeTab;
            int rowY = tabY + tab.ordinal() * 48;
            int rowX = this.panelX + 14;
            int rowW = sidebar - 28;
            if (active) {
                graphics.fill(rowX, rowY, rowX + rowW, rowY + 36, AlmatyClient.accentColor(135));
                graphics.fill(rowX, rowY, rowX + 3, rowY + 36, accent);
                graphics.fill(rowX + rowW - 1, rowY, rowX + rowW, rowY + 36, 0x663E74FF);
            }
            drawStringFit(graphics, tab.icon, rowX + 14, rowY + 11, 16, active ? 0xFFFFFFFF : 0xFF758099);
            drawStringFit(graphics, tab.title, rowX + 38, rowY + 11, rowW - 48, active ? 0xFFFFFFFF : 0xFFADB4C8);
        }

        int cardY = y + this.panelHeight - 86;
        graphics.fill(this.panelX + 14, cardY, this.panelX + sidebar - 14, cardY + 54, 0x77101828);
        drawStringFit(graphics, "v1.0.0", this.panelX + 26, cardY + 13, sidebar - 52, 0xFF8F98B1);
        drawStringFit(graphics, "Release", this.panelX + 26, cardY + 30, sidebar - 52, accent);
    }

    private void drawTopBar(GuiGraphics graphics, int y) {
        int sidebar = sidebarWidth();
        int x = this.panelX + sidebar + 18;
        int top = y + 18;
        int right = this.panelX + this.panelWidth - 18;

        graphics.fill(x, top, x + 230, top + 32, 0x66101828);
        graphics.fill(x, top, x + 230, top + 1, 0xFF20283A);
        drawStringFit(graphics, "Search modules...", x + 34, top + 10, 150, 0xFF9BA3B7);
        graphics.drawString(this.font, "/", x + 205, top + 10, 0xFF9BA3B7, false);

        int closeX = right - 26;
        int minX = closeX - 36;
        int gearX = minX - 40;
        graphics.fill(gearX, top, gearX + 32, top + 32, 0x66101828);
        graphics.drawCenteredString(this.font, "*", gearX + 16, top + 10, AlmatyClient.accentColor(255));
        graphics.drawCenteredString(this.font, "-", minX + 16, top + 10, 0xFFB8C0D4);
        graphics.drawCenteredString(this.font, "X", closeX + 13, top + 10, 0xFFB8C0D4);
    }

    private void drawContent(GuiGraphics graphics, int y) {
        this.maxScroll = Math.max(0, contentHeight() - contentViewportHeight());
        clampScroll();

        int viewportX = contentViewportX();
        int viewportY = y + 76;
        int viewportW = contentViewportWidth();
        int viewportH = contentViewportHeight();
        int listX = moduleListX();
        int listW = moduleListWidth();
        int detailX = detailX();
        int detailW = detailWidth();
        int scroll = (int) Math.round(this.contentScroll);

        graphics.enableScissor(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);

        drawSectionHeader(graphics, listX, viewportY - scroll, listW, this.activeTab.title, this.activeTab.subtitle);
        drawModuleList(graphics, listX, viewportY + 58 - scroll, listW);
        drawDetailPanel(graphics, detailX, viewportY - scroll, detailW);

        graphics.disableScissor();
        drawScrollBar(graphics, viewportX + viewportW - 5, viewportY, viewportH);
    }

    private void drawSectionHeader(GuiGraphics graphics, int x, int y, int w, String title, String subtitle) {
        graphics.fill(x, y, x + w, y + 48, 0x44101828);
        drawStringFit(graphics, title, x + 14, y + 10, w - 28, 0xFFFFFFFF);
        drawStringFit(graphics, subtitle, x + 14, y + 27, w - 28, 0xFF9BA3B7);
    }

    private void drawModuleList(GuiGraphics graphics, int x, int y, int w) {
        if (this.activeTab == Tab.MOVEMENT) {
            drawModuleCard(graphics, x, y, w, "Sprint", "Automatically sprints when moving forward.", AlmatyClient.isAutoSprintEnabled(), true);
        } else if (this.activeTab == Tab.COMBAT) {
            drawModuleCard(graphics, x, y, w, "Aura", "Attacks selected targets in range.", CombatAutomation.isAuraEnabled(), true);
        } else if (this.activeTab == Tab.VISUALS) {
            drawModuleCard(graphics, x, y, w, "Particles", "Water bubbles when hitting entities.", AlmatyClient.isParticlesEnabled(), true);
        } else if (this.activeTab == Tab.PLAYERS) {
            drawModuleCard(graphics, x, y, w, "ESP", "Boxes and labels for selected entities.", AlmatyClient.isEspEnabled(), true);
        } else if (this.activeTab == Tab.OTHER) {
            drawModuleCard(graphics, x, y, w, "Colors", "Adjust the client accent color.", true, true);
            drawSmallActionButton(graphics, resetButtonX(), resetButtonY(), resetButtonWidth(), RESET_BUTTON_HEIGHT, "Reset GUI");
        }
    }

    private void drawModuleCard(GuiGraphics graphics, int x, int y, int w, String title, String subtitle, boolean enabled, boolean active) {
        int border = active ? AlmatyClient.accentColor(230) : 0xFF20283A;
        graphics.fill(x, y, x + w, y + 70, active ? 0x77101828 : 0x55101828);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + 69, x + w, y + 70, 0xFF171D2B);
        graphics.fill(x, y, x + 1, y + 70, border);
        graphics.fill(x + w - 1, y, x + w, y + 70, 0xFF171D2B);
        graphics.fill(x + 16, y + 17, x + 48, y + 49, active ? AlmatyClient.accentColor(115) : 0xFF151B2A);
        drawStringFit(graphics, title, x + 62, y + 16, w - 122, 0xFFFFFFFF);
        drawStringFit(graphics, subtitle, x + 62, y + 33, w - 122, 0xFF9BA3B7);
        drawToggle(graphics, x + w - 52, y + 24, enabled);
    }

    private void drawDetailPanel(GuiGraphics graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + detailHeight(), 0x66101828);
        graphics.fill(x, y, x + w, y + 1, 0xFF20283A);
        graphics.fill(x, y, x + 1, y + detailHeight(), 0xFF20283A);
        graphics.fill(x + w - 1, y, x + w, y + detailHeight(), 0xFF20283A);

        if (this.activeTab == Tab.MOVEMENT) {
            drawDetailHeader(graphics, x, y, w, "Sprint", AlmatyClient.isAutoSprintEnabled());
            drawSettingRow(graphics, x, y + 98, w, "Mode", "Vanilla key-hold sprint.", "Vanilla");
            drawSettingToggle(graphics, x, y + 154, w, "Stop On Collision", "Releases sprint when colliding.", AlmatyClient.sprintStopOnCollision());
            drawSettingRow(graphics, x, y + 210, w, "Start Delay", "Minimal delay before sprinting.", sprintDelayText());
        } else if (this.activeTab == Tab.COMBAT) {
            drawDetailHeader(graphics, x, y, w, "Aura", CombatAutomation.isAuraEnabled());
            drawSettingRow(graphics, x, y + 98, w, "Range", "Target scan distance.", auraRangeText());
            drawCheckRow(graphics, x, y + 154, w, "Jump Only", CombatAutomation.auraJumpOnly());
            drawSettingRow(graphics, x, y + 194, w, "Move Mode", "Jump movement behavior.", CombatAutomation.auraMoveModeText());
            drawSettingRow(graphics, x, y + 250, w, "Target Mode", "Target priority selector.", CombatAutomation.auraTargetModeText());
            drawSettingToggle(graphics, x, y + 306, w, "Rotate To Target", "Looks at the target hitbox center.", CombatAutomation.auraRotate());
            drawCheckRow(graphics, x, y + 366, w, "Players", CombatAutomation.auraPlayers());
            drawCheckRow(graphics, x, y + 406, w, "Mobs", CombatAutomation.auraMobs());
        } else if (this.activeTab == Tab.VISUALS) {
            drawDetailHeader(graphics, x, y, w, "Particles", AlmatyClient.isParticlesEnabled());
            drawSettingToggle(graphics, x, y + 98, w, "Water Bubbles", "Spawn bubbles after entity hits.", AlmatyClient.isParticlesEnabled());
        } else if (this.activeTab == Tab.PLAYERS) {
            drawDetailHeader(graphics, x, y, w, "ESP", AlmatyClient.isEspEnabled());
            drawCheckRow(graphics, x, y + 98, w, "Players", AlmatyClient.espPlayers());
            drawCheckRow(graphics, x, y + 138, w, "Mobs", AlmatyClient.espMobs());
            drawCheckRow(graphics, x, y + 178, w, "Items", AlmatyClient.espItems());
            drawCheckRow(graphics, x, y + 238, w, "Name", AlmatyClient.espName());
            drawCheckRow(graphics, x, y + 278, w, "Health", AlmatyClient.espHealth());
        } else if (this.activeTab == Tab.OTHER) {
            drawDetailHeader(graphics, x, y, w, "Color", true);
            drawColorSlider(graphics, x + 22, y + 106, w - 44, "Red", AlmatyClient.guiRed(), 0);
            drawColorSlider(graphics, x + 22, y + 160, w - 44, "Green", AlmatyClient.guiGreen(), 1);
            drawColorSlider(graphics, x + 22, y + 214, w - 44, "Blue", AlmatyClient.guiBlue(), 2);
        }
    }

    private void drawDetailHeader(GuiGraphics graphics, int x, int y, int w, String title, boolean enabled) {
        graphics.fill(x + 22, y + 24, x + 74, y + 76, AlmatyClient.accentColor(115));
        drawStringFit(graphics, title, x + 92, y + 30, w - 180, 0xFFFFFFFF);
        drawStatusPill(graphics, x + 92, y + 52, enabled);
        drawToggle(graphics, x + w - 70, y + 38, enabled);
        graphics.fill(x + 20, y + 90, x + w - 20, y + 91, 0xFF1B2233);
    }

    private void drawStatusPill(GuiGraphics graphics, int x, int y, boolean enabled) {
        int w = enabled ? 56 : 64;
        graphics.fill(x, y, x + w, y + 18, enabled ? 0x552EDBC3 : 0x552A3144);
        drawStringFit(graphics, enabled ? "Enabled" : "Disabled", x + 8, y + 5, w - 16, enabled ? 0xFF36F0D4 : 0xFF9BA3B7);
    }

    private void drawSettingRow(GuiGraphics graphics, int x, int y, int w, String title, String hint, String value) {
        graphics.fill(x + 20, y, x + w - 20, y + 48, 0x44101828);
        drawStringFit(graphics, title, x + 34, y + 10, w - 180, 0xFFE7ECFF);
        drawStringFit(graphics, hint, x + 34, y + 27, w - 180, 0xFF9BA3B7);
        graphics.fill(x + w - 150, y + 12, x + w - 34, y + 36, 0x66151B2A);
        drawStringFit(graphics, value, x + w - 132, y + 19, 94, 0xFFFFFFFF);
    }

    private void drawSettingToggle(GuiGraphics graphics, int x, int y, int w, String title, String hint, boolean enabled) {
        graphics.fill(x + 20, y, x + w - 20, y + 48, 0x44101828);
        drawStringFit(graphics, title, x + 34, y + 10, w - 130, 0xFFE7ECFF);
        drawStringFit(graphics, hint, x + 34, y + 27, w - 130, 0xFF9BA3B7);
        drawToggle(graphics, x + w - 70, y + 13, enabled);
    }

    private void drawCheckRow(GuiGraphics graphics, int x, int y, int w, String title, boolean enabled) {
        graphics.fill(x + 20, y, x + w - 20, y + 34, 0x44101828);
        graphics.fill(x + 34, y + 10, x + 46, y + 22, enabled ? AlmatyClient.accentColor(255) : 0xFF273142);
        drawStringFit(graphics, title, x + 58, y + 10, w - 92, 0xFFE7ECFF);
    }

    private void drawActionButton(GuiGraphics graphics, int x, int y, int w, String title) {
        graphics.fill(x, y, x + w, y + 34, AlmatyClient.accentColor(110));
        graphics.fill(x, y, x + w, y + 1, AlmatyClient.accentColor(255));
        graphics.drawCenteredString(this.font, title, x + w / 2, y + 12, 0xFFFFFFFF);
    }

    private void drawSmallActionButton(GuiGraphics graphics, int x, int y, int w, int h, String title) {
        graphics.fill(x, y, x + w, y + h, AlmatyClient.accentColor(115));
        graphics.fill(x, y, x + w, y + 1, AlmatyClient.accentColor(255));
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF171D2B);
        graphics.drawCenteredString(this.font, title, x + w / 2, y + 9, 0xFFFFFFFF);
    }

    private void drawColorSlider(GuiGraphics graphics, int x, int y, int w, String label, int value, int index) {
        drawStringFit(graphics, label, x, y, Math.max(24, w / 4), 0xFFFFFFFF);
        drawStringFit(graphics, Integer.toString(value), x + w - 34, y, 32, 0xFFBFD7FF);
        int start = x;
        int end = x + w;
        int trackY = y + 24;
        int knob = start + Math.round((end - start) * (value / 255.0F));
        graphics.fill(start, trackY, end, trackY + 4, 0xFF222938);
        graphics.fill(start, trackY, knob, trackY + 4, AlmatyClient.accentColor(255));
        graphics.fill(knob - 4, trackY - 5, knob + 4, trackY + 9, 0xFFFFFFFF);
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, boolean enabled) {
        graphics.fill(x, y, x + 42, y + 22, enabled ? AlmatyClient.accentColor(255) : 0xFF273142);
        graphics.fill(x + (enabled ? 23 : 4), y + 4, x + (enabled ? 38 : 19), y + 19, 0xFFE9EEFF);
    }

    private void drawScrollBar(GuiGraphics graphics, int x, int y, int h) {
        if (this.maxScroll <= 0) {
            return;
        }

        graphics.fill(x, y, x + 3, y + h, 0x55222938);
        int thumbH = Math.max(24, h * h / Math.max(h + this.maxScroll, 1));
        int thumbY = y + (int) Math.round((h - thumbH) * (this.contentScroll / Math.max(1, this.maxScroll)));
        graphics.fill(x, thumbY, x + 3, thumbY + thumbH, AlmatyClient.accentColor(220));
    }

    private void drawResizeHandles(GuiGraphics graphics, int y) {
        int right = this.panelX + this.panelWidth;
        int bottom = y + this.panelHeight;
        graphics.fill(right - 18, bottom - 5, right - 4, bottom - 3, 0x99FFFFFF);
        graphics.fill(right - 14, bottom - 10, right - 4, bottom - 8, 0x99FFFFFF);
        graphics.fill(right - 9, bottom - 15, right - 4, bottom - 13, 0x99FFFFFF);
    }

    private boolean clickActiveContent(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, contentViewportX(), contentViewportY(), contentViewportWidth(), contentViewportHeight())) {
            return false;
        }

        int y = (int) Math.round(mouseY + this.contentScroll);
        if (this.activeTab == Tab.MOVEMENT) {
            if (inside(mouseX, y, moduleListX(), contentViewportY() + 58, moduleListWidth(), 70)
                    || inside(mouseX, y, detailX(), contentViewportY(), detailWidth(), 90)) {
                AlmatyClient.toggleAutoSprint();
                return true;
            }
            return clickSprintDetail(mouseX, y);
        } else if (this.activeTab == Tab.COMBAT) {
            if (inside(mouseX, y, moduleListX(), contentViewportY() + 58, moduleListWidth(), 70)
                    || inside(mouseX, y, detailX(), contentViewportY(), detailWidth(), 90)) {
                CombatAutomation.setAuraEnabled(!CombatAutomation.isAuraEnabled());
                return true;
            }
            return clickAuraDetail(mouseX, y);
        } else if (this.activeTab == Tab.VISUALS) {
            if (inside(mouseX, y, moduleListX(), contentViewportY() + 58, moduleListWidth(), 70)
                    || inside(mouseX, y, detailX(), contentViewportY(), detailWidth(), 160)) {
                AlmatyClient.setParticlesEnabled(!AlmatyClient.isParticlesEnabled());
                return true;
            }
        } else if (this.activeTab == Tab.PLAYERS) {
            if (inside(mouseX, y, moduleListX(), contentViewportY() + 58, moduleListWidth(), 70)
                    || inside(mouseX, y, detailX(), contentViewportY(), detailWidth(), 90)) {
                AlmatyClient.setEspEnabled(!AlmatyClient.isEspEnabled());
                return true;
            }
            return clickEspDetail(mouseX, y);
        } else if (this.activeTab == Tab.OTHER) {
            if (inside(mouseX, mouseY, resetButtonX(), resetButtonY(), resetButtonWidth(), RESET_BUTTON_HEIGHT)) {
                resetPanel();
                return true;
            }
        }

        return false;
    }

    private boolean openBindSelection(double mouseX, double mouseY) {
        ClientModule module = moduleAt(mouseX, mouseY);
        if (module == null || this.minecraft == null) {
            return false;
        }

        this.minecraft.setScreen(new BindSelectionScreen(this, module));
        return true;
    }

    private ClientModule moduleAt(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, contentViewportX(), contentViewportY(), contentViewportWidth(), contentViewportHeight())) {
            return null;
        }

        int y = (int) Math.round(mouseY + this.contentScroll);
        boolean moduleCard = inside(mouseX, y, moduleListX(), contentViewportY() + 58, moduleListWidth(), 70);
        boolean detailHeader = inside(mouseX, y, detailX(), contentViewportY(), detailWidth(), 90);
        if (!moduleCard && !detailHeader) {
            return null;
        }

        if (this.activeTab == Tab.MOVEMENT) {
            return ClientModule.SPRINT;
        }
        if (this.activeTab == Tab.COMBAT) {
            return ClientModule.AURA;
        }
        if (this.activeTab == Tab.VISUALS) {
            return ClientModule.PARTICLES;
        }
        if (this.activeTab == Tab.PLAYERS) {
            return ClientModule.ESP;
        }
        return null;
    }

    private boolean clickSprintDetail(double mouseX, int y) {
        int x = detailX();
        int w = detailWidth();
        int base = contentViewportY();
        if (inside(mouseX, y, x + 20, base + 154, w - 40, 48)) {
            AlmatyClient.setSprintStopOnCollision(!AlmatyClient.sprintStopOnCollision());
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 210, w - 40, 48)) {
            AlmatyClient.cycleSprintStartDelayTicks();
            return true;
        }
        return false;
    }

    private boolean clickAuraDetail(double mouseX, int y) {
        int x = detailX();
        int w = detailWidth();
        int base = contentViewportY();
        if (inside(mouseX, y, x + 20, base + 98, w - 40, 48)) {
            CombatAutomation.cycleAuraRange();
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 154, w - 40, 34)) {
            CombatAutomation.setAuraJumpOnly(!CombatAutomation.auraJumpOnly());
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 194, w - 40, 48)) {
            CombatAutomation.cycleAuraMoveMode();
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 250, w - 40, 48)) {
            CombatAutomation.cycleAuraTargetMode();
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 306, w - 40, 48)) {
            CombatAutomation.setAuraRotate(!CombatAutomation.auraRotate());
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 366, w - 40, 34)) {
            CombatAutomation.setAuraPlayers(!CombatAutomation.auraPlayers());
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 406, w - 40, 34)) {
            CombatAutomation.setAuraMobs(!CombatAutomation.auraMobs());
            return true;
        }
        return false;
    }

    private boolean clickEspDetail(double mouseX, int y) {
        int x = detailX();
        int w = detailWidth();
        int base = contentViewportY();
        if (inside(mouseX, y, x + 20, base + 98, w - 40, 34)) {
            AlmatyClient.setEspPlayers(!AlmatyClient.espPlayers());
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 138, w - 40, 34)) {
            AlmatyClient.setEspMobs(!AlmatyClient.espMobs());
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 178, w - 40, 34)) {
            AlmatyClient.setEspItems(!AlmatyClient.espItems());
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 238, w - 40, 34)) {
            AlmatyClient.setEspName(!AlmatyClient.espName());
            return true;
        }
        if (inside(mouseX, y, x + 20, base + 278, w - 40, 34)) {
            AlmatyClient.setEspHealth(!AlmatyClient.espHealth());
            return true;
        }
        return false;
    }

    private int sliderAt(double mouseX, double mouseY) {
        if (this.activeTab != Tab.OTHER || !inside(mouseX, mouseY, contentViewportX(), contentViewportY(), contentViewportWidth(), contentViewportHeight())) {
            return -1;
        }

        int detailX = detailX();
        int w = detailWidth();
        int y = (int) Math.round(mouseY + this.contentScroll);
        int[] ys = {contentViewportY() + 130, contentViewportY() + 184, contentViewportY() + 238};
        for (int i = 0; i < ys.length; i++) {
            if (inside(mouseX, y, detailX + 18, ys[i] - 10, w - 36, 24)) {
                return i;
            }
        }
        return -1;
    }

    private void updateColorSlider(int slider, double mouseX) {
        int start = detailX() + 22;
        int end = start + detailWidth() - 44;
        int value = (int) Math.round((Math.max(start, Math.min(end, mouseX)) - start) * 255.0D / Math.max(1, end - start));
        int red = AlmatyClient.guiRed();
        int green = AlmatyClient.guiGreen();
        int blue = AlmatyClient.guiBlue();

        if (slider == 0) {
            red = value;
        } else if (slider == 1) {
            green = value;
        } else {
            blue = value;
        }
        AlmatyClient.setGuiColor(red, green, blue);
    }

    private float openProgress() {
        if (this.closing) {
            float close = Math.min(1.0F, (Util.getMillis() - this.closingAt) / 150.0F);
            return 1.0F - close * close;
        }

        float open = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 170.0F);
        return 1.0F - (1.0F - open) * (1.0F - open);
    }

    private void beginClose() {
        if (!this.closing) {
            savePanel();
            saveSelectedTab();
            this.closing = true;
            this.closingAt = Util.getMillis();
        }
    }

    private Tab tabAt(double mouseX, double mouseY) {
        int sidebar = sidebarWidth();
        int x = this.panelX + 14;
        int w = sidebar - 28;
        for (Tab tab : Tab.values()) {
            int y = this.panelY + 74 + tab.ordinal() * 48;
            if (inside(mouseX, mouseY, x, y, w, 36)) {
                return tab;
            }
        }
        return null;
    }

    private int findResizeMode(double mouseX, double mouseY) {
        int visualWidth = scaled(this.panelWidth);
        int visualHeight = scaled(this.panelHeight);
        boolean inVerticalRange = mouseY >= this.panelY - RESIZE_HANDLE
                && mouseY <= this.panelY + visualHeight + RESIZE_HANDLE;
        boolean inHorizontalRange = mouseX >= this.panelX - RESIZE_HANDLE
                && mouseX <= this.panelX + visualWidth + RESIZE_HANDLE;
        boolean left = inVerticalRange && mouseX >= this.panelX - RESIZE_HANDLE && mouseX <= this.panelX + RESIZE_HANDLE;
        boolean right = inVerticalRange && mouseX >= this.panelX + visualWidth - RESIZE_HANDLE
                && mouseX <= this.panelX + visualWidth + RESIZE_HANDLE;
        boolean top = inHorizontalRange && mouseY >= this.panelY - RESIZE_HANDLE && mouseY <= this.panelY + RESIZE_HANDLE;
        boolean bottom = inHorizontalRange && mouseY >= this.panelY + visualHeight - RESIZE_HANDLE
                && mouseY <= this.panelY + visualHeight + RESIZE_HANDLE;

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

    private void resizePanel(double mouseX, double mouseY) {
        int dx = (int) Math.round(mouseX - this.resizeStartMouseX);
        int dy = (int) Math.round(mouseY - this.resizeStartMouseY);
        int x = this.resizeStartPanelX;
        int y = this.resizeStartPanelY;
        int width = this.resizeStartPanelWidth;
        int height = this.resizeStartPanelHeight;

        if ((this.resizeMode & RESIZE_LEFT) != 0) {
            int fixedRight = this.resizeStartPanelX + scaled(this.resizeStartPanelWidth);
            x = Math.max(6, Math.min(this.resizeStartPanelX + dx, fixedRight - scaled(MIN_PANEL_WIDTH)));
            width = unscaled(fixedRight - x);
        } else if ((this.resizeMode & RESIZE_RIGHT) != 0) {
            int right = Math.min(this.width - 6, Math.max(this.resizeStartPanelX + scaled(MIN_PANEL_WIDTH), this.resizeStartPanelX + scaled(this.resizeStartPanelWidth) + dx));
            width = unscaled(right - this.resizeStartPanelX);
        }

        if ((this.resizeMode & RESIZE_TOP) != 0) {
            int fixedBottom = this.resizeStartPanelY + scaled(this.resizeStartPanelHeight);
            y = Math.max(6, Math.min(this.resizeStartPanelY + dy, fixedBottom - scaled(MIN_PANEL_HEIGHT)));
            height = unscaled(fixedBottom - y);
        } else if ((this.resizeMode & RESIZE_BOTTOM) != 0) {
            int bottom = Math.min(this.height - 6, Math.max(this.resizeStartPanelY + scaled(MIN_PANEL_HEIGHT), this.resizeStartPanelY + scaled(this.resizeStartPanelHeight) + dy));
            height = unscaled(bottom - this.resizeStartPanelY);
        }

        this.panelX = x;
        this.panelY = y;
        this.panelWidth = width;
        this.panelHeight = height;
        clampPanelToScreen();
        clampScroll();
    }

    private void clampPanelToScreen() {
        this.panelWidth = Math.min(Math.max(this.panelWidth, MIN_PANEL_WIDTH), Math.max(MIN_PANEL_WIDTH, unscaled(this.width - 12)));
        this.panelHeight = Math.min(Math.max(this.panelHeight, MIN_PANEL_HEIGHT), Math.max(MIN_PANEL_HEIGHT, unscaled(this.height - 12)));
        this.panelX = Math.max(6, Math.min(this.panelX, this.width - scaled(this.panelWidth) - 6));
        this.panelY = Math.max(6, Math.min(this.panelY, this.height - scaled(this.panelHeight) - 6));
    }

    private void clampScroll() {
        this.maxScroll = Math.max(0, contentHeight() - contentViewportHeight());
        this.contentScroll = Math.max(0.0D, Math.min(this.contentScroll, this.maxScroll));
    }

    private void savePanel() {
        AlmatyConfig.setInt("gui.x", this.panelX);
        AlmatyConfig.setInt("gui.y", this.panelY);
        AlmatyConfig.setInt("gui.width", this.panelWidth);
        AlmatyConfig.setInt("gui.height", this.panelHeight);
        saveSelectedTab();
    }

    private void resetPanel() {
        this.panelWidth = 860;
        this.panelHeight = 500;
        this.panelX = this.width / 2 - scaled(this.panelWidth) / 2;
        this.panelY = this.height / 2 - scaled(this.panelHeight) / 2;
        this.contentScroll = 0;
        clampPanelToScreen();
        savePanel();
    }

    private void saveSelectedTab() {
        AlmatyConfig.setString("gui.selectedTab", this.activeTab.name());
    }

    private Tab readSavedTab() {
        String raw = AlmatyConfig.getString("gui.selectedTab", Tab.MOVEMENT.name());
        try {
            return Tab.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return Tab.MOVEMENT;
        }
    }

    private boolean isInHeader(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, this.panelX, this.panelY, this.panelWidth, 64);
    }

    private boolean isInClose(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, this.panelX + this.panelWidth - 44, this.panelY + 18, 30, 32);
    }

    private int sidebarWidth() {
        return Math.max(150, Math.min(210, this.panelWidth / 4));
    }

    private int moduleListX() {
        return this.panelX + sidebarWidth() + 18;
    }

    private int moduleListWidth() {
        return Math.max(210, Math.min(290, (this.panelWidth - sidebarWidth() - 56) * 38 / 100));
    }

    private int resetButtonX() {
        return moduleListX();
    }

    private int resetButtonY() {
        return contentViewportY() + contentViewportHeight() - RESET_BUTTON_HEIGHT - 8;
    }

    private int resetButtonWidth() {
        return Math.min(124, moduleListWidth());
    }

    private int detailX() {
        return moduleListX() + moduleListWidth() + 16;
    }

    private int detailWidth() {
        return this.panelX + this.panelWidth - 20 - detailX();
    }

    private int contentViewportX() {
        return moduleListX();
    }

    private int contentViewportY() {
        return this.panelY + 76;
    }

    private int contentViewportWidth() {
        return this.panelX + this.panelWidth - 20 - contentViewportX();
    }

    private int contentViewportHeight() {
        return Math.max(120, this.panelHeight - 96);
    }

    private int contentHeight() {
        return Math.max(360, detailHeight() + 16);
    }

    private int detailHeight() {
        if (this.activeTab == Tab.COMBAT) {
            return 460;
        }
        if (this.activeTab == Tab.PLAYERS) {
            return 340;
        }
        if (this.activeTab == Tab.OTHER) {
            return 350;
        }
        return 280;
    }

    private void drawStringFit(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0 || text == null || text.isEmpty()) {
            return;
        }

        graphics.drawString(this.font, fitText(text, maxWidth), x, y, color, false);
    }

    private String fitText(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        int suffixWidth = this.font.width(suffix);
        if (maxWidth <= suffixWidth) {
            return this.font.plainSubstrByWidth(text, Math.max(1, maxWidth));
        }

        return this.font.plainSubstrByWidth(text, maxWidth - suffixWidth) + suffix;
    }

    private static String sprintDelayText() {
        int ticks = AlmatyClient.sprintStartDelayTicks();
        return ticks == 1 ? "1 tick" : ticks + " ticks";
    }

    private static String auraRangeText() {
        return String.format(java.util.Locale.ROOT, "%.1f blocks", CombatAutomation.auraRange());
    }

    private double toLogicalX(double mouseX) {
        return this.panelX + (mouseX - this.panelX) / GUI_SCALE;
    }

    private double toLogicalY(double mouseY) {
        return this.panelY + (mouseY - this.panelY) / GUI_SCALE;
    }

    private static int scaled(int value) {
        return Math.round(value * GUI_SCALE);
    }

    private static int unscaled(int value) {
        return Math.round(value / GUI_SCALE);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static int colorWithAlpha(int alpha, int red, int green, int blue) {
        return ((alpha & 255) << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
    }

    private enum Tab {
        MOVEMENT("Movement", "Improve your movement.", "M"),
        COMBAT("Combat", "Automated combat modules.", "C"),
        VISUALS("Visuals", "Client-side effects.", "V"),
        PLAYERS("Players", "Entity ESP controls.", "P"),
        OTHER("Other", "Interface settings.", "O");

        private final String title;
        private final String subtitle;
        private final String icon;

        Tab(String title, String subtitle, String icon) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
        }
    }
}
