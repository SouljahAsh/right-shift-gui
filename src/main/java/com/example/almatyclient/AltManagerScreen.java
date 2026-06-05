package com.example.almatyclient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class AltManagerScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 246;
    private static final int PAGE_SIZE = 5;

    private final Screen parent;
    private final List<Button> selectButtons = new ArrayList<>();
    private final List<Button> deleteButtons = new ArrayList<>();
    private int page;

    private EditBox nickBox;
    private Button applyButton;
    private Button randomButton;
    private Button saveButton;
    private Button prevButton;
    private Button nextButton;
    private Button backButton;

    public AltManagerScreen(Screen parent) {
        super(Component.translatable("screen.almatyclient.alt_manager.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = panelY();

        this.nickBox = this.addRenderableWidget(new EditBox(
                this.font,
                x + 26,
                y + 52,
                308,
                20,
                Component.translatable("screen.almatyclient.alt_manager.nick")
        ));
        this.nickBox.setMaxLength(16);
        this.nickBox.setHint(Component.translatable("screen.almatyclient.alt_manager.nick_hint"));
        this.nickBox.setValue(AltManager.getSelectedNick());
        this.nickBox.setResponder(value -> updateApplyButton());

        this.applyButton = this.addRenderableWidget(Button.builder(
                Component.literal("Apply"),
                button -> applyNick()
        ).bounds(x + 26, y + 80, 96, 20).build());

        this.randomButton = this.addRenderableWidget(Button.builder(
                Component.literal("Random"),
                button -> {
                    this.nickBox.setValue(AltManager.randomNick());
                    applyNick();
                }
        ).bounds(x + 132, y + 80, 96, 20).build());

        this.saveButton = this.addRenderableWidget(Button.builder(
                Component.literal("Save"),
                button -> saveNick()
        ).bounds(x + 238, y + 80, 96, 20).build());

        for (int i = 0; i < PAGE_SIZE; i++) {
            Button select = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
                AltManager.selectNick(button.getMessage().getString());
                this.nickBox.setValue(AltManager.getSelectedNick());
                updateList();
            }).bounds(x + 26, y + 126 + i * 23, 250, 20).build());

            Button delete = this.addRenderableWidget(Button.builder(Component.literal("X"), button -> {
                int index = this.deleteButtons.indexOf(button);
                int nickIndex = this.page * PAGE_SIZE + index;
                List<String> nicks = AltManager.getNicks();
                if (nickIndex >= 0 && nickIndex < nicks.size()) {
                    AltManager.deleteNick(nicks.get(nickIndex));
                    this.page = Math.min(this.page, maxPage());
                    updateList();
                }
            }).bounds(x + 284, y + 126 + i * 23, 50, 20).build());

            this.selectButtons.add(select);
            this.deleteButtons.add(delete);
        }

        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (this.page > 0) {
                this.page--;
                updateList();
            }
        }).bounds(x + 26, y + 220, 50, 20).build());

        this.backButton = this.addRenderableWidget(Button.builder(
                Component.translatable("screen.almatyclient.alt_manager.back"),
                button -> this.onClose()
        ).bounds(x + 100, y + 220, 160, 20).build());

        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if (this.page < maxPage()) {
                this.page++;
                updateList();
            }
        }).bounds(x + 284, y + 220, 50, 20).build());

        updateApplyButton();
        updateList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x99000000);

        int x = panelX();
        int y = panelY();
        layoutWidgets(x, y);

        int accent = AlmatyClient.accentColor(255);
        graphics.fill(x + 5, y + 6, x + PANEL_WIDTH + 5, y + PANEL_HEIGHT + 6, 0x66000000);
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xEA0A101A);
        graphics.fill(x, y, x + PANEL_WIDTH, y + 2, accent);
        graphics.fill(x, y + PANEL_HEIGHT - 2, x + PANEL_WIDTH, y + PANEL_HEIGHT, accent);
        graphics.fill(x, y, x + 2, y + PANEL_HEIGHT, accent);
        graphics.fill(x + PANEL_WIDTH - 2, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, accent);

        graphics.drawCenteredString(this.font, "Alt Manager", this.width / 2, y + 16, 0xFFFFFFFF);
        graphics.drawString(this.font, selectedLabel(), x + 26, y + 34, 0xFFBFD7FF, false);
        graphics.drawString(this.font, "Saved accounts", x + 26, y + 111, 0xFFFFFFFF, false);

        if (AltManager.getNicks().isEmpty()) {
            graphics.drawCenteredString(this.font, "Empty", this.width / 2, y + 158, 0xFF8CA3C7);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER) {
            applyNick();
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

    private void applyNick() {
        String nick = AltManager.applyNick(this.nickBox.getValue());
        if (!nick.isEmpty()) {
            this.nickBox.setValue(nick);
            updateList();
        }
    }

    private void saveNick() {
        if (AltManager.saveNick(this.nickBox.getValue())) {
            this.page = maxPage();
            updateList();
        }
    }

    private void updateApplyButton() {
        boolean valid = this.nickBox != null && !AltManager.sanitizeNick(this.nickBox.getValue()).isEmpty();
        if (this.applyButton != null) {
            this.applyButton.active = valid;
        }
        if (this.saveButton != null) {
            this.saveButton.active = valid;
        }
    }

    private void updateList() {
        List<String> nicks = AltManager.getNicks();
        int start = this.page * PAGE_SIZE;

        for (int i = 0; i < this.selectButtons.size(); i++) {
            int index = start + i;
            boolean visible = index < nicks.size();
            Button select = this.selectButtons.get(i);
            Button delete = this.deleteButtons.get(i);

            select.visible = visible;
            select.active = visible;
            delete.visible = visible;
            delete.active = visible;

            if (visible) {
                select.setMessage(Component.literal(nicks.get(index)));
            }
        }

        if (this.prevButton != null) {
            this.prevButton.active = this.page > 0;
        }
        if (this.nextButton != null) {
            this.nextButton.active = this.page < maxPage();
        }
        updateApplyButton();
    }

    private void layoutWidgets(int x, int y) {
        this.nickBox.setX(x + 26);
        this.nickBox.setY(y + 52);
        this.applyButton.setX(x + 26);
        this.applyButton.setY(y + 80);
        this.randomButton.setX(x + 132);
        this.randomButton.setY(y + 80);
        this.saveButton.setX(x + 238);
        this.saveButton.setY(y + 80);

        for (int i = 0; i < this.selectButtons.size(); i++) {
            this.selectButtons.get(i).setX(x + 26);
            this.selectButtons.get(i).setY(y + 126 + i * 23);
            this.deleteButtons.get(i).setX(x + 284);
            this.deleteButtons.get(i).setY(y + 126 + i * 23);
        }

        this.prevButton.setX(x + 26);
        this.prevButton.setY(y + 220);
        this.backButton.setX(x + 100);
        this.backButton.setY(y + 220);
        this.nextButton.setX(x + 284);
        this.nextButton.setY(y + 220);
    }

    private Component selectedLabel() {
        String selected = AltManager.getSelectedNick();
        return selected.isEmpty()
                ? Component.literal("Current: none")
                : Component.literal("Current: " + selected);
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
}
