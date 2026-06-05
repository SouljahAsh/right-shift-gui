package com.example.almatyclient;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class AlmatyClient implements ClientModInitializer {
    public static final String MOD_ID = "almatyclient";

    private static final String AUTO_SPRINT_KEY = "feature.autoSprint";
    private static final String PARTICLES_KEY = "feature.particles";
    private static final String ESP_KEY = "feature.esp";
    private static final String ESP_PLAYERS_KEY = "esp.players";
    private static final String ESP_MOBS_KEY = "esp.mobs";
    private static final String ESP_ITEMS_KEY = "esp.items";
    private static final String ESP_NAME_KEY = "esp.name";
    private static final String ESP_HEALTH_KEY = "esp.health";

    private static final Set<Integer> FORCED_GLOW = new HashSet<>();
    private static final Map<Integer, NameState> FORCED_NAMES = new HashMap<>();
    private static boolean sprintKeyForced;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls")
    );

    private static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        AlmatyConfig.load();
        AltManager.load();

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
            tickEsp(client);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() && isParticlesEnabled() && entity instanceof LivingEntity living) {
                spawnWaterBubbles(living);
            }
            return InteractionResult.PASS;
        });
    }

    public static boolean isAutoSprintEnabled() {
        return AlmatyConfig.getBoolean(AUTO_SPRINT_KEY, false);
    }

    public static void toggleAutoSprint() {
        setAutoSprintEnabled(!isAutoSprintEnabled());
    }

    public static void setAutoSprintEnabled(boolean enabled) {
        AlmatyConfig.setBoolean(AUTO_SPRINT_KEY, enabled);
        if (!enabled) {
            releaseForcedSprint(Minecraft.getInstance());
        }
    }

    public static boolean isParticlesEnabled() {
        return AlmatyConfig.getBoolean(PARTICLES_KEY, false);
    }

    public static void setParticlesEnabled(boolean enabled) {
        AlmatyConfig.setBoolean(PARTICLES_KEY, enabled);
    }

    public static boolean isEspEnabled() {
        return AlmatyConfig.getBoolean(ESP_KEY, false);
    }

    public static void setEspEnabled(boolean enabled) {
        AlmatyConfig.setBoolean(ESP_KEY, enabled);
        if (!enabled) {
            clearForcedGlow(Minecraft.getInstance());
        }
    }

    public static boolean espPlayers() {
        return AlmatyConfig.getBoolean(ESP_PLAYERS_KEY, true);
    }

    public static void setEspPlayers(boolean enabled) {
        AlmatyConfig.setBoolean(ESP_PLAYERS_KEY, enabled);
    }

    public static boolean espMobs() {
        return AlmatyConfig.getBoolean(ESP_MOBS_KEY, true);
    }

    public static void setEspMobs(boolean enabled) {
        AlmatyConfig.setBoolean(ESP_MOBS_KEY, enabled);
    }

    public static boolean espItems() {
        return AlmatyConfig.getBoolean(ESP_ITEMS_KEY, false);
    }

    public static void setEspItems(boolean enabled) {
        AlmatyConfig.setBoolean(ESP_ITEMS_KEY, enabled);
    }

    public static boolean espName() {
        return AlmatyConfig.getBoolean(ESP_NAME_KEY, true);
    }

    public static void setEspName(boolean enabled) {
        AlmatyConfig.setBoolean(ESP_NAME_KEY, enabled);
    }

    public static boolean espHealth() {
        return AlmatyConfig.getBoolean(ESP_HEALTH_KEY, true);
    }

    public static void setEspHealth(boolean enabled) {
        AlmatyConfig.setBoolean(ESP_HEALTH_KEY, enabled);
    }

    public static int guiRed() {
        return AlmatyConfig.getInt("gui.red", 35);
    }

    public static int guiGreen() {
        return AlmatyConfig.getInt("gui.green", 95);
    }

    public static int guiBlue() {
        return AlmatyConfig.getInt("gui.blue", 190);
    }

    public static void setGuiColor(int red, int green, int blue) {
        AlmatyConfig.setInt("gui.red", clampColor(red));
        AlmatyConfig.setInt("gui.green", clampColor(green));
        AlmatyConfig.setInt("gui.blue", clampColor(blue));
    }

    public static int accentColor(int alpha) {
        return ((alpha & 255) << 24) | (guiRed() << 16) | (guiGreen() << 8) | guiBlue();
    }

    private static void tickAutoSprint(Minecraft client) {
        if (!isAutoSprintEnabled() || client.player == null || client.options == null) {
            releaseForcedSprint(client);
            return;
        }

        boolean walkingForward = client.options.keyUp.isDown();
        boolean blocked = client.player.isCrouching()
                || client.player.isUsingItem()
                || client.player.isPassenger()
                || client.player.horizontalCollision;

        if (walkingForward && !blocked) {
            client.options.keySprint.setDown(true);
            sprintKeyForced = true;
            client.player.setSprinting(true);
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
        if (horizontalSpeed <= 0.035D || horizontalSpeed >= 0.295D) {
            return;
        }

        double target = 0.295D;
        double blend = 0.11D;
        double factor = 1.0D + ((target - horizontalSpeed) / target) * blend;
        client.player.setDeltaMovement(velocity.x * factor, velocity.y, velocity.z * factor);
    }

    private static void tickEsp(Minecraft client) {
        if (client.level == null || client.player == null) {
            FORCED_GLOW.clear();
            return;
        }

        if (!isEspEnabled()) {
            clearForcedGlow(client);
            return;
        }

        Set<Integer> active = new HashSet<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) {
                continue;
            }

            boolean target = isEspTarget(entity);
            if (target) {
                entity.setGlowingTag(true);
                active.add(entity.getId());
                applyNamePlate(entity);
            } else if (FORCED_GLOW.contains(entity.getId())) {
                entity.setGlowingTag(false);
                restoreNamePlate(entity);
            }
        }

        FORCED_NAMES.keySet().removeIf(id -> {
            if (active.contains(id)) {
                return false;
            }

            Entity entity = client.level.getEntity(id);
            if (entity != null) {
                restoreNamePlate(entity);
            }
            return true;
        });
        FORCED_GLOW.clear();
        FORCED_GLOW.addAll(active);
    }

    private static boolean isEspTarget(Entity entity) {
        if (entity instanceof Player) {
            return espPlayers();
        }
        if (entity instanceof Mob) {
            return espMobs();
        }
        return entity instanceof ItemEntity && espItems();
    }

    private static void applyNamePlate(Entity entity) {
        if (!(entity instanceof LivingEntity living) || (!espName() && !espHealth())) {
            restoreNamePlate(entity);
            return;
        }

        FORCED_NAMES.putIfAbsent(entity.getId(), new NameState(entity.getCustomName(), entity.isCustomNameVisible()));

        StringBuilder text = new StringBuilder();
        if (espName()) {
            text.append(entity.getName().getString());
        }
        if (espHealth()) {
            if (!text.isEmpty()) {
                text.append(" ");
            }
            text.append(Math.round(living.getHealth())).append("/").append(Math.round(living.getMaxHealth()));
        }

        entity.setCustomName(net.minecraft.network.chat.Component.literal(text.toString()));
        entity.setCustomNameVisible(true);
    }

    private static void restoreNamePlate(Entity entity) {
        NameState state = FORCED_NAMES.remove(entity.getId());
        if (state == null) {
            return;
        }

        entity.setCustomName(state.name());
        entity.setCustomNameVisible(state.visible());
    }

    private static void clearForcedGlow(Minecraft client) {
        if (client.level != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (FORCED_GLOW.contains(entity.getId())) {
                    entity.setGlowingTag(false);
                }
                if (FORCED_NAMES.containsKey(entity.getId())) {
                    restoreNamePlate(entity);
                }
            }
        }
        FORCED_GLOW.clear();
        FORCED_NAMES.clear();
    }

    private static void spawnWaterBubbles(LivingEntity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        for (int i = 0; i < 12; i++) {
            double offsetX = (client.level.random.nextDouble() - 0.5D) * entity.getBbWidth();
            double offsetY = client.level.random.nextDouble() * entity.getBbHeight();
            double offsetZ = (client.level.random.nextDouble() - 0.5D) * entity.getBbWidth();
            double velocityX = offsetX * 0.045D;
            double velocityY = 0.035D + client.level.random.nextDouble() * 0.045D;
            double velocityZ = offsetZ * 0.045D;

            client.level.addParticle(
                    ParticleTypes.BUBBLE,
                    entity.getX() + offsetX,
                    entity.getY() + offsetY,
                    entity.getZ() + offsetZ,
                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record NameState(Component name, boolean visible) {
    }
}
