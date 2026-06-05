package com.example.almatyclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.gui.Font;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

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
    private static final String ENTITY_OVERLAY_KEY = "entityOverlay.enabled";
    private static final String ENTITY_OVERLAY_PLAYERS_KEY = "entityOverlay.players";
    private static final String ENTITY_OVERLAY_MOBS_KEY = "entityOverlay.mobs";
    private static final String ENTITY_OVERLAY_RED_KEY = "entityOverlay.red";
    private static final String ENTITY_OVERLAY_GREEN_KEY = "entityOverlay.green";
    private static final String ENTITY_OVERLAY_BLUE_KEY = "entityOverlay.blue";
    private static final String ENTITY_OVERLAY_WIDTH_KEY = "entityOverlay.width";

    private static final RenderPipeline ESP_LINES_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "esp_lines"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build());
    private static final RenderType ESP_LINES = RenderType.create(
            "almatyclient_esp_lines",
            RenderSetup.builder(ESP_LINES_PIPELINE)
                    .bufferSize(RenderType.SMALL_BUFFER_SIZE)
                    .createRenderSetup()
    );

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
        });

        WorldRenderEvents.AFTER_ENTITIES.register(AlmatyClient::renderWorldOverlays);

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

    public static boolean isEntityOverlayEnabled() {
        return AlmatyConfig.getBoolean(ENTITY_OVERLAY_KEY, false);
    }

    public static void setEntityOverlayEnabled(boolean enabled) {
        AlmatyConfig.setBoolean(ENTITY_OVERLAY_KEY, enabled);
    }

    public static boolean entityOverlayPlayers() {
        return AlmatyConfig.getBoolean(ENTITY_OVERLAY_PLAYERS_KEY, true);
    }

    public static void setEntityOverlayPlayers(boolean enabled) {
        AlmatyConfig.setBoolean(ENTITY_OVERLAY_PLAYERS_KEY, enabled);
    }

    public static boolean entityOverlayMobs() {
        return AlmatyConfig.getBoolean(ENTITY_OVERLAY_MOBS_KEY, true);
    }

    public static void setEntityOverlayMobs(boolean enabled) {
        AlmatyConfig.setBoolean(ENTITY_OVERLAY_MOBS_KEY, enabled);
    }

    public static int entityOverlayRed() {
        return AlmatyConfig.getInt(ENTITY_OVERLAY_RED_KEY, 70);
    }

    public static int entityOverlayGreen() {
        return AlmatyConfig.getInt(ENTITY_OVERLAY_GREEN_KEY, 160);
    }

    public static int entityOverlayBlue() {
        return AlmatyConfig.getInt(ENTITY_OVERLAY_BLUE_KEY, 255);
    }

    public static void setEntityOverlayColor(int red, int green, int blue) {
        AlmatyConfig.setInt(ENTITY_OVERLAY_RED_KEY, clampColor(red));
        AlmatyConfig.setInt(ENTITY_OVERLAY_GREEN_KEY, clampColor(green));
        AlmatyConfig.setInt(ENTITY_OVERLAY_BLUE_KEY, clampColor(blue));
    }

    public static int entityOverlayColorRgb() {
        return (entityOverlayRed() << 16) | (entityOverlayGreen() << 8) | entityOverlayBlue();
    }

    public static int entityOverlayWidth() {
        return AlmatyConfig.getInt(ENTITY_OVERLAY_WIDTH_KEY, 2);
    }

    public static void setEntityOverlayWidth(int width) {
        AlmatyConfig.setInt(ENTITY_OVERLAY_WIDTH_KEY, Math.max(1, Math.min(6, width)));
    }

    public static boolean shouldUseEntityOverlayColor(Entity entity) {
        return isEntityOverlayTarget(entity);
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

    private static boolean isEspTarget(Entity entity) {
        if (entity instanceof Player) {
            return espPlayers();
        }
        if (entity instanceof Mob) {
            return espMobs();
        }
        return entity instanceof ItemEntity && espItems();
    }

    private static boolean isEntityOverlayTarget(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (!isEntityOverlayEnabled() || client.player == null || entity == client.player || !(entity instanceof LivingEntity)) {
            return false;
        }

        if (entity instanceof Player) {
            return entityOverlayPlayers();
        }
        return entity instanceof Mob && entityOverlayMobs();
    }

    private static void renderWorldOverlays(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || context.matrices() == null || context.consumers() == null) {
            return;
        }

        if (!isEspEnabled() && !isEntityOverlayEnabled()) {
            return;
        }

        PoseStack matrices = context.matrices();
        MultiBufferSource consumers = context.consumers();
        Vec3 camera = context.gameRenderer().getMainCamera().position();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player) {
                continue;
            }

            boolean espTarget = isEspEnabled() && isEspTarget(entity);
            boolean overlayTarget = isEntityOverlayTarget(entity);
            if (!espTarget && !overlayTarget) {
                continue;
            }

            if (espTarget || overlayTarget) {
                renderBoundingBox(matrices, consumers, entity, camera);
            }
            if (espTarget) {
                renderNamePlate(client, matrices, consumers, entity, camera);
            }
        }
    }

    private static void renderBoundingBox(PoseStack matrices, MultiBufferSource consumers, Entity entity, Vec3 camera) {
        AABB box = entity.getBoundingBox().move(-camera.x, -camera.y, -camera.z);
        int red = entityOverlayRed();
        int green = entityOverlayGreen();
        int blue = entityOverlayBlue();
        float width = entityOverlayWidth();
        VertexConsumer lines = consumers.getBuffer(ESP_LINES);
        PoseStack.Pose pose = matrices.last();

        addLine(lines, pose, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, red, green, blue, width);
        addLine(lines, pose, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, red, green, blue, width);
        addLine(lines, pose, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, red, green, blue, width);
        addLine(lines, pose, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, red, green, blue, width);

        addLine(lines, pose, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, red, green, blue, width);
        addLine(lines, pose, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, red, green, blue, width);
        addLine(lines, pose, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, red, green, blue, width);
        addLine(lines, pose, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, red, green, blue, width);

        addLine(lines, pose, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, red, green, blue, width);
        addLine(lines, pose, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, red, green, blue, width);
        addLine(lines, pose, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, red, green, blue, width);
        addLine(lines, pose, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, red, green, blue, width);
    }

    private static void addLine(VertexConsumer lines, PoseStack.Pose pose, double x1, double y1, double z1, double x2, double y2, double z2, int red, int green, int blue, float width) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        lines.addVertex(pose, (float) x1, (float) y1, (float) z1)
                .setColor(red, green, blue, 255)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(width);
        lines.addVertex(pose, (float) x2, (float) y2, (float) z2)
                .setColor(red, green, blue, 255)
                .setNormal(pose, nx, ny, nz)
                .setLineWidth(width);
    }

    private static void renderNamePlate(Minecraft client, PoseStack matrices, MultiBufferSource consumers, Entity entity, Vec3 camera) {
        if (!(entity instanceof LivingEntity living) || (!espName() && !espHealth())) {
            return;
        }

        Component label = Component.literal(entityLabel(entity, living));
        Font font = client.font;

        matrices.pushPose();
        matrices.translate(entity.getX() - camera.x, entity.getY() - camera.y + entity.getBbHeight() + 0.55D, entity.getZ() - camera.z);
        matrices.mulPose(client.getEntityRenderDispatcher().camera.rotation());
        matrices.scale(-0.025F, -0.025F, 0.025F);

        float x = -font.width(label) / 2.0F;
        font.drawInBatch(label, x, 0.0F, 0xFFFFFFFF, false, matrices.last().pose(), consumers, Font.DisplayMode.SEE_THROUGH, 0x66000000, LightTexture.FULL_BRIGHT);
        font.drawInBatch(label, x, 0.0F, 0xFFFFFFFF, false, matrices.last().pose(), consumers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        matrices.popPose();
    }

    private static String entityLabel(Entity entity, LivingEntity living) {
        StringBuilder text = new StringBuilder();
        if (espName()) {
            text.append(entity.getName().getString());
        }
        if (espHealth()) {
            if (!text.isEmpty()) {
                text.append(" | ");
            }
            text.append("HP: ").append(Math.round(living.getHealth()));
        }
        return text.toString();
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
}
