package com.example.almatyclient;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class AlmatyConfig {
    private static final Properties DATA = new Properties();
    private static boolean loaded;

    private AlmatyConfig() {
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }

        loaded = true;
        Path path = path();
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            DATA.load(reader);
        } catch (IOException ignored) {
            DATA.clear();
        }
    }

    public static synchronized void save() {
        load();
        Path path = path();

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                DATA.store(writer, "AlmatyClient");
            }
        } catch (IOException ignored) {
        }
    }

    public static synchronized boolean getBoolean(String key, boolean fallback) {
        load();
        return Boolean.parseBoolean(DATA.getProperty(key, Boolean.toString(fallback)));
    }

    public static synchronized void setBoolean(String key, boolean value) {
        load();
        DATA.setProperty(key, Boolean.toString(value));
        save();
    }

    public static synchronized int getInt(String key, int fallback) {
        load();
        try {
            return Integer.parseInt(DATA.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static synchronized void setInt(String key, int value) {
        load();
        DATA.setProperty(key, Integer.toString(value));
        save();
    }

    public static synchronized String getString(String key, String fallback) {
        load();
        return DATA.getProperty(key, fallback);
    }

    public static synchronized void setString(String key, String value) {
        load();
        DATA.setProperty(key, value);
        save();
    }

    public static synchronized List<String> getList(String key) {
        load();
        String raw = DATA.getProperty(key, "");
        if (raw.isBlank()) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>();
        for (String value : raw.split(",")) {
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    public static synchronized void setList(String key, List<String> values) {
        load();
        DATA.setProperty(key, String.join(",", values));
        save();
    }

    private static Path path() {
        return Minecraft.getInstance()
                .gameDirectory
                .toPath()
                .resolve("config")
                .resolve(AlmatyClient.MOD_ID.toLowerCase(Locale.ROOT))
                .resolve("settings.properties");
    }
}
