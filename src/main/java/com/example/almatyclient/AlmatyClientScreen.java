package com.example.almatyclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.Locale;

public final class AlmatyClientScreen extends Screen {
    private static final int MIN_PANEL_WIDTH = 390;
    private static final int MIN_PANEL_HEIGHT = 220;
    private static final int RESIZE_HANDLE = 7;
    private static final int RESIZE_LEFT = 1;
    private static final int RESIZE_RIGHT = 2;
    private static final int RESIZE_TOP = 4;
    private static final int RESIZE_BOTTOM = 8;

    private static final String[] CATEGORIES = {
            "Movement", "Combat", "Render", "Player", "World", "Misc", "Settings"
    };

    private static final ModuleRow[] MODULES = {
            new ModuleRow("Sprint", "Automatically sprints.", true),
            new ModuleRow("Fly", "Allows you to fly.", false),
            new ModuleRow("Speed", "Increases movement.", false),
            new ModuleRow("Long Jump", "Jump much further.", false),
            new ModuleRow("Step", "Step up blocks.", false),
            new ModuleRow("No Slow", "Removes slowdown.", false)
    };

    private final Screen parent;
    private final long openedAt;
    private String toastText = "";
    private long toastUntil;
    private int selectedCategory;
    private int selectedModule;
    private boolean layoutInitialized;
    private int panelX;
    private int panelY;
    private int panelWidth = 430;
    private int panelHeight = 240;
    private int resizeMode;
    private double lastMouseX;
    private double lastMouseY;
    private EditBox searchBox;

    public AlmatyClientScreen(Screen parent) {
        super(Component.translatable("screen.almatyclient.title"));
        this.parent = parent;
        this.openedAt = Util.getMillis();
    }

    @Override
    protected void init() {
        if (!this.layoutInitialized) {
            this.panelWidth = Math.min(this.panelWidth, Math.max(MIN_PANEL_WIDTH, this.width - 14));
            this.panelHeight = Math.min(this.panelHeight, Math.max(MIN_PANEL_HEIGHT, this.height - 14));
            this.panelX = this.width / 2 - this.panelWidth / 2;
            this.panelY = this.height / 2 - this.panelHeight / 2;
            this.layoutInitialized = true;
        } else {
            clampPanelToScreen();
        }

        this.searchBox = this.addRenderableWidget(new EditBox(
                this.font,
                0,
                0,
                96,
                18,
                Component.literal("Search modules")
        ));
        this.searchBox.setMaxLength(32);
        this.searchBox.setHint(Component.literal("Search modules..."));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xA8000000);

