package com.example.almatyclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.example.almatyclient.mixin.LivingEntityJumpAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
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
    private static final String INVENTORY_WALK_KEY = "feature.inventoryWalk";
    private static final String NO_JUMP_DELAY_KEY = "feature.noJumpDelay";
    private static final String ESP_PLAYERS_KEY = "esp.players";
    private static final String ESP_MOBS_KEY = "esp.mobs";
    private static final String ESP_ITEMS_KEY = "esp.items";
    private static final String ESP_NAME_KEY = "esp.name";
    private static final String ESP_HEALTH_KEY = "esp.health";
    private static final String SPRINT_STOP_ON_COLLISION_KEY = "sprint.stopOnCollision";
    private static final String SPRINT_START_DELAY_KEY = "sprint.startDelayTicks";
    private static final int ESP_LINE_WIDTH = 2;
    private static final int DEFAULT_SPRINT_START_DELAY_TICKS = 1;
    private static final int MAX_SPRINT_START_DELAY_TICKS = 10;

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
    private static boolean inventoryWalkForced;
    private static int forwardTicks;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "controls")
    );

    private static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        AlmatyConfig.load();
        AltManager.load();
        CombatAutomation.init();
        BindManager.init();

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

            tickInventoryWalk(client);
            tickAutoSprint(client);
            tickNoJumpDelay(client);
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

    public static boolean sprintStopOnCollision() {
        return AlmatyConfig.getBoolean(SPRINT_STOP_ON_COLLISION_KEY, true);
    }

    public static void setSprintStopOnCollision(boolean enabled) {
        AlmatyConfig.setBoolean(SPRINT_STOP_ON_COLLISION_KEY, enabled);
        if (enabled) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && client.player.horizontalCollision) {
                releaseForcedSprint(client);
            }
        }
    }

    public static int sprintStartDelayTicks() {
        return clampSprintDelay(AlmatyConfig.getInt(SPRINT_START_DELAY_KEY, DEFAULT_SPRINT_START_DELAY_TICKS));
    }

    public static void setSprintStartDelayTicks(int ticks) {
        AlmatyConfig.setInt(SPRINT_START_DELAY_KEY, clampSprintDelay(ticks));
    }

    public static void cycleSprintStartDelayTicks() {
        int ticks = sprintStartDelayTicks();
        if (ticks >= MAX_SPRINT_START_DELAY_TICKS) {
            setSprintStartDelayTicks(0);
        } else {
            setSprintStartDelayTicks(ticks + 1);
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

    public static boolean isInventoryWalkEnabled() {
        return AlmatyConfig.getBoolean(INVENTORY_WALK_KEY, false);
    }

    public static void setInventoryWalkEnabled(boolean enabled) {
        AlmatyConfig.setBoolean(INVENTORY_WALK_KEY, enabled);
        if (!enabled) {
            releaseInventoryWalk(Minecraft.getInstance());
        }
    }

    public static boolean isNoJumpDelayEnabled() {
        return AlmatyConfig.getBoolean(NO_JUMP_DELAY_KEY, false);
    }

    public static void setNoJumpDelayEnabled(boolean enabled) {
        AlmatyConfig.setBoolean(NO_JUMP_DELAY_KEY, enabled);
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
            forwardTicks = 0;
            return;
        }

        boolean walkingForward = client.options.keyUp.isDown();
        boolean blocked = client.player.isCrouching()
                || client.player.isUsingItem()
                || client.player.isPassenger()
                || !client.player.onGround()
                || client.player.getFoodData().getFoodLevel() <= 6;
        if (sprintStopOnCollision() && client.player.horizontalCollision) {
            blocked = true;
        }

        boolean canSprint = walkingForward && !blocked;
        if (canSprint) {
            forwardTicks++;
        } else {
            forwardTicks = 0;
        }

        if (canSprint && forwardTicks >= sprintStartDelayTicks()) {
            client.options.keySprint.setDown(true);
            sprintKeyForced = true;
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

    private static void tickInventoryWalk(Minecraft client) {
        if (!isInventoryWalkEnabled() || client.player == null || client.level == null || client.options == null || client.screen == null) {
            releaseInventoryWalk(client);
            return;
        }

        if (client.screen instanceof ChatScreen || client.screen.isPauseScreen()) {
            releaseInventoryWalk(client);
            return;
        }

        KeyMapping.setAll();
        inventoryWalkForced = true;
    }

    private static void releaseInventoryWalk(Minecraft client) {
        if (!inventoryWalkForced || client.options == null) {
            inventoryWalkForced = false;
            return;
        }

        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        client.options.keySprint.setDown(false);
        client.options.keyShift.setDown(false);
        inventoryWalkForced = false;
    }

    private static void tickNoJumpDelay(Minecraft client) {
        if (!isNoJumpDelayEnabled() || client.player == null) {
            return;
        }

        ((LivingEntityJumpAccessor) client.player).almatyclient$setNoJumpDelay(0);
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

    private static boolean isEspLabelTarget(Entity entity) {
        if (entity instanceof ItemEntity) {
            return espItems();
        }
        return entity instanceof LivingEntity && isEspTarget(entity) && (espName() || espHealth());
    }

    private static void renderWorldOverlays(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || context.matrices() == null || context.consumers() == null) {
            return;
        }

        if (!isEspEnabled()) {
            return;
        }

        PoseStack matrices = context.matrices();
        MultiBufferSource consumers = context.consumers();
        Vec3 camera = context.gameRenderer().getMainCamera().position();

        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == client.player || !isEspTarget(entity)) {
                continue;
            }

            renderBoundingBox(matrices, consumers, entity, camera);
        }
    }

    private static void renderBoundingBox(PoseStack matrices, MultiBufferSource consumers, Entity entity, Vec3 camera) {
        AABB box = entity.getBoundingBox().move(-camera.x, -camera.y, -camera.z);
        int red = guiRed();
        int green = guiGreen();
        int blue = guiBlue();
        float width = ESP_LINE_WIDTH;
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

    public static Component espNameTag(Entity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || entity == client.player || !isEspEnabled() || !isEspLabelTarget(entity)) {
            return null;
        }

        String label = entityLabel(entity);
        return label.isBlank() ? null : Component.literal(label);
    }

    private static String entityLabel(Entity entity) {
        if (entity instanceof ItemEntity itemEntity) {
            return itemLabel(itemEntity);
        }

        if (!(entity instanceof LivingEntity living)) {
            return baseDisplayName(entity);
        }

        StringBuilder text = new StringBuilder();
        if (espName()) {
            text.append(baseDisplayName(entity));
        }
        if (espHealth()) {
            if (!text.isEmpty()) {
                text.append(" | ");
            }
            text.append("HP: ").append(Math.round(living.getHealth()));
        }
        return text.toString();
    }

    private static String itemLabel(ItemEntity itemEntity) {
        String name = itemEntity.getItem().getHoverName().getString();
        int count = itemEntity.getItem().getCount();
        if (count > 1) {
            return name + " x" + count;
        }
        return name;
    }

    private static String baseDisplayName(Entity entity) {
        if (entity instanceof Player player) {
            return player.getPlainTextName();
        }

        Component customName = entity.getCustomName();
        if (customName != null) {
            return stripEspLabel(customName.getString());
        }

        return entity.getType().getDescription().getString();
    }

    private static String stripEspLabel(String name) {
        String stripped = name;
        int separator = stripped.indexOf(" | HP:");
        if (separator >= 0) {
            stripped = stripped.substring(0, separator);
        } else if (stripped.startsWith("HP:")) {
            stripped = stripped.substring(3).trim();
            while (!stripped.isEmpty() && (Character.isDigit(stripped.charAt(0)) || stripped.charAt(0) == '.' || stripped.charAt(0) == ' ')) {
                stripped = stripped.substring(1).trim();
            }
        }
        return stripped.isBlank() ? "Entity" : stripped;
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

    private static int clampSprintDelay(int ticks) {
        return Math.max(0, Math.min(MAX_SPRINT_START_DELAY_TICKS, ticks));
    }
}
