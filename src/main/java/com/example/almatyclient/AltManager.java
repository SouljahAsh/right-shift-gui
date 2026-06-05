package com.example.almatyclient;

import com.mojang.authlib.GameProfile;
import com.example.almatyclient.mixin.ClientPacketListenerAccessor;
import com.example.almatyclient.mixin.MinecraftUserAccessor;
import com.example.almatyclient.mixin.PlayerGameProfileAccessor;
import com.example.almatyclient.mixin.PlayerInfoAccessor;
import net.minecraft.client.User;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.UUID;

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

        User user = client.getUser();
        UUID profileId = user != null ? user.getProfileId() : null;

        if (user != null) {
            User renamedUser = new User(
                    nick,
                    user.getProfileId(),
                    user.getAccessToken(),
                    user.getXuid(),
                    user.getClientId()
            );
            ((MinecraftUserAccessor) client).almatyclient$setUser(renamedUser);
            replaceFieldsOfType(client, User.class, renamedUser);
            replaceStringFields(user, nick);
        }

        GameProfile replacement = null;
        if (client.player != null) {
            GameProfile current = client.player.getGameProfile();
            profileId = current.id() != null ? current.id() : profileId;
            replacement = createRenamedProfile(current, nick);

            ((PlayerGameProfileAccessor) client.player).almatyclient$setGameProfile(replacement);
            replaceGameProfileFields(client.player, profileId, replacement);
            replaceStringFields(client.player, nick);
            client.player.setCustomName(Component.literal(nick));
            client.player.setCustomNameVisible(false);
        }

        if (replacement == null && profileId != null) {
            replacement = new GameProfile(profileId, nick);
        }

        if (replacement != null) {
            replaceGameProfileFields(client, profileId, replacement);
            replaceGameProfileFields(client.level, profileId, replacement);
            updateConnectionProfiles(client.getConnection(), profileId, replacement);
        }
    }

    private static void updateConnectionProfiles(ClientPacketListener connection, UUID profileId, GameProfile replacement) {
        if (connection == null || profileId == null) {
            return;
        }

        replaceGameProfileFields(connection, profileId, replacement);
        ((ClientPacketListenerAccessor) connection).almatyclient$setLocalGameProfile(replacement);

        PlayerInfo localInfo = connection.getPlayerInfo(profileId);
        if (localInfo != null) {
            ((PlayerInfoAccessor) localInfo).almatyclient$setProfile(replacement);
            replaceGameProfileFields(localInfo, profileId, replacement);
            replaceStringFields(localInfo, replacement.name());
        }

        Collection<PlayerInfo> listed = connection.getListedOnlinePlayers();
        for (PlayerInfo info : listed) {
            if (info != null && info.getProfile() != null && profileId.equals(info.getProfile().id())) {
                ((PlayerInfoAccessor) info).almatyclient$setProfile(replacement);
                replaceGameProfileFields(info, profileId, replacement);
                replaceStringFields(info, replacement.name());
            }
        }
    }

    private static GameProfile createRenamedProfile(GameProfile oldProfile, String nick) {
        return new GameProfile(oldProfile.id(), nick, oldProfile.properties());
    }

    private static void replaceGameProfileFields(Object target, UUID profileId, GameProfile replacement) {
        if (target == null || profileId == null || replacement == null) {
            return;
        }

        Class<?> type = target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != GameProfile.class || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    GameProfile current = (GameProfile) field.get(target);
                    if (current == null || profileId.equals(current.id()) || isLocalProfileField(field)) {
                        field.set(target, replacement);
                    }
                } catch (IllegalAccessException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private static <T> void replaceFieldsOfType(Object target, Class<T> fieldType, T value) {
        if (target == null || value == null) {
            return;
        }

        Class<?> type = target.getClass();
        while (type != null) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() != fieldType || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    field.set(target, value);
                } catch (IllegalAccessException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }

    private static boolean isLocalProfileField(Field field) {
        String name = field.getName().toLowerCase();
        return name.contains("local") || name.contains("singleplayer");
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
                    String name = field.getName().toLowerCase();
                    if (name.contains("name") || name.contains("username")) {
                        field.set(target, value);
                    }
                } catch (IllegalAccessException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
    }
}
