package com.example.almatyclient;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class BindCommand {
    private static final String PREFIX = ".bind";
    private static final Map<String, Integer> KEYS = buildKeyMap();

    private BindCommand() {
    }

    public static boolean handle(String message) {
        String trimmed = message.trim();
        if (!trimmed.equalsIgnoreCase(PREFIX) && !trimmed.toLowerCase(Locale.ROOT).startsWith(PREFIX + " ")) {
            return false;
        }

        String[] args = trimmed.split("\\s+");
        if (args.length == 1) {
            sendUsage();
            return true;
        }

        String subcommand = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(subcommand)) {
            listBinds();
            return true;
        }
        if ("add".equals(subcommand)) {
            addBind(args);
            return true;
        }
        if ("remove".equals(subcommand)) {
            removeBind(args);
            return true;
        }

        send(Component.literal("Unknown bind command: " + args[1]).withStyle(ChatFormatting.RED));
        sendUsage();
        return true;
    }

    private static void listBinds() {
        int count = 0;
        send(prefix().append(Component.literal("Configured binds:").withStyle(ChatFormatting.WHITE)));
        for (ClientModule module : ClientModule.values()) {
            if (BindManager.key(module) < 0) {
                continue;
            }

            count++;
            send(Component.literal("  " + module.id() + " -> ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(BindManager.keyName(module)).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
        }

        if (count == 0) {
            send(Component.literal("  No binds configured.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addBind(String[] args) {
        if (args.length < 4) {
            send(Component.literal("Usage: .bind add <module> <key>").withStyle(ChatFormatting.RED));
            return;
        }

        ClientModule module = module(join(args, 2, args.length - 1));
        if (module == null) {
            sendUnknownModule(args[2]);
            return;
        }

        int key = key(args[args.length - 1]);
        if (key <= 0 || key == GLFW.GLFW_KEY_ESCAPE) {
            send(Component.literal("Unknown or unsupported key: " + args[args.length - 1]).withStyle(ChatFormatting.RED));
            return;
        }

        BindManager.bind(module, key);
        send(prefix()
                .append(Component.literal(module.title()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" bound to ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(BindManager.keyName(module)).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)));
    }

    private static void removeBind(String[] args) {
        if (args.length < 3) {
            send(Component.literal("Usage: .bind remove <module>").withStyle(ChatFormatting.RED));
            return;
        }

        ClientModule module = module(join(args, 2, args.length));
        if (module == null) {
            sendUnknownModule(args[2]);
            return;
        }

        BindManager.unbind(module);
        send(prefix()
                .append(Component.literal("Removed bind from ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(module.title()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
    }

    private static void sendUsage() {
        send(prefix().append(Component.literal("Usage:").withStyle(ChatFormatting.WHITE)));
        send(Component.literal("  .bind list").withStyle(ChatFormatting.GRAY));
        send(Component.literal("  .bind add <module> <key>").withStyle(ChatFormatting.GRAY));
        send(Component.literal("  .bind remove <module>").withStyle(ChatFormatting.GRAY));
        send(Component.literal("Modules: " + moduleNames()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void sendUnknownModule(String raw) {
        send(Component.literal("Unknown module: " + raw).withStyle(ChatFormatting.RED));
        send(Component.literal("Modules: " + moduleNames()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static ClientModule module(String raw) {
        String normalized = normalize(raw);
        for (ClientModule module : ClientModule.values()) {
            if (normalize(module.id()).equals(normalized)
                    || normalize(module.title()).equals(normalized)
                    || normalize(module.name()).equals(normalized)) {
                return module;
            }
        }
        return null;
    }

    private static int key(String raw) {
        Integer key = KEYS.get(normalizeKey(raw));
        return key == null ? -1 : key;
    }

    private static String moduleNames() {
        StringBuilder names = new StringBuilder();
        for (ClientModule module : ClientModule.values()) {
            if (!names.isEmpty()) {
                names.append(", ");
            }
            names.append(module.id());
        }
        return names.toString();
    }

    private static MutableComponent prefix() {
        return Component.literal("[Almaty] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
    }

    private static void send(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(message, false);
        }
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String join(String[] args, int start, int end) {
        StringBuilder value = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (!value.isEmpty()) {
                value.append(' ');
            }
            value.append(args[i]);
        }
        return value.toString();
    }

    private static Map<String, Integer> buildKeyMap() {
        Map<String, Integer> keys = new HashMap<>();
        for (Field field : GLFW.class.getFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || field.getType() != int.class || !field.getName().startsWith("GLFW_KEY_")) {
                continue;
            }

            try {
                int key = field.getInt(null);
                String name = field.getName().substring("GLFW_KEY_".length()).toLowerCase(Locale.ROOT);
                keys.put(name, key);
                keys.put(name.replace("_", ""), key);
            } catch (IllegalAccessException ignored) {
            }
        }

        keys.put("ctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
        keys.put("control", GLFW.GLFW_KEY_LEFT_CONTROL);
        keys.put("shift", GLFW.GLFW_KEY_LEFT_SHIFT);
        keys.put("alt", GLFW.GLFW_KEY_LEFT_ALT);
        keys.put("rshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        keys.put("rightshift", GLFW.GLFW_KEY_RIGHT_SHIFT);
        keys.put("lshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        keys.put("leftshift", GLFW.GLFW_KEY_LEFT_SHIFT);
        return keys;
    }
}