        float progress = Math.min(1.0F, (Util.getMillis() - this.openedAt) / 180.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        int animatedPanelY = this.panelY + Math.round((1.0F - eased) * 10.0F);

        layoutSearchBox(animatedPanelY);
        drawShell(graphics, animatedPanelY);
        drawSidebar(graphics, animatedPanelY);
        drawTopBar(graphics, animatedPanelY);
        drawModuleList(graphics, animatedPanelY);
        drawDetailPanel(graphics, animatedPanelY);
        drawResizeHandles(graphics, animatedPanelY);

        super.render(graphics, mouseX, mouseY, delta);
        drawToast(graphics);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        this.resizeMode = findResizeMode(mouseX, mouseY);
        if (this.resizeMode != 0) {
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }

        if (isInClose(mouseX, mouseY)) {
            this.onClose();
            return true;
        }

        if (isInSprintToggle(mouseX, mouseY) || isInSprintModule(mouseX, mouseY)) {
            AlmatyClient.toggleAutoSprint();
            showToast(AlmatyClient.isAutoSprintEnabled() ? "Sprint Enabled" : "Sprint Disabled");
            return true;
        }

        int category = categoryAt(mouseX, mouseY);
        if (category >= 0) {
            this.selectedCategory = category;
            return true;
        }

        int module = moduleAt(mouseX, mouseY);
        if (module >= 0) {
            this.selectedModule = module;
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

    private void drawShell(GuiGraphics graphics, int y) {
        graphics.fill(this.panelX + 5, y + 6, this.panelX + this.panelWidth + 5, y + this.panelHeight + 6, 0x70000000);
        graphics.fill(this.panelX, y, this.panelX + this.panelWidth, y + this.panelHeight, 0xE80A0E18);
        graphics.fill(this.panelX, y, this.panelX + this.panelWidth, y + 1, 0x553A4158);
        graphics.fill(this.panelX, y, this.panelX + 1, y + this.panelHeight, 0x553A4158);
        graphics.fill(this.panelX + this.panelWidth - 1, y, this.panelX + this.panelWidth, y + this.panelHeight, 0x553A4158);
        graphics.fill(this.panelX, y + this.panelHeight - 1, this.panelX + this.panelWidth, y + this.panelHeight, 0x553A4158);
    }

    private void drawSidebar(GuiGraphics graphics, int y) {
        int sidebarWidth = sidebarWidth();
        int x = this.panelX;

        graphics.fill(x, y, x + sidebarWidth, y + this.panelHeight, 0xAA070B14);
        graphics.fill(x + sidebarWidth, y + 8, x + sidebarWidth + 1, y + this.panelHeight - 8, 0x553A4158);

        graphics.drawString(this.font, "Almaty", x + 16, y + 18, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "Client", x + 51, y + 18, 0xFF7B55FF, false);

        int itemY = y + 50;
        for (int i = 0; i < CATEGORIES.length; i++) {
            drawSidebarItem(graphics, i, x + 8, itemY + i * 25);
        }

        int profileY = y + this.panelHeight - 74;
        graphics.fill(x + 8, profileY, x + sidebarWidth - 8, profileY + 52, 0xB2141930);
        graphics.fill(x + 8, profileY, x + 10, profileY + 52, 0xAA7A42FF);
        graphics.fill(x + 18, profileY + 12, x + 38, profileY + 32, 0xFF6D4FFF);
        graphics.fill(x + 21, profileY + 15, x + 35, profileY + 29, 0xFFB9D4FF);
        graphics.drawString(this.font, playerName(), x + 46, profileY + 10, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "Premium", x + 46, profileY + 24, 0xFF8E6CFF, false);
        graphics.drawString(this.font, "v1.0.0", x + 16, y + this.panelHeight - 17, 0xFF7E859A, false);
    }

    private void drawTopBar(GuiGraphics graphics, int y) {
        int contentX = this.panelX + sidebarWidth() + 12;
        int topY = y + 13;
        int topRight = this.panelX + this.panelWidth - 16;

        graphics.fill(contentX, topY, contentX + 115, topY + 18, 0x88111824);
        graphics.drawString(this.font, "/", contentX + 102, topY + 5, 0xFF8990A3, false);

        int accountW = Math.min(88, Math.max(62, topRight - contentX - 156));
        graphics.fill(topRight - accountW - 56, topY, topRight - 56, topY + 18, 0x88111824);
        graphics.drawString(this.font, playerName(), topRight - accountW - 46, topY + 5, 0xFFE8EAFF, false);
        graphics.fill(topRight - 45, topY, topRight - 25, topY + 18, 0x88111824);
        graphics.drawCenteredString(this.font, "!", topRight - 35, topY + 5, 0xFFBFC6E6);
        graphics.fill(topRight - 22, topY, topRight - 2, topY + 18, 0x88111824);
        graphics.drawCenteredString(this.font, "X", topRight - 12, topY + 5, 0xFFBFC6E6);
    }

    private void drawModuleList(GuiGraphics graphics, int y) {
        int listX = listX();
        int listY = y + 48;
        int listW = listWidth();
        int rowH = 34;

        graphics.fill(listX, listY - 12, listX + listW, y + this.panelHeight - 14, 0x77111824);
        graphics.drawString(this.font, "Movement", listX + 12, listY - 3, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "Improve your movement.", listX + 12, listY + 9, 0xFF9EA6BC, false);

        int rowY = listY + 28;
        int drawn = 0;
        String filter = this.searchBox == null ? "" : this.searchBox.getValue().toLowerCase(Locale.ROOT);

        for (int i = 0; i < MODULES.length && drawn < maxModuleRows(); i++) {
            ModuleRow module = MODULES[i];
            if (!filter.isEmpty() && !module.title().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }

            drawModuleRow(graphics, i, listX + 8, rowY + drawn * (rowH + 6), listW - 16, rowH);
            drawn++;
        }
    }

    private void drawDetailPanel(GuiGraphics graphics, int y) {
        int detailX = detailX();
        int detailY = y + 48;
        int detailW = detailWidth();
        int detailH = this.panelHeight - 62;

        graphics.fill(detailX, detailY, detailX + detailW, detailY + detailH, 0x77111824);
        graphics.fill(detailX + 14, detailY + 14, detailX + 44, detailY + 44, 0xFF251B57);
        graphics.drawCenteredString(this.font, "RUN", detailX + 29, detailY + 25, 0xFFCFC4FF);

        graphics.drawString(this.font, "Sprint", detailX + 56, detailY + 15, 0xFFFFFFFF, false);
        drawEnabledPill(graphics, detailX + 94, detailY + 14);
        graphics.drawString(this.font, "Automatically sprints when moving forward.", detailX + 56, detailY + 32, 0xFFBBC2D4, false);
        drawToggle(graphics, detailX + detailW - 40, detailY + 18, AlmatyClient.isAutoSprintEnabled());

        int tabY = detailY + 64;
        String[] tabs = {"General", "Options", "Visuals", "Keybinds"};
        int tabX = detailX + 12;
        for (int i = 0; i < tabs.length; i++) {
            int color = i == 0 ? 0xFFFFFFFF : 0xFF8990A3;
            graphics.drawString(this.font, tabs[i], tabX, tabY, color, false);
            if (i == 0) {
                graphics.fill(tabX, tabY + 13, tabX + 34, tabY + 15, 0xFF8C4DFF);
            }
            tabX += 52;
        }

        int settingsY = tabY + 28;
        int bottom = detailY + detailH - 10;
        if (settingsY + 28 <= bottom) {
            drawSettingRow(graphics, detailX + 10, settingsY, detailW - 20, "Mode", "Vanilla", false);
        }
        if (settingsY + 60 <= bottom) {
            drawSettingRow(graphics, detailX + 10, settingsY + 32, detailW - 20, "Multi Direction", "ON", true);
        }
        if (settingsY + 94 <= bottom) {
            drawSliderRow(graphics, detailX + 10, settingsY + 64, detailW - 20, "Speed", "1.30x", 0.46F);
        }
        if (settingsY + 128 <= bottom) {
            drawSliderRow(graphics, detailX + 10, settingsY + 98, detailW - 20, "Start Delay", "0.0s", 0.02F);
        }
    }

    private void drawSidebarItem(GuiGraphics graphics, int index, int x, int y) {
        boolean selected = index == this.selectedCategory;
        int w = sidebarWidth() - 16;
        if (selected) {
            graphics.fill(x, y, x + w, y + 20, 0xCC2C1B77);
            graphics.fill(x, y + 18, x + w, y + 20, 0xFF7548FF);
        }

        String icon = CATEGORIES[index].substring(0, Math.min(2, CATEGORIES[index].length())).toUpperCase(Locale.ROOT);
        graphics.drawString(this.font, icon, x + 10, y + 6, selected ? 0xFFD9D0FF : 0xFF747C93, false);
        graphics.drawString(this.font, CATEGORIES[index], x + 34, y + 6, selected ? 0xFFFFFFFF : 0xFFB3BACD, false);
    }

    private void drawModuleRow(GuiGraphics graphics, int index, int x, int y, int w, int h) {
        ModuleRow module = MODULES[index];
        boolean selected = index == this.selectedModule;
        boolean enabled = module.sprint() && AlmatyClient.isAutoSprintEnabled();

        graphics.fill(x, y, x + w, y + h, selected ? 0xBB271A5A : 0x88111824);
        if (selected) {
            graphics.fill(x, y, x + 2, y + h, 0xFF8C4DFF);
            graphics.fill(x, y, x + w, y + 1, 0xFF8C4DFF);
        }
        graphics.fill(x + 8, y + 7, x + 26, y + 25, enabled ? 0xFF4328B8 : 0xFF20263A);
        graphics.drawString(this.font, module.title(), x + 34, y + 6, 0xFFFFFFFF, false);
        graphics.drawString(this.font, module.description(), x + 34, y + 18, 0xFFAAB1C3, false);
        drawToggle(graphics, x + w - 32, y + 8, enabled);
    }

    private void drawEnabledPill(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 42, y + 14, 0x80204747);
        graphics.fill(x + 5, y + 5, x + 8, y + 8, 0xFF2FE4C6);
        graphics.drawString(this.font, "Enabled", x + 13, y + 3, 0xFF65F7DF, false);
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, boolean enabled) {
        graphics.fill(x, y, x + 30, y + 16, enabled ? 0xFF5A38FF : 0xFF20263A);
        graphics.fill(x + (enabled ? 17 : 3), y + 3, x + (enabled ? 27 : 13), y + 13, 0xFFE8ECFF);
    }

    private void drawSettingRow(GuiGraphics graphics, int x, int y, int w, String title, String value, boolean toggle) {
        graphics.fill(x, y, x + w, y + 28, 0x55111824);
        graphics.drawString(this.font, title, x + 10, y + 6, 0xFFE8EAFF, false);
        graphics.drawString(this.font, title.equals("Mode") ? "How sprint works." : "Sprint in all directions.", x + 10, y + 17, 0xFF9098AD, false);
        if (toggle) {
            drawToggle(graphics, x + w - 40, y + 6, true);
        } else {
            graphics.fill(x + w - 76, y + 5, x + w - 8, y + 23, 0x88151B2C);
            graphics.drawString(this.font, value, x + w - 66, y + 10, 0xFFFFFFFF, false);
        }
    }

    private void drawSliderRow(GuiGraphics graphics, int x, int y, int w, String title, String value, float amount) {
        graphics.fill(x, y, x + w, y + 30, 0x55111824);
        graphics.drawString(this.font, title, x + 10, y + 5, 0xFFE8EAFF, false);
        graphics.drawString(this.font, title.equals("Speed") ? "Sprint speed multiplier." : "Delay before sprinting.", x + 10, y + 16, 0xFF9098AD, false);
        graphics.fill(x + 10, y + 25, x + w - 12, y + 27, 0xFF20263A);
        int filled = x + 10 + Math.round((w - 22) * amount);
        graphics.fill(x + 10, y + 25, filled, y + 27, 0xFF8C4DFF);
        graphics.fill(filled - 3, y + 22, filled + 3, y + 29, 0xFFC47BFF);
        graphics.fill(x + w - 46, y + 4, x + w - 10, y + 22, 0x88151B2C);
        graphics.drawString(this.font, value, x + w - 38, y + 9, 0xFFFFFFFF, false);
    }

    private void drawToast(GuiGraphics graphics) {
        if (this.toastText.isEmpty() || Util.getMillis() > this.toastUntil) {
            return;
        }

        int w = 162;
        int h = 44;
        int x = this.width - w - 12;
        int y = this.height - h - 12;
        graphics.fill(x, y, x + w, y + h, 0xEE111827);
        graphics.fill(x, y, x + w, y + 1, 0xFF7548FF);
        graphics.fill(x + 12, y + 12, x + 32, y + 32, 0xFF6A3DFF);
        graphics.drawCenteredString(this.font, "OK", x + 22, y + 18, 0xFFFFFFFF);
        graphics.drawString(this.font, this.toastText, x + 42, y + 11, 0xFFFFFFFF, false);
        graphics.drawString(this.font, "Auto Sprint updated.", x + 42, y + 24, 0xFFB7BED0, false);
    }

    private void drawResizeHandles(GuiGraphics graphics, int y) {
        int right = this.panelX + this.panelWidth;
        int bottom = y + this.panelHeight;
        graphics.fill(right - 16, bottom - 4, right - 4, bottom - 2, 0xFFBFD7FF);
        graphics.fill(right - 12, bottom - 8, right - 4, bottom - 6, 0xFFBFD7FF);
        graphics.fill(right - 8, bottom - 12, right - 4, bottom - 10, 0xFFBFD7FF);
    }

    private void layoutSearchBox(int y) {
        if (this.searchBox == null) {
            return;
        }

        int searchX = this.panelX + sidebarWidth() + 16;
        this.searchBox.setX(searchX);
        this.searchBox.setY(y + 13);
        this.searchBox.setWidth(Math.max(84, Math.min(150, listWidth() - 14)));
    }

    private boolean isInClose(double mouseX, double mouseY) {
        int y = this.panelY + 13;
        int x = this.panelX + this.panelWidth - 38;
        return mouseX >= x && mouseX <= x + 20 && mouseY >= y && mouseY <= y + 18;
    }

    private boolean isInSprintToggle(double mouseX, double mouseY) {
        int detailX = detailX();
        int detailY = this.panelY + 48;
        return mouseX >= detailX + detailWidth() - 44
                && mouseX <= detailX + detailWidth() - 6
                && mouseY >= detailY + 14
                && mouseY <= detailY + 40;
    }

    private boolean isInSprintModule(double mouseX, double mouseY) {
        int x = listX() + 8;
        int y = this.panelY + 76;
        return mouseX >= x && mouseX <= x + listWidth() - 16 && mouseY >= y && mouseY <= y + 34;
    }

    private int categoryAt(double mouseX, double mouseY) {
        int x = this.panelX + 8;
        int y = this.panelY + 50;
        int w = sidebarWidth() - 16;

        for (int i = 0; i < CATEGORIES.length; i++) {
            int itemY = y + i * 25;
            if (mouseX >= x && mouseX <= x + w && mouseY >= itemY && mouseY <= itemY + 20) {
                return i;
            }
        }

        return -1;
    }

    private int moduleAt(double mouseX, double mouseY) {
        int x = listX() + 8;
        int y = this.panelY + 76;
        int w = listWidth() - 16;
        int rowH = 34;

        int drawn = 0;
        String filter = this.searchBox == null ? "" : this.searchBox.getValue().toLowerCase(Locale.ROOT);

        for (int i = 0; i < MODULES.length && drawn < maxModuleRows(); i++) {
            if (!filter.isEmpty() && !MODULES[i].title().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }

            int rowY = y + drawn * (rowH + 6);
            if (mouseX >= x && mouseX <= x + w && mouseY >= rowY && mouseY <= rowY + rowH) {
                return i;
            }
            drawn++;
        }

        return -1;
    }

    private void showToast(String text) {
        this.toastText = text;
        this.toastUntil = Util.getMillis() + 2200L;
    }

    private String playerName() {
        String selected = AltManager.getSelectedNick();
        if (!selected.isEmpty()) {
            return selected;
        }

        return this.minecraft != null && this.minecraft.getUser() != null
                ? this.minecraft.getUser().getName()
                : "Snowi";
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

    private int sidebarWidth() {
        return Math.max(96, Math.min(128, this.panelWidth / 4));
    }

    private int listX() {
        return this.panelX + sidebarWidth();
    }

    private int listWidth() {
        return Math.max(112, Math.min(176, this.panelWidth / 3));
    }

    private int detailX() {
        return listX() + listWidth() + 10;
    }

    private int detailWidth() {
        return Math.max(160, this.panelX + this.panelWidth - detailX() - 12);
    }

    private int maxModuleRows() {
        return Math.max(3, (this.panelHeight - 88) / 40);
    }

    private record ModuleRow(String title, String description, boolean sprint) {
    }
}
