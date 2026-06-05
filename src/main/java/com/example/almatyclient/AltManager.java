package com.example.almatyclient;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class AltManager {
    private static final String NICKS_KEY = "alts.nicks";
    private static final String SELECTED_KEY = "alts.selected";
    private static final Random RANDOM = new Random();
    private static final List<String> NICKS = new ArrayList<>();
    private static String selectedNick = "";
    private static boolean loaded;

    private AltManager() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        loaded = true;
        NICKS.clear();
        for (String nick : AlmatyConfig.getList(NICKS_KEY)) {
            String cleaned = sanitizeNick(nick);
            if (!cleaned.isEmpty() && NICKS.stream().noneMatch(existing -> existing.equalsIgnoreCase(cleaned))) {
                NICKS.add(cleaned);
            }
        }
        selectedNick = sanitizeNick(AlmatyConfig.getString(SELECTED_KEY, ""));
    }

    public static synchronized List<String> getNicks() {
        load();
        return Collections.unmodifiableList(new ArrayList<>(NICKS));
    }

    public static synchronized String getSelectedNick() {
        load();
        return selectedNick;
    }

    public static synchronized boolean saveNick(String rawNick) {
        String nick = applyNick(rawNick);
        if (nick.isEmpty()) {
            return false;
        }

        if (NICKS.stream().noneMatch(existing -> existing.equalsIgnoreCase(nick))) {
            NICKS.add(nick);
        }
        persist();
        return true;
    }

    public static synchronized String applyNick(String rawNick) {
        load();
        String nick = sanitizeNick(rawNick);
        if (nick.isEmpty()) {
            return "";
        }

        selectedNick = nick;
        AlmatyConfig.setString(SELECTED_KEY, selectedNick);
        applyToMinecraftSession(nick);
        return nick;
    }

    public static synchronized void selectNick(String nick) {
        applyNick(nick);
    }

    public static synchronized void deleteNick(String rawNick) {
        load();
        String nick = sanitizeNick(rawNick);
        NICKS.removeIf(existing -> existing.equalsIgnoreCase(nick));
        if (selectedNick.equalsIgnoreCase(nick)) {
            selectedNick = "";
            AlmatyConfig.setString(SELECTED_KEY, "");
        }
        persist();
    }

    public static String randomNick() {
        String[] prefixes = {"Almaty", "Blue", "Sprint", "Glide", "Nova", "Pixel", "Astra", "KZ"};
        String[] suffixes = {"Runner", "Client", "Wave", "Mode", "Rush", "Sky", "Tap", "Flow"};
        String prefix = prefixes[RANDOM.nextInt(prefixes.length)];
        String suffix = suffixes[RANDOM.nextInt(suffixes.length)];
        int number = 100 + RANDOM.nextInt(900);
        return sanitizeNick(prefix + suffix + number);
    }

    public static String sanitizeNick(String rawNick) {
        if (rawNick == null) {
            return "";
        }

        String cleaned = rawNick.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.length() > 16) {
            cleaned = cleaned.substring(0, 16);
        }

        return cleaned.length() >= 3 ? cleaned : "";
    }

    private static void persist() {
        AlmatyConfig.setList(NICKS_KEY, NICKS);
        AlmatyConfig.setString(SELECTED_KEY, selectedNick);
    }

    private static void applyToMinecraftSession(String nick) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }

        Object user = client.getUser();
        if (user != null) {
            replaceStringFields(user, nick);
        }

        if (client.player != null) {
            GameProfile profile = client.player.getGameProfile();
            replaceStringFields(profile, nick);
        }
    }

    private static void replaceStringFields(Object target, String value) {
        Class<?> type = target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != String.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    String current = (String) field.get(target);
                    String name = field.getName().toLowerCase();
                    if (name.contains("name") || current == null || current.equals(getSelectedNick())) {
                        field.set(target, value);
                    }
                } catch (IllegalAccessException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }
}
