package com.example.almatyclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public final class BindManager {
    private static final String KEY_PREFIX = "bind.";
    private static final int NO_BIND = -1;
    private static final Set<Integer> PRESSED_KEYS = new HashSet<>();
    private static boolean initialized;

    private BindManager() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(BindManager::tick);
    }

    public static int key(ClientModule module) {
        return AlmatyConfig.getInt(configKey(module), NO_BIND);
    }

    public static void bind(ClientModule module, int key) {
        if (key <= 0 || key == GLFW.GLFW_KEY_ESCAPE) {
            unbind(module);
            return;
        }
        AlmatyConfig.setInt(configKey(module), key);
        PRESSED_KEYS.add(key);
    }

    public static void unbind(ClientModule module) {
        int key = key(module);
        AlmatyConfig.setInt(configKey(module), NO_BIND);
        PRESSED_KEYS.remove(key);
    }

    public static String keyName(ClientModule module) {
        int key = key(module);
        if (key < 0) {
            return "None";
        }

        String glfwName = GLFW.glfwGetKeyName(key, -1);
        if (glfwName != null && !glfwName.isBlank()) {
            return glfwName.toUpperCase();
        }

        InputConstants.Key inputKey = InputConstants.Type.KEYSYM.getOrCreate(key);
        return inputKey.getDisplayName().getString();
    }

    private static void tick(Minecraft client) {
        if (client == null || client.player == null || client.getWindow() == null || client.screen != null) {
            PRESSED_KEYS.clear();
            return;
        }

        for (ClientModule module : ClientModule.values()) {
            int key = key(module);
            if (key < 0) {
                continue;
            }

            boolean down = InputConstants.isKeyDown(client.getWindow(), key);
            if (down && PRESSED_KEYS.add(key)) {
                module.toggle();
            } else if (!down) {
                PRESSED_KEYS.remove(key);
            }
        }
    }

    private static String configKey(ClientModule module) {
        return KEY_PREFIX + module.id();
    }
}
