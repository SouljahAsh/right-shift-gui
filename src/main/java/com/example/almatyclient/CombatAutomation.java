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
    private static final String AURA_JUMP_ONLY_KEY = "aura.jumpOnly";
    private static final String AURA_MOVE_MODE_KEY = "aura.moveMode";
    private static final String AURA_TARGET_MODE_KEY = "aura.targetMode";
    private static final int DEFAULT_RANGE_TENTHS = 40;
    private static final int MIN_RANGE_TENTHS = 20;
    private static final int MAX_RANGE_TENTHS = 60;
    private static final int RANGE_STEP_TENTHS = 5;
    private static final int MOVE_HOLD_POSITION = 0;
    private static final int MOVE_LEAP_IN = 1;
    private static final int TARGET_NEAREST = 0;
    private static final int TARGET_LOWEST_HEALTH = 1;
    private static final double LEAP_SPEED = 0.28D;
    private static final float CRITICAL_FALL_DISTANCE = 0.08F;
    private static final float MAX_YAW_STEP = 10.0F;
    private static final float MAX_PITCH_STEP = 8.0F;
    private static final float AIM_TOLERANCE = 2.5F;

    private static int lockedTargetId = -1;

    private CombatAutomation() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(CombatAutomation::tick);
    }

    private static void tick(Minecraft client) {
        if (!isAuraEnabled()) {
            clearLockedTarget();
            return;
        }
        if (client.level == null || client.player == null || client.gameMode == null) {
            clearLockedTarget();
            return;
        }

        LocalPlayer player = client.player;
        Entity target = lockedTarget(client, player);
        if (target == null) {
            target = findTarget(client, player);
            lockedTargetId = target == null ? -1 : target.getId();
        }
        if (target == null) {
            return;
        }

        if (auraRotate()) {
            smoothRotateToTarget(player, target);
            if (!isAimReady(player, target)) {
                return;
            }
        }
        if (auraJumpOnly() && auraMoveMode() == MOVE_LEAP_IN) {
            leapTowardTarget(player, target);
        }
        if (player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }
        if (auraJumpOnly() && !isCriticalWindow(player)) {
            return;
        }
        client.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
    }

    private static Entity lockedTarget(Minecraft client, LocalPlayer player) {
        if (lockedTargetId < 0 || client.level == null) {
            return null;
        }

        Entity entity = client.level.getEntity(lockedTargetId);
        if (entity == null || !isValidTarget(player, entity)) {
            clearLockedTarget();
            return null;
        }
        return entity;
    }

    private static Entity findTarget(Minecraft client, LocalPlayer player) {
        Vec3 eyePosition = player.getEyePosition();
        Entity bestTarget = null;
        double rangeSq = auraRange() * auraRange();
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!isValidTarget(player, entity)) {
                continue;
            }

            double distanceSq = eyePosition.distanceToSqr(hitboxCenter(entity));
            if (distanceSq > rangeSq) {
                continue;
            }

            double score = targetScore(entity, distanceSq);
            if (score <= bestScore) {
                bestTarget = entity;
                bestScore = score;
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

    private static void smoothRotateToTarget(LocalPlayer player, Entity target) {
        float[] rotation = targetRotation(player, target);
        float yaw = rotation[0];
        float pitch = rotation[1];
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();
        float nextYaw = currentYaw + clamp(wrapDegrees(yaw - currentYaw), -MAX_YAW_STEP, MAX_YAW_STEP);
        float nextPitch = currentPitch + clamp(pitch - currentPitch, -MAX_PITCH_STEP, MAX_PITCH_STEP);

        player.setYRot(nextYaw);
        player.setXRot(clamp(nextPitch, -90.0F, 90.0F));
        player.yHeadRot = nextYaw;
        player.yBodyRot = nextYaw;
    }

    private static boolean isAimReady(LocalPlayer player, Entity target) {
        float[] rotation = targetRotation(player, target);
        float yawDelta = Math.abs(wrapDegrees(rotation[0] - player.getYRot()));
        float pitchDelta = Math.abs(rotation[1] - player.getXRot());
        return yawDelta <= AIM_TOLERANCE && pitchDelta <= AIM_TOLERANCE;
    }

    private static float[] targetRotation(LocalPlayer player, Entity target) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 center = hitboxCenter(target);
        double dx = center.x - eyePosition.x;
        double dy = center.y - eyePosition.y;
        double dz = center.z - eyePosition.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));
        return new float[]{yaw, pitch};
    }

    private static void leapTowardTarget(LocalPlayer player, Entity target) {
        Vec3 center = hitboxCenter(target);
        Vec3 direction = new Vec3(center.x - player.getX(), 0.0D, center.z - player.getZ());
        if (direction.lengthSqr() > 0.0001D) {
            direction = direction.normalize().scale(LEAP_SPEED);
            Vec3 velocity = player.getDeltaMovement();
            player.setDeltaMovement(direction.x, velocity.y, direction.z);
        }
        if (player.onGround()) {
            player.jumpFromGround();
        }
    }

    private static boolean isCriticalWindow(LocalPlayer player) {
        return !player.onGround() && player.fallDistance >= CRITICAL_FALL_DISTANCE;
    }

    private static double targetScore(Entity entity, double distanceSq) {
        if (auraTargetMode() == TARGET_LOWEST_HEALTH && entity instanceof LivingEntity living) {
            return living.getHealth();
        }
        return distanceSq;
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
        clearLockedTarget();
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

    public static boolean auraJumpOnly() {
        return AlmatyConfig.getBoolean(AURA_JUMP_ONLY_KEY, true);
    }

    public static void setAuraJumpOnly(boolean enabled) {
        AlmatyConfig.setBoolean(AURA_JUMP_ONLY_KEY, enabled);
    }

    public static int auraMoveMode() {
        int mode = AlmatyConfig.getInt(AURA_MOVE_MODE_KEY, MOVE_HOLD_POSITION);
        return mode == MOVE_LEAP_IN ? MOVE_LEAP_IN : MOVE_HOLD_POSITION;
    }

    public static void cycleAuraMoveMode() {
        AlmatyConfig.setInt(AURA_MOVE_MODE_KEY, auraMoveMode() == MOVE_HOLD_POSITION ? MOVE_LEAP_IN : MOVE_HOLD_POSITION);
    }

    public static String auraMoveModeText() {
        return auraMoveMode() == MOVE_LEAP_IN ? "Leap In" : "Hold Position";
    }

    public static int auraTargetMode() {
        int mode = AlmatyConfig.getInt(AURA_TARGET_MODE_KEY, TARGET_NEAREST);
        return mode == TARGET_LOWEST_HEALTH ? TARGET_LOWEST_HEALTH : TARGET_NEAREST;
    }

    public static void cycleAuraTargetMode() {
        AlmatyConfig.setInt(AURA_TARGET_MODE_KEY, auraTargetMode() == TARGET_NEAREST ? TARGET_LOWEST_HEALTH : TARGET_NEAREST);
    }

    public static String auraTargetModeText() {
        return auraTargetMode() == TARGET_LOWEST_HEALTH ? "Lowest Health" : "Nearest";
    }

    private static int clampRangeTenths(int value) {
        return Math.max(MIN_RANGE_TENTHS, Math.min(MAX_RANGE_TENTHS, value));
    }

    private static void clearLockedTarget() {
        lockedTargetId = -1;
    }

    private static float wrapDegrees(float value) {
        value %= 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
