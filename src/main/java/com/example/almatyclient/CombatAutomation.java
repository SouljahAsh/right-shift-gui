package com.example.almatyclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CombatAutomation {
    private static final String AURA_ENABLED_KEY = "aura.enabled";
    private static final String AURA_RANGE_TENTHS_KEY = "aura.rangeTenths";
    private static final String AURA_ROTATE_KEY = "aura.rotate";
    private static final String AURA_PLAYERS_KEY = "aura.players";
    private static final String AURA_MOBS_KEY = "aura.mobs";
    private static final int DEFAULT_RANGE_TENTHS = 40;
    private static final int MIN_RANGE_TENTHS = 20;
    private static final int MAX_RANGE_TENTHS = 60;
    private static final int RANGE_STEP_TENTHS = 5;

    private CombatAutomation() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(CombatAutomation::tick);
    }

    private static void tick(Minecraft client) {
        if (!isAuraEnabled()) {
            return;
        }
        if (client.level == null || client.player == null || client.gameMode == null) {
            return;
        }

        LocalPlayer player = client.player;
        if (player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }

        Entity target = findTarget(client, player);
        if (target == null) {
            return;
        }

        if (auraRotate()) {
            rotateToTarget(player, target);
        }
        client.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private static Entity findTarget(Minecraft client, LocalPlayer player) {
        Vec3 eyePosition = player.getEyePosition();
        Entity bestTarget = null;
        double rangeSq = auraRange() * auraRange();
        double bestDistanceSq = rangeSq;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isValidTarget(player, entity)) {
                continue;
            }

            double distanceSq = eyePosition.distanceToSqr(hitboxCenter(entity));
            if (distanceSq <= bestDistanceSq) {
                bestTarget = entity;
                bestDistanceSq = distanceSq;
            }
        }

        return bestTarget;
    }

    private static boolean isValidTarget(LocalPlayer player, Entity entity) {
        if (!(entity instanceof LivingEntity) || entity == player || !entity.isAlive() || !entity.isAttackable() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof Player) {
            return auraPlayers();
        }
        return entity instanceof Mob && auraMobs();
    }

    private static void rotateToTarget(LocalPlayer player, Entity target) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 center = hitboxCenter(target);
        double dx = center.x - eyePosition.x;
        double dy = center.y - eyePosition.y;
        double dz = center.z - eyePosition.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));

        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yHeadRot = yaw;
        player.yBodyRot = yaw;
    }

    private static Vec3 hitboxCenter(Entity entity) {
        AABB box = entity.getBoundingBox();
        return new Vec3(
                (box.minX + box.maxX) * 0.5D,
                (box.minY + box.maxY) * 0.5D,
                (box.minZ + box.maxZ) * 0.5D
        );
    }

    public static boolean isAuraEnabled() {
        return AlmatyConfig.getBoolean(AURA_ENABLED_KEY, false);
    }

    public static void setAuraEnabled(boolean enabled) {
        AlmatyConfig.setBoolean(AURA_ENABLED_KEY, enabled);
    }

    public static double auraRange() {
        return clampRangeTenths(AlmatyConfig.getInt(AURA_RANGE_TENTHS_KEY, DEFAULT_RANGE_TENTHS)) / 10.0D;
    }

    public static void cycleAuraRange() {
        int range = clampRangeTenths(AlmatyConfig.getInt(AURA_RANGE_TENTHS_KEY, DEFAULT_RANGE_TENTHS));
        range += RANGE_STEP_TENTHS;
        if (range > MAX_RANGE_TENTHS) {
            range = MIN_RANGE_TENTHS;
        }
        AlmatyConfig.setInt(AURA_RANGE_TENTHS_KEY, range);
    }

    public static boolean auraRotate() {
        return AlmatyConfig.getBoolean(AURA_ROTATE_KEY, true);
    }

    public static void setAuraRotate(boolean enabled) {
        AlmatyConfig.setBoolean(AURA_ROTATE_KEY, enabled);
    }

    public static boolean auraPlayers() {
        return AlmatyConfig.getBoolean(AURA_PLAYERS_KEY, true);
    }

    public static void setAuraPlayers(boolean enabled) {
        AlmatyConfig.setBoolean(AURA_PLAYERS_KEY, enabled);
    }

    public static boolean auraMobs() {
        return AlmatyConfig.getBoolean(AURA_MOBS_KEY, true);
    }

    public static void setAuraMobs(boolean enabled) {
        AlmatyConfig.setBoolean(AURA_MOBS_KEY, enabled);
    }

    private static int clampRangeTenths(int value) {
        return Math.max(MIN_RANGE_TENTHS, Math.min(MAX_RANGE_TENTHS, value));
    }
}
