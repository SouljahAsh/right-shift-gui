package com.example.almatyclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class AltManagerScreen extends Screen {
    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 220;
    private static final int PAGE_SIZE = 5;

    private final Screen parent;
    private final List<Button> nickButtons = new ArrayList<>();
    private Tab tab = Tab.ADD;
    private int page;

    private EditBox nickBox;
    private Button addTabButton;
    private Button savedTabButton;
    private Button saveButton;
    private Button randomButton;
    private Button prevButton;
    private Button nextButton;
    private Button backButton;

    public AltManagerScreen(Screen parent) {
        super(Component.translatable("screen.almatyclient.alt_manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelX = panelX();
        int panelY = panelY();

        this.addTabButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.alt_manager.tab.add"),
                button -> setTab(Tab.ADD)
        ).bounds(panelX + 18, panelY + 36, 146, 20).build());

        this.savedTabButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.alt_manager.tab.saved"),
                button -> setTab(Tab.SAVED)
        ).bounds(panelX + 176, panelY + 36, 146, 20).build());

        this.nickBox = this.addRenderableWidget(new EditBox(
                this.font,
                panelX + 36,
                panelY + 82,
                268,
                20,
                Component.translatable("screen.almatyclient.alt_manager.nick")
        ));
        this.nickBox.setMaxLength(16);
        this.nickBox.setHint(Component.translatable("screen.almatyclient.alt_manager.nick_hint"));
        this.nickBox.setResponder(value -> updateSaveButton());

        this.saveButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.alt_manager.save"),
                button -> saveNick()
        ).bounds(panelX + 36, panelY + 112, 128, 20).build());

        this.randomButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.alt_manager.random"),
                button -> {
                    this.nickBox.setValue(AltManager.randomNick());
                    saveNick();
                }
        ).bounds(panelX + 176, panelY + 112, 128, 20).build());

        for (int i = 0; i < PAGE_SIZE; i++) {
            Button button = this.addRenderableWidget(Button.builder(
                    Component.empty(),
                    pressed -> selectNick(pressed.getMessage().getString())
            ).bounds(panelX + 36, panelY + 72 + i * 24, 268, 20).build());
            this.nickButtons.add(button);
        }

        this.prevButton = this.addRenderableWidget(Button.builder(
                Component.literal("<"),
                button -> {
                    if (this.page > 0) {
                        this.page--;
                        updateTabWidgets();
                    }
                }
        ).bounds(panelX + 36, panelY + 196, 52, 20).build());

        this.nextButton = this.addRenderableWidget(Button.builder(
                Component.literal(">"),
                button -> {
                    int maxPage = maxPage();
                    if (this.page < maxPage) {
                        this.page++;
                        updateTabWidgets();
                    }
                }
        ).bounds(panelX + 252, panelY + 196, 52, 20).build());

        this.backButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.alt_manager.back"),
                button -> this.onClose()
        ).bounds(panelX + 106, panelY + 196, 128, 20).build());

        updateTabWidgets();
        updateSaveButton();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);

        int panelX = panelX();
        int panelY = panelY();

        layoutWidgets(panelX, panelY);

        graphics.fill(panelX + 5, panelY + 6, panelX + PANEL_WIDTH + 5, panelY + PANEL_HEIGHT + 6, 0x66000000);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xEA101820);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + 2, 0xFF4BA3FF);
        graphics.fill(panelX, panelY + 2, panelX + PANEL_WIDTH, panelY + 4, 0xFF2DD4BF);
        graphics.fill(panelX, panelY + PANEL_HEIGHT - 2, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF4BA3FF);
        graphics.fill(panelX, panelY, panelX + 2, panelY + PANEL_HEIGHT, 0xFF4BA3FF);
        graphics.fill(panelX + PANEL_WIDTH - 2, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 0xFF2DD4BF);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, panelY + 16, 0xFFFFFFFF);
        drawSelectedNick(graphics, panelY);

        if (this.tab == Tab.SAVED && AltManager.getNicks().isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.translatable("screen.almatyclient.alt_manager.empty"),
                    this.width / 2,
                    panelY + 104,
                    0xFFBFD7FF
            );
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER && this.tab == Tab.ADD) {
            saveNick();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void setTab(Tab tab) {
        this.tab = tab;
        this.page = Math.min(this.page, maxPage());
        updateTabWidgets();
    }

    private void saveNick() {
        if (AltManager.saveNick(this.nickBox.getValue())) {
            this.nickBox.setValue(AltManager.getSelectedNick());
            this.tab = Tab.SAVED;
            this.page = maxPage();
            updateTabWidgets();
        }
    }

    private void selectNick(String nick) {
        AltManager.selectNick(nick);
        this.nickBox.setValue(AltManager.getSelectedNick());
        updateTabWidgets();
    }

    private void updateSaveButton() {
        if (this.saveButton != null) {
            this.saveButton.active = !AltManager.sanitizeNick(this.nickBox.getValue()).isEmpty();
        }
    }

    private void updateTabWidgets() {
        boolean addVisible = this.tab == Tab.ADD;
        boolean savedVisible = this.tab == Tab.SAVED;

        setVisible(this.nickBox, addVisible);
        setVisible(this.saveButton, addVisible);
        setVisible(this.randomButton, addVisible);

        List<String> nicks = AltManager.getNicks();
        int start = this.page * PAGE_SIZE;

        for (int i = 0; i < this.nickButtons.size(); i++) {
            Button button = this.nickButtons.get(i);
            int nickIndex = start + i;
            boolean visible = savedVisible && nickIndex < nicks.size();

            setVisible(button, visible);

            if (visible) {
                button.setMessage(Component.literal(nicks.get(nickIndex)));
            }
        }

        setVisible(this.prevButton, savedVisible);
        setVisible(this.nextButton, savedVisible);

        if (this.addTabButton != null) {
            this.addTabButton.active = this.tab != Tab.ADD;
        }

        if (this.savedTabButton != null) {
            this.savedTabButton.active = this.tab != Tab.SAVED;
        }

        if (this.prevButton != null) {
            this.prevButton.active = this.page > 0;
        }

        if (this.nextButton != null) {
            this.nextButton.active = this.page < maxPage();
        }

        updateSaveButton();
    }

    private void layoutWidgets(int panelX, int panelY) {
        this.addTabButton.setX(panelX + 18);
        this.addTabButton.setY(panelY + 36);
        this.savedTabButton.setX(panelX + 176);
        this.savedTabButton.setY(panelY + 36);

        this.nickBox.setX(panelX + 36);
        this.nickBox.setY(panelY + 82);
        this.saveButton.setX(panelX + 36);
        this.saveButton.setY(panelY + 112);
        this.randomButton.setX(panelX + 176);
        this.randomButton.setY(panelY + 112);

        for (int i = 0; i < this.nickButtons.size(); i++) {
            Button button = this.nickButtons.get(i);
            button.setX(panelX + 36);
            button.setY(panelY + 72 + i * 24);
        }

        this.prevButton.setX(panelX + 36);
        this.prevButton.setY(panelY + 196);
        this.backButton.setX(panelX + 106);
        this.backButton.setY(panelY + 196);
        this.nextButton.setX(panelX + 252);
        this.nextButton.setY(panelY + 196);
    }

    private void drawSelectedNick(GuiGraphics graphics, int panelY) {
        String nick = AltManager.getSelectedNick();
        Component selected = nick.isEmpty()
                ? Component.translatable("screen.almatyclient.alt_manager.selected.none")
                : Component.translatable("screen.almatyclient.alt_manager.selected", nick);

        graphics.drawCenteredString(this.font, selected, this.width / 2, panelY + 60, 0xFF52FFA8);
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    private int maxPage() {
        int count = AltManager.getNicks().size();
        return Math.max(0, (count - 1) / PAGE_SIZE);
    }

    private static void setVisible(AbstractWidget widget, boolean visible) {
        if (widget != null) {
            widget.visible = visible;
            widget.active = visible;
        }
    }

    private enum Tab {
        ADD,
        SAVED
    }
}
