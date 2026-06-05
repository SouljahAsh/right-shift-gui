package com.example.almatyclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class AlmatyClient implements ClientModInitializer {
    public static final String MOD_ID = "almatyclient";

    private static boolean autoSprintEnabled;
    private static boolean sprintKeyForced;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls")
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

        if (!autoSprintEnabled) {
            releaseForcedSprint(Minecraft.getInstance());
        }
    }

    private static void tickAutoSprint(Minecraft client) {
        if (!autoSprintEnabled || client.player == null || client.options == null) {
            releaseForcedSprint(client);
            return;
        }

        boolean walkingForward = client.options.keyUp.isDown();
        boolean sprintBlocked = client.player.isCrouching()
                || client.player.isUsingItem()
                || client.player.isPassenger();

        if (walkingForward && !sprintBlocked) {
            client.options.keySprint.setDown(true);
            sprintKeyForced = true;

            if (!client.player.isSprinting()) {
                client.player.setSprinting(true);
            }

            smoothSprintMovement(client);
        } else {
            releaseForcedSprint(client);
        }
    }

    private static void releaseForcedSprint(Minecraft client) {
        if (sprintKeyForced && client.options != null) {
            client.options.keySprint.setDown(false);
            sprintKeyForced = false;
        }
    }

    private static void smoothSprintMovement(Minecraft client) {
        if (client.player == null || !client.player.onGround()) {
            return;
        }

        Vec3 velocity = client.player.getDeltaMovement();
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        if (horizontalSpeed > 0.04D && horizontalSpeed < 0.31D) {
            double boost = 1.0D + (0.31D - horizontalSpeed) * 0.035D;
            client.player.setDeltaMovement(velocity.x * boost, velocity.y, velocity.z * boost);
        }
    }
}
