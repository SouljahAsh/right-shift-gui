package com.example.almatyclient;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class AltManager {
    private static final Random RANDOM = new Random();
    private static final List<String> NICKS = new ArrayList<>();
    private static String selectedNick = "";
    private static boolean loaded;

    private AltManager() {
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
        load();
        String nick = sanitizeNick(rawNick);

        if (nick.isEmpty()) {
            return false;
        }

        if (NICKS.stream().noneMatch(existing -> existing.equalsIgnoreCase(nick))) {
            NICKS.add(nick);
        }

        selectedNick = nick;
        save();
        return true;
    }

    public static synchronized void selectNick(String nick) {
        load();

        for (String existing : NICKS) {
            if (existing.equalsIgnoreCase(nick)) {
                selectedNick = existing;
                save();
                return;
            }
        }
    }

    public static String randomNick() {
        String[] prefixes = {"Almaty", "Shift", "Sprint", "Glide", "Nova", "Pixel", "Astra", "KZ"};
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

    private static void load() {
        if (loaded) {
            return;
        }

        loaded = true;
        Path path = configPath();

        if (!Files.exists(path)) {
            return;
        }

        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.startsWith("selected=")) {
                    selectedNick = sanitizeNick(line.substring("selected=".length()));
                    continue;
                }

                String nick = sanitizeNick(line);
                if (!nick.isEmpty() && NICKS.stream().noneMatch(existing -> existing.equalsIgnoreCase(nick))) {
                    NICKS.add(nick);
                }
            }
        } catch (IOException ignored) {
            NICKS.clear();
            selectedNick = "";
        }
    }

    private static void save() {
        Path path = configPath();
        List<String> lines = new ArrayList<>();

        if (!selectedNick.isEmpty()) {
            lines.add("selected=" + selectedNick);
        }

        lines.addAll(NICKS);

        try {
            Files.createDirectories(path.getParent());
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static Path configPath() {
        return Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve("config")
                .resolve(AlmatyClient.MOD_ID.toLowerCase(Locale.ROOT))
                .resolve("alts.txt");
    }
}
