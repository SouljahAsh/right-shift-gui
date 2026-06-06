package com.example.almatyclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CombatAutomation {
    private static final String AURA_ENABLED_KEY = "aura.enabled";
    private static final String AURA_RANGE_BLOCKS_KEY = "aura.rangeBlocks";
    private static final String AURA_RANGE_TENTHS_KEY = "aura.rangeTenths";
    private static final String AURA_ROTATE_KEY = "aura.rotate";
    private static final String AURA_PLAYERS_KEY = "aura.players";
    private static final String AURA_MOBS_KEY = "aura.mobs";
    private static final String AURA_JUMP_ONLY_KEY = "aura.jumpOnly";
    private static final String AURA_MOVE_MODE_KEY = "aura.moveMode";
    private static final String AURA_TARGET_MODE_KEY = "aura.targetMode";
    private static final int DEFAULT_RANGE_BLOCKS = 4;
    private static final int MIN_RANGE_BLOCKS = 2;
    private static final int MAX_RANGE_BLOCKS = 6;
    private static final int MOVE_HOLD_POSITION = 0;
    private static final int MOVE_LEAP_IN = 1;
    private static final int TARGET_NEAREST = 0;
    private static final int TARGET_LOWEST_HEALTH = 1;
    private static final double ATTACK_REACH = 2.95D;
    private static final double ATTACK_REACH_SQ = ATTACK_REACH * ATTACK_REACH;
    private static final double LEAP_STOP_DISTANCE = 2.55D;
    private static final double LEAP_STOP_DISTANCE_SQ = LEAP_STOP_DISTANCE * LEAP_STOP_DISTANCE;
    private static final double CRITICAL_FALL_SPEED = -0.001D;
    private static final float AIM_TOLERANCE = 0.35F;
    private static final int REQUIRED_AIM_READY_TICKS = 1;
    private static final int ATTACK_SYNC_TICKS = 3;
    private static final int LEAP_JUMP_COOLDOWN_TICKS = 5;

    private static int lockedTargetId = -1;
    private static int aimReadyTicks;
    private static int ticksSinceAttack = ATTACK_SYNC_TICKS;
    private static int jumpCooldown;
    private static boolean forcedForward;

    private CombatAutomation() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(CombatAutomation::tick);
    }

    private static void tick(Minecraft client) {
        if (!isAuraEnabled()) {
            clearLockedTarget();
            releaseForcedMovement(client);
            return;
        }
        if (client.level == null || client.player == null || client.gameMode == null) {
            clearLockedTarget();
            releaseForcedMovement(client);
            return;
        }

        LocalPlayer player = client.player;
        Entity target = lockedTarget(client, player);
        if (target == null) {
            target = findTarget(client, player);
            lockedTargetId = target == null ? -1 : target.getId();
        }
        if (target == null) {
            releaseForcedMovement(client);
            aimReadyTicks = 0;
            return;
        }

        if (auraRotate()) {
            smoothRotateToTarget(player, target);
            if (!isAimReady(player, target)) {
                aimReadyTicks = 0;
                return;
            }
        } else if (!isAimReady(player, target)) {
            aimReadyTicks = 0;
            return;
        }
        aimReadyTicks++;
        if (auraJumpOnly() && auraMoveMode() == MOVE_LEAP_IN) {
            leapTowardTarget(client, player, target);
        } else {
            releaseForcedMovement(client);
        }
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }
        ticksSinceAttack++;
        if (player.getAttackStrengthScale(0.0F) < 1.0F) {
            return;
        }
        if (aimReadyTicks < REQUIRED_AIM_READY_TICKS) {
            return;
        }
        if (!isInAttackReach(player, target)) {
            return;
        }
        if (auraJumpOnly()) {
            prepareCriticalAttack(client, player);
        }
        if (auraJumpOnly() && !isCriticalWindow(player)) {
            return;
        }
        if (ticksSinceAttack < ATTACK_SYNC_TICKS) {
            return;
        }
        client.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        ticksSinceAttack = 0;
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

            double distanceSq = distanceToHitboxSqr(eyePosition, entity.getBoundingBox());
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
        float pitch = clamp(rotation[1], -90.0F, 90.0F);

        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yHeadRot = yaw;
        player.yBodyRot = yaw;
    }

    private static boolean isAimReady(LocalPlayer player, Entity target) {
        float[] rotation = targetRotation(player, target);
        float yawDelta = Math.abs(wrapDegrees(rotation[0] - player.getYRot()));
        float pitchDelta = Math.abs(rotation[1] - player.getXRot());
        return yawDelta <= AIM_TOLERANCE && pitchDelta <= AIM_TOLERANCE;
    }

    private static float[] targetRotation(LocalPlayer player, Entity target) {
        Vec3 eyePosition = player.getEyePosition();
        Vec3 center = aimPoint(player, target);
        double dx = center.x - eyePosition.x;
        double dy = center.y - eyePosition.y;
        double dz = center.z - eyePosition.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));
        return new float[]{yaw, pitch};
    }

    private static void leapTowardTarget(Minecraft client, LocalPlayer player, Entity target) {
        double distanceSq = distanceToHitboxSqr(player.getEyePosition(), target.getBoundingBox());
        if (distanceSq > LEAP_STOP_DISTANCE_SQ) {
            releaseForcedMovement(client);
        } else {
            releaseForcedMovement(client);
        }
        if (player.onGround() && jumpCooldown <= 0) {
            player.jumpFromGround();
            jumpCooldown = LEAP_JUMP_COOLDOWN_TICKS;
        }
    }

    private static void prepareCriticalAttack(Minecraft client, LocalPlayer player) {
        if (!player.isSprinting()) {
            return;
        }

        player.setSprinting(false);
        player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        if (client.options != null) {
            client.options.keySprint.setDown(false);
        }
    }

    private static boolean isCriticalWindow(LocalPlayer player) {
        return player.fallDistance > 0.0D
                && player.getDeltaMovement().y <= CRITICAL_FALL_SPEED
                && !player.onGround()
                && !player.isInWater()
                && !player.onClimbable()
                && !player.isMobilityRestricted()
                && !player.isPassenger()
                && !player.isSprinting();
    }

    private static boolean isInAttackReach(LocalPlayer player, Entity target) {
        return distanceToHitboxSqr(player.getEyePosition(), target.getBoundingBox()) <= ATTACK_REACH_SQ;
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

    private static Vec3 aimPoint(LocalPlayer player, Entity target) {
        return hitboxCenter(target);
    }

    public static boolean isAuraEnabled() {
        return AlmatyConfig.getBoolean(AURA_ENABLED_KEY, false);
    }

    public static void setAuraEnabled(boolean enabled) {
        AlmatyConfig.setBoolean(AURA_ENABLED_KEY, enabled);
        clearLockedTarget();
        if (!enabled) {
            releaseForcedMovement(Minecraft.getInstance());
        }
    }

    public static double auraRange() {
        return auraRangeBlocks();
    }

    public static void cycleAuraRange() {
        int range = auraRangeBlocks() + 1;
        if (range > MAX_RANGE_BLOCKS) {
            range = MIN_RANGE_BLOCKS;
        }
        AlmatyConfig.setInt(AURA_RANGE_BLOCKS_KEY, range);
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

    private static int auraRangeBlocks() {
        int legacyTenths = AlmatyConfig.getInt(AURA_RANGE_TENTHS_KEY, DEFAULT_RANGE_BLOCKS * 10);
        int fallbackBlocks = Math.round(legacyTenths / 10.0F);
        return clampRangeBlocks(AlmatyConfig.getInt(AURA_RANGE_BLOCKS_KEY, fallbackBlocks));
    }

    private static int clampRangeBlocks(int value) {
        return Math.max(MIN_RANGE_BLOCKS, Math.min(MAX_RANGE_BLOCKS, value));
    }

    private static void clearLockedTarget() {
        lockedTargetId = -1;
        aimReadyTicks = 0;
        ticksSinceAttack = ATTACK_SYNC_TICKS;
    }

    private static void forceForwardMovement(Minecraft client) {
        if (client.options == null || client.options.keyUp.isDown()) {
            return;
        }
        client.options.keyUp.setDown(true);
        forcedForward = true;
    }

    private static void releaseForcedMovement(Minecraft client) {
        if (forcedForward && client.options != null) {
            client.options.keyUp.setDown(false);
        }
        forcedForward = false;
    }

    private static double distanceToHitboxSqr(Vec3 point, AABB box) {
        double x = clamp(point.x, box.minX, box.maxX);
        double y = clamp(point.y, box.minY, box.maxY);
        double z = clamp(point.z, box.minZ, box.maxZ);
        double dx = point.x - x;
        double dy = point.y - y;
        double dz = point.z - z;
        return dx * dx + dy * dy + dz * dz;
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
