package com.example.almatyclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

public final class AlmatyClientScreen extends Screen {
    private static final int MIN_PANEL_WIDTH = 390;
    private static final int MIN_PANEL_HEIGHT = 250;
    private static final int RESIZE_HANDLE = 7;
    private static final int RESIZE_LEFT = 1;
    private static final int RESIZE_RIGHT = 2;
    private static final int RESIZE_TOP = 4;
    private static final int RESIZE_BOTTOM = 8;

    private final Screen parent;
    private final long openedAt;

    private Tab activeTab = Tab.MOVEMENT;
    private boolean layoutInitialized;
    private boolean dragging;
    private boolean closing;
    private boolean targetDropdownOpen = true;
    private boolean displayDropdownOpen = true;
    private boolean particlesSettingsOpen;
    private boolean espSettingsOpen;
    private int activeSlider = -1;
    private int resizeMode;
    private int panelX;
    private int panelY;
    private int panelWidth = 430;
    private int panelHeight = 270;
    private int resizeStartPanelX;
    private int resizeStartPanelY;
    private int resizeStartPanelWidth;
    private int resizeStartPanelHeight;
    private long closingAt;
    private double lastMouseX;
    private double lastMouseY;
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
            this.panelX = AlmatyConfig.getInt("gui.x", this.width / 2 - this.panelWidth / 2);
            this.panelY = AlmatyConfig.getInt("gui.y", this.height / 2 - this.panelHeight / 2);
            this.activeTab = readSavedTab();
            this.layoutInitialized = true;
        }
        clampPanelToScreen();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0xA5000000);

        float progress = openProgress();
        int animatedY = this.panelY + Math.round((1.0F - progress) * 10.0F);
        int animatedHeight = Math.max(1, Math.round(this.panelHeight * progress));

        drawShell(graphics, animatedY, animatedHeight);
        drawHeader(graphics, animatedY);
        drawTabs(graphics, animatedY);
        drawContent(graphics, animatedY);
        drawResizeHandles(graphics, animatedY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        this.resizeMode = findResizeMode(mouseX, mouseY);
        if (this.resizeMode != 0) {
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            this.resizeStartMouseX = mouseX;
            this.resizeStartMouseY = mouseY;
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
            this.particlesSettingsOpen = false;
            this.espSettingsOpen = false;
            saveSelectedTab();
            return true;
        }

        int slider = sliderAt(mouseX, mouseY);
        if (slider >= 0) {
            this.activeSlider = slider;
            updateColorSlider(slider, mouseX);
            return true;
        }

        if (this.activeTab == Tab.MOVEMENT && inRow(mouseX, mouseY, 0)) {
            AlmatyClient.toggleAutoSprint();
            return true;
        }

        if (this.activeTab == Tab.OTHER && inRow(mouseX, mouseY, 3)) {
            resetPanel();
            return true;
        }

        if (this.activeTab == Tab.VISUALS && inRow(mouseX, mouseY, 0)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.particlesSettingsOpen = !this.particlesSettingsOpen;
            } else {
                AlmatyClient.setParticlesEnabled(!AlmatyClient.isParticlesEnabled());
            }
            return true;
        }

        if (this.activeTab == Tab.PLAYERS && inRow(mouseX, mouseY, 0)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.espSettingsOpen = !this.espSettingsOpen;
            } else {
                AlmatyClient.setEspEnabled(!AlmatyClient.isEspEnabled());
            }
            return true;
        }

        if (this.particlesSettingsOpen && clickParticlesSettings(mouseX, mouseY)) {
            return true;
        }

        if (this.espSettingsOpen && clickEspSettings(mouseX, mouseY)) {
            return true;
        }

        if (isInHeader(mouseX, mouseY)) {
            this.dragging = true;
            this.dragOffsetX = mouseX - this.panelX;
            this.dragOffsetY = mouseY - this.panelY;
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.activeSlider >= 0) {
            updateColorSlider(this.activeSlider, event.x());
            return true;
        }

        if (this.resizeMode != 0) {
            resizePanel(event.x(), event.y());
            this.lastMouseX = event.x();
            this.lastMouseY = event.y();
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
        int accent = AlmatyClient.accentColor(255);
        int background = colorWithAlpha(226, Math.max(6, AlmatyClient.guiRed() / 8), Math.max(9, AlmatyClient.guiGreen() / 8), Math.max(14, AlmatyClient.guiBlue() / 8));

        graphics.fill(this.panelX + 5, y + 6, this.panelX + this.panelWidth + 5, y + animatedHeight + 6, 0x66000000);
        graphics.fill(this.panelX, y, this.panelX + this.panelWidth, y + animatedHeight, background);
        graphics.fill(this.panelX, y, this.panelX + this.panelWidth, y + 2, accent);
        graphics.fill(this.panelX, y + animatedHeight - 2, this.panelX + this.panelWidth, y + animatedHeight, accent);
        graphics.fill(this.panelX, y, this.panelX + 2, y + animatedHeight, accent);
        graphics.fill(this.panelX + this.panelWidth - 2, y, this.panelX + this.panelWidth, y + animatedHeight, accent);
    }

    private void drawHeader(GuiGraphics graphics, int y) {
        int accent = AlmatyClient.accentColor(255);
        graphics.fill(this.panelX + 2, y + 2, this.panelX + this.panelWidth - 2, y + 34, 0x66000000);
        drawStringFit(graphics, "AlmatyClient", this.panelX + 14, y + 13, Math.max(40, this.panelWidth - 56), 0xFFFFFFFF);
        graphics.fill(this.panelX + 92, y + 15, this.panelX + 132, y + 17, accent);
        graphics.fill(this.panelX + this.panelWidth - 28, y + 8, this.panelX + this.panelWidth - 10, y + 26, 0x66111824);
        graphics.drawCenteredString(this.font, "X", this.panelX + this.panelWidth - 19, y + 13, 0xFFFFFFFF);
    }

    private void drawTabs(GuiGraphics graphics, int y) {
        int sidebar = sidebarWidth();
        graphics.fill(this.panelX + 10, y + 44, this.panelX + sidebar - 10, y + this.panelHeight - 14, 0x55111824);

        for (Tab tab : Tab.values()) {
            int itemY = tabY(tab);
            boolean active = tab == this.activeTab;
            if (tab == Tab.OTHER) {
                graphics.fill(this.panelX + 20, itemY - 12, this.panelX + sidebar - 20, itemY - 11, 0x66465466);
            }
            if (active) {
                graphics.fill(this.panelX + 16, itemY, this.panelX + sidebar - 16, itemY + 23, AlmatyClient.accentColor(180));
                graphics.fill(this.panelX + 16, itemY + 21, this.panelX + sidebar - 16, itemY + 23, AlmatyClient.accentColor(255));
            }
            drawStringFit(graphics, tab.title, this.panelX + 26, itemY + 8, sidebar - 48, active ? 0xFFFFFFFF : 0xFFB9C4D8);
        }
    }

    private void drawContent(GuiGraphics graphics, int y) {
        int x = contentX();
        int w = contentWidth();
        graphics.fill(x, y + 44, x + w, y + this.panelHeight - 14, 0x55111824);
        graphics.enableScissor(x, y + 44, x + w, y + this.panelHeight - 14);

        drawStringFit(graphics, this.activeTab.title, x + 14, y + 56, w - 28, 0xFFFFFFFF);

        if (this.activeTab == Tab.MOVEMENT) {
            drawFeatureRow(graphics, x + 12, y + 82, w - 24, "AutoSprint", AlmatyClient.isAutoSprintEnabled());
        } else if (this.activeTab == Tab.OTHER) {
            drawColorSlider(graphics, x + 12, y + 82, w - 24, "Red", AlmatyClient.guiRed(), 0);
            drawColorSlider(graphics, x + 12, y + 120, w - 24, "Green", AlmatyClient.guiGreen(), 1);
            drawColorSlider(graphics, x + 12, y + 158, w - 24, "Blue", AlmatyClient.guiBlue(), 2);
            drawActionRow(graphics, x + 12, y + 198, w - 24, "Reset GUI", "Restore window size");
        } else if (this.activeTab == Tab.VISUALS) {
            drawFeatureRow(graphics, x + 12, y + 82, w - 24, "Particles", AlmatyClient.isParticlesEnabled());
            if (this.particlesSettingsOpen) {
                drawParticlesSettings(graphics, x + 22, y + 124, w - 44);
            }
        } else if (this.activeTab == Tab.PLAYERS) {
            drawFeatureRow(graphics, x + 12, y + 82, w - 24, "ESP", AlmatyClient.isEspEnabled());
            if (this.espSettingsOpen) {
                drawEspSettings(graphics, x + 22, y + 124, w - 44);
            }
        }
        graphics.disableScissor();
    }

    private void drawFeatureRow(GuiGraphics graphics, int x, int y, int w, String title, boolean enabled) {
        graphics.fill(x, y, x + w, y + 34, enabled ? AlmatyClient.accentColor(95) : 0x66111824);
        graphics.fill(x, y, x + 2, y + 34, enabled ? AlmatyClient.accentColor(255) : 0x66465466);
        drawStringFit(graphics, title, x + 12, y + 7, Math.max(24, w - 66), 0xFFFFFFFF);
        drawStringFit(graphics, "RMB settings", x + 12, y + 19, Math.max(24, w - 66), 0xFF98A8C4);
        drawToggle(graphics, x + w - 42, y + 9, enabled);
    }

    private void drawActionRow(GuiGraphics graphics, int x, int y, int w, String title, String hint) {
        graphics.fill(x, y, x + w, y + 34, 0x66111824);
        graphics.fill(x, y, x + 2, y + 34, AlmatyClient.accentColor(210));
        drawStringFit(graphics, title, x + 12, y + 7, Math.max(24, w - 24), 0xFFFFFFFF);
        drawStringFit(graphics, hint, x + 12, y + 19, Math.max(24, w - 24), 0xFF98A8C4);
    }

    private void drawColorSlider(GuiGraphics graphics, int x, int y, int w, String label, int value, int index) {
        graphics.fill(x, y, x + w, y + 30, 0x66111824);
        drawStringFit(graphics, label, x + 10, y + 6, Math.max(24, w / 4), 0xFFFFFFFF);
        drawStringFit(graphics, Integer.toString(value), x + w - 30, y + 6, 28, 0xFFBFD7FF);

        int start = sliderStartX();
        int end = sliderEndX();
        int trackY = sliderY(index);
        int knob = start + Math.round((end - start) * (value / 255.0F));
        graphics.fill(start, trackY, end, trackY + 3, 0xFF263447);
        graphics.fill(start, trackY, knob, trackY + 3, AlmatyClient.accentColor(255));
        graphics.fill(knob - 3, trackY - 4, knob + 3, trackY + 7, 0xFFFFFFFF);
    }

    private void drawParticlesSettings(GuiGraphics graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 42, 0x99111824);
        graphics.fill(x, y, x + w, y + 1, AlmatyClient.accentColor(255));
        drawStringFit(graphics, "Water Bubbles", x + 10, y + 9, Math.max(24, w - 62), 0xFFFFFFFF);
        drawStringFit(graphics, "Hit player or mob", x + 10, y + 23, Math.max(24, w - 62), 0xFF9CB0CE);
        drawToggle(graphics, x + w - 42, y + 12, AlmatyClient.isParticlesEnabled());
    }

    private void drawEspSettings(GuiGraphics graphics, int x, int y, int w) {
        int currentY = y;
        graphics.fill(x, currentY, x + w, currentY + 22, 0x99111824);
        drawStringFit(graphics, "Выбор цели", x + 10, currentY + 7, Math.max(24, w - 34), 0xFFFFFFFF);
        drawStringFit(graphics, this.targetDropdownOpen ? "-" : "+", x + w - 16, currentY + 7, 12, 0xFFFFFFFF);
        currentY += 24;

        if (this.targetDropdownOpen) {
            drawCheck(graphics, x, currentY, w, "Игроки", AlmatyClient.espPlayers());
            drawCheck(graphics, x, currentY + 22, w, "Мобы", AlmatyClient.espMobs());
            drawCheck(graphics, x, currentY + 44, w, "Предметы", AlmatyClient.espItems());
            currentY += 70;
        }

        graphics.fill(x, currentY, x + w, currentY + 22, 0x99111824);
        drawStringFit(graphics, "Отображение", x + 10, currentY + 7, Math.max(24, w - 34), 0xFFFFFFFF);
        drawStringFit(graphics, this.displayDropdownOpen ? "-" : "+", x + w - 16, currentY + 7, 12, 0xFFFFFFFF);
        currentY += 24;

        if (this.displayDropdownOpen) {
            drawCheck(graphics, x, currentY, w, "Имя", AlmatyClient.espName());
            drawCheck(graphics, x, currentY + 22, w, "Здоровье", AlmatyClient.espHealth());
        }
    }

    private void drawCheck(GuiGraphics graphics, int x, int y, int w, String label, boolean enabled) {
        graphics.fill(x + 8, y, x + w - 8, y + 20, 0x55111824);
        graphics.fill(x + 16, y + 5, x + 26, y + 15, enabled ? AlmatyClient.accentColor(255) : 0xFF263447);
        drawStringFit(graphics, label, x + 36, y + 6, Math.max(16, w - 52), 0xFFE6ECF8);
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, boolean enabled) {
        graphics.fill(x, y, x + 30, y + 16, enabled ? AlmatyClient.accentColor(255) : 0xFF273142);
        graphics.fill(x + (enabled ? 17 : 3), y + 3, x + (enabled ? 27 : 13), y + 13, 0xFFFFFFFF);
    }

    private void drawResizeHandles(GuiGraphics graphics, int y) {
        int right = this.panelX + this.panelWidth;
        int bottom = y + this.panelHeight;
        graphics.fill(right - 16, bottom - 4, right - 4, bottom - 2, 0xFFFFFFFF);
        graphics.fill(right - 12, bottom - 8, right - 4, bottom - 6, 0xFFFFFFFF);
        graphics.fill(right - 8, bottom - 12, right - 4, bottom - 10, 0xFFFFFFFF);
    }

    private boolean clickParticlesSettings(double mouseX, double mouseY) {
        int x = contentX() + 22;
        int y = this.panelY + 164;
        int w = contentWidth() - 44;
        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 42) {
            AlmatyClient.setParticlesEnabled(!AlmatyClient.isParticlesEnabled());
            return true;
        }
        return false;
    }

    private boolean clickEspSettings(double mouseX, double mouseY) {
        int x = contentX() + 22;
        int y = this.panelY + 124;
        int w = contentWidth() - 44;
        int currentY = y;

        if (inside(mouseX, mouseY, x, currentY, w, 22)) {
            this.targetDropdownOpen = !this.targetDropdownOpen;
            return true;
        }
        currentY += 24;

        if (this.targetDropdownOpen) {
            if (inside(mouseX, mouseY, x + 8, currentY, w - 16, 20)) {
                AlmatyClient.setEspPlayers(!AlmatyClient.espPlayers());
                return true;
            }
            if (inside(mouseX, mouseY, x + 8, currentY + 22, w - 16, 20)) {
                AlmatyClient.setEspMobs(!AlmatyClient.espMobs());
                return true;
            }
            if (inside(mouseX, mouseY, x + 8, currentY + 44, w - 16, 20)) {
                AlmatyClient.setEspItems(!AlmatyClient.espItems());
                return true;
            }
            currentY += 70;
        }

        if (inside(mouseX, mouseY, x, currentY, w, 22)) {
            this.displayDropdownOpen = !this.displayDropdownOpen;
            return true;
        }
        currentY += 24;

        if (this.displayDropdownOpen) {
            if (inside(mouseX, mouseY, x + 8, currentY, w - 16, 20)) {
                AlmatyClient.setEspName(!AlmatyClient.espName());
                return true;
            }
            if (inside(mouseX, mouseY, x + 8, currentY + 22, w - 16, 20)) {
                AlmatyClient.setEspHealth(!AlmatyClient.espHealth());
                return true;
            }
        }

        return false;
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
        int x = this.panelX + 16;
        int w = sidebar - 32;
        for (Tab tab : Tab.values()) {
            int y = tabY(tab);
            if (inside(mouseX, mouseY, x, y, w, 23)) {
                return tab;
            }
        }
        return null;
    }

    private boolean inRow(double mouseX, double mouseY, int index) {
        int x = contentX() + 12;
        int y = this.panelY + 82 + index * 40;
        int w = contentWidth() - 24;
        return inside(mouseX, mouseY, x, y, w, 34);
    }

    private int sliderAt(double mouseX, double mouseY) {
        if (this.activeTab != Tab.OTHER) {
            return -1;
        }

        int start = sliderStartX();
        int end = sliderEndX();
        for (int i = 0; i < 3; i++) {
            int y = sliderY(i);
            if (mouseX >= start - 4 && mouseX <= end + 4 && mouseY >= y - 7 && mouseY <= y + 10) {
                return i;
            }
        }
        return -1;
    }

    private void updateColorSlider(int slider, double mouseX) {
        int start = sliderStartX();
        int end = sliderEndX();
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

    private int findResizeMode(double mouseX, double mouseY) {
        boolean inVerticalRange = mouseY >= this.panelY - RESIZE_HANDLE
                && mouseY <= this.panelY + this.panelHeight + RESIZE_HANDLE;
        boolean inHorizontalRange = mouseX >= this.panelX - RESIZE_HANDLE
                && mouseX <= this.panelX + this.panelWidth + RESIZE_HANDLE;
        boolean left = inVerticalRange && mouseX >= this.panelX - RESIZE_HANDLE && mouseX <= this.panelX + RESIZE_HANDLE;
        boolean right = inVerticalRange && mouseX >= this.panelX + this.panelWidth - RESIZE_HANDLE
                && mouseX <= this.panelX + this.panelWidth + RESIZE_HANDLE;
        boolean top = inHorizontalRange && mouseY >= this.panelY - RESIZE_HANDLE && mouseY <= this.panelY + RESIZE_HANDLE;
        boolean bottom = inHorizontalRange && mouseY >= this.panelY + this.panelHeight - RESIZE_HANDLE
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

    private void resizePanel(double mouseX, double mouseY) {
        int dx = (int) Math.round(mouseX - this.resizeStartMouseX);
        int dy = (int) Math.round(mouseY - this.resizeStartMouseY);
        int x = this.resizeStartPanelX;
        int y = this.resizeStartPanelY;
        int width = this.resizeStartPanelWidth;
        int height = this.resizeStartPanelHeight;

        if ((this.resizeMode & RESIZE_LEFT) != 0) {
            int fixedRight = this.resizeStartPanelX + this.resizeStartPanelWidth;
            x = Math.max(6, Math.min(this.resizeStartPanelX + dx, fixedRight - MIN_PANEL_WIDTH));
            width = fixedRight - x;
        } else if ((this.resizeMode & RESIZE_RIGHT) != 0) {
            int right = Math.min(this.width - 6, Math.max(this.resizeStartPanelX + MIN_PANEL_WIDTH, this.resizeStartPanelX + this.resizeStartPanelWidth + dx));
            width = right - this.resizeStartPanelX;
        }

        if ((this.resizeMode & RESIZE_TOP) != 0) {
            int fixedBottom = this.resizeStartPanelY + this.resizeStartPanelHeight;
            y = Math.max(6, Math.min(this.resizeStartPanelY + dy, fixedBottom - MIN_PANEL_HEIGHT));
            height = fixedBottom - y;
        } else if ((this.resizeMode & RESIZE_BOTTOM) != 0) {
            int bottom = Math.min(this.height - 6, Math.max(this.resizeStartPanelY + MIN_PANEL_HEIGHT, this.resizeStartPanelY + this.resizeStartPanelHeight + dy));
            height = bottom - this.resizeStartPanelY;
        }

        this.panelX = x;
        this.panelY = y;
        this.panelWidth = width;
        this.panelHeight = height;
        clampPanelToScreen();
    }

    private void clampPanelToScreen() {
        this.panelWidth = Math.min(Math.max(this.panelWidth, MIN_PANEL_WIDTH), Math.max(MIN_PANEL_WIDTH, this.width - 12));
        this.panelHeight = Math.min(Math.max(this.panelHeight, MIN_PANEL_HEIGHT), Math.max(MIN_PANEL_HEIGHT, this.height - 12));
        this.panelX = Math.max(6, Math.min(this.panelX, this.width - this.panelWidth - 6));
        this.panelY = Math.max(6, Math.min(this.panelY, this.height - this.panelHeight - 6));
    }

    private void savePanel() {
        AlmatyConfig.setInt("gui.x", this.panelX);
        AlmatyConfig.setInt("gui.y", this.panelY);
        AlmatyConfig.setInt("gui.width", this.panelWidth);
        AlmatyConfig.setInt("gui.height", this.panelHeight);
        saveSelectedTab();
    }

    private void resetPanel() {
        this.panelWidth = 430;
        this.panelHeight = 270;
        this.panelX = this.width / 2 - this.panelWidth / 2;
        this.panelY = this.height / 2 - this.panelHeight / 2;
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
        return inside(mouseX, mouseY, this.panelX + 2, this.panelY + 2, this.panelWidth - 4, 32);
    }

    private boolean isInClose(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, this.panelX + this.panelWidth - 28, this.panelY + 8, 18, 18);
    }

    private int sidebarWidth() {
        return Math.max(116, Math.min(150, this.panelWidth / 3));
    }

    private int contentX() {
        return this.panelX + sidebarWidth() + 4;
    }

    private int contentWidth() {
        return this.panelX + this.panelWidth - contentX() - 10;
    }

    private int sliderStartX() {
        return contentX() + 92;
    }

    private int sliderEndX() {
        return contentX() + contentWidth() - 50;
    }

    private int sliderY(int index) {
        return this.panelY + 102 + index * 38;
    }

    private int tabY(Tab tab) {
        if (tab == Tab.OTHER) {
            return this.panelY + this.panelHeight - 47;
        }

        return this.panelY + 48 + tab.ordinal() * 31;
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

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static int colorWithAlpha(int alpha, int red, int green, int blue) {
        return ((alpha & 255) << 24) | ((red & 255) << 16) | ((green & 255) << 8) | (blue & 255);
    }

    private enum Tab {
        MOVEMENT("Movement"),
        VISUALS("Visuals"),
        PLAYERS("Players"),
        OTHER("Other");

        private final String title;

        Tab(String title) {
            this.title = title;
        }
    }
}
