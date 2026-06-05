package com.example.almatyclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public final class AlmatyClient implements ClientModInitializer {
    public static final String MOD_ID = "almatyclient";

    private static boolean autoSprintEnabled;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "controls")
    );

    private static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.almatyclient.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (!(client.screen instanceof AlmatyClientScreen)) {
                    client.setScreen(new AlmatyClientScreen(client.screen));
                }
            }

            tickAutoSprint(client);
        });
    }

    public static boolean isAutoSprintEnabled() {
        return autoSprintEnabled;
    }

    public static void toggleAutoSprint() {
        autoSprintEnabled = !autoSprintEnabled;
    }

    private static void tickAutoSprint(Minecraft client) {
        if (!autoSprintEnabled || client.player == null || client.options == null) {
            return;
        }

        boolean walkingForward = client.options.keyUp.isDown();
        boolean canSprint = walkingForward
                && !client.player.isSprinting()
                && !client.player.isCrouching()
                && !client.player.isUsingItem()
                && !client.player.isPassenger();

        if (canSprint) {
            client.player.setSprinting(true);
        }
    }
}
