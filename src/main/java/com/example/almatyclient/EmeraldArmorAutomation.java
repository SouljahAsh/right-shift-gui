package com.example.almatyclient;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

public final class EmeraldArmorAutomation {
    private static final String ENABLED_KEY = "feature.emeraldArmorAutoCraft";
    private static final int EMERALDS_PER_BUY = 64;
    private static final int ACTION_DELAY_TICKS = 8;
    private static final int GUI_TIMEOUT_TICKS = 100;
    private static final int BUY_RETRY_DELAY_TICKS = 12;
    private static final int BUY_CLICK_ATTEMPTS = 3;
    private static final int PATH_TIMEOUT_TICKS = 20 * 45;
    private static final int BLOCK_SEARCH_RADIUS = 32;
    private static final double INTERACT_RANGE_SQ = 4.5D * 4.5D;
    private static final int[][] ARMOR_PATTERNS = {
            {1, 2, 3, 4, 6},
            {1, 3, 4, 5, 6, 7, 8, 9},
            {1, 2, 3, 4, 6, 7, 9},
            {4, 6, 7, 9}
    };

    private static State state = State.IDLE;
    private static int delayTicks;
    private static int stateTicks;
    private static int emeraldsBeforeShop;
    private static int emeraldPurchaseAttempt;
    private static int armorPatternIndex;
    private static int craftGridIndex;
    private static int emeraldSourceSlot;
    private static BlockPos targetBlock;
    private static String lastError = "";

    private EmeraldArmorAutomation() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(EmeraldArmorAutomation::tick);
    }

    public static boolean isEnabled() {
        return AlmatyConfig.getBoolean(ENABLED_KEY, false);
    }

    public static void setEnabled(boolean enabled) {
        AlmatyConfig.setBoolean(ENABLED_KEY, enabled);
        if (enabled) {
            reset(State.CHECK_BALANCE);
        } else {
            reset(State.IDLE);
            stopBaritone();
        }
    }

    public static void toggle() {
        setEnabled(!isEnabled());
    }

    public static String stateText() {
        return state.title;
    }

    public static String lastError() {
        return lastError.isBlank() ? "None" : lastError;
    }

    private static void tick(Minecraft client) {
        if (!isEnabled()) {
            return;
        }
        if (client.player == null || client.level == null || client.gameMode == null) {
            return;
        }
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        stateTicks++;
        switch (state) {
            case CHECK_BALANCE -> checkBalance(client);
            case OPEN_SHOP -> openShop(client);
            case WAIT_SHOP -> waitShop(client);
            case CLICK_SHOP_GOLD -> clickShopGold(client);
            case WAIT_EMERALD_CATEGORY -> waitEmeraldCategory(client);
            case BUY_EMERALDS -> buyEmeralds(client);
            case WAIT_EMERALDS -> waitEmeralds(client);
            case FIND_CRAFTING_TABLE -> findCraftingTable(client);
            case PATH_TO_CRAFTING_TABLE -> pathToCraftingTable(client);
            case OPEN_CRAFTING_TABLE -> openBlock(client, State.WAIT_CRAFTING, Blocks.CRAFTING_TABLE);
            case WAIT_CRAFTING -> waitCrafting(client);
            case CRAFT_ARMOR -> craftArmorPiece(client);
            case PLACE_CRAFT_SLOT -> placeCraftSlot(client);
            case RETURN_EMERALDS -> returnEmeralds(client);
            case TAKE_CRAFT_RESULT -> takeCraftResult(client);
            case FIND_CHEST -> findChest(client);
            case PATH_TO_CHEST -> pathToChest(client);
            case OPEN_CHEST -> openBlock(client, State.WAIT_CHEST, Blocks.CHEST);
            case WAIT_CHEST -> waitChest(client);
            case DEPOSIT_ARMOR -> depositArmor(client);
            case LOOP_DELAY -> next(State.CHECK_BALANCE);
            case IDLE -> {
            }
        }
    }

    private static void checkBalance(Minecraft client) {
        int emeralds = client.player.getInventory().countItem(Items.EMERALD);
        if (emeralds >= EMERALDS_PER_BUY) {
            armorPatternIndex = 0;
            next(State.FIND_CRAFTING_TABLE);
            return;
        }
        if (!hasEmptyInventorySlot(client.player)) {
            disable("Inventory is full");
            return;
        }
        emeraldsBeforeShop = emeralds;
        next(State.OPEN_SHOP);
    }

    private static void openShop(Minecraft client) {
        client.player.connection.sendCommand("shop");
        next(State.WAIT_SHOP);
    }

    private static void waitShop(Minecraft client) {
        if (client.player.containerMenu instanceof ChestMenu) {
            next(State.CLICK_SHOP_GOLD);
            return;
        }
        timeout("Shop did not open");
    }

    private static void clickShopGold(Minecraft client) {
        if (!(client.player.containerMenu instanceof ChestMenu menu)) {
            timeout("Shop did not open");
            return;
        }
        int slot = findShopSlot(menu, Items.GOLD_INGOT);
        if (slot < 0) {
            timeout("Gold category was not found");
            return;
        }
        emeraldPurchaseAttempt = 0;
        click(client, slot, 0, ClickType.PICKUP);
        next(State.WAIT_EMERALD_CATEGORY);
    }

    private static void waitEmeraldCategory(Minecraft client) {
        if (!(client.player.containerMenu instanceof ChestMenu menu)) {
            timeout("Emerald category did not open");
            return;
        }
        if (findShopSlot(menu, Items.EMERALD) >= 0) {
            next(State.BUY_EMERALDS);
            return;
        }
        timeout("Emerald category did not open");
    }

    private static void buyEmeralds(Minecraft client) {
        if (!(client.player.containerMenu instanceof ChestMenu menu)) {
            timeout("Emerald category did not open");
            return;
        }
        int slot = findShopSlot(menu, Items.EMERALD);
        if (slot < 0) {
            disable("Emerald item was not found in shop");
            return;
        }
        if (emeraldPurchaseAttempt == 0) {
            click(client, slot, 1, ClickType.QUICK_MOVE);
        } else if (emeraldPurchaseAttempt == 1) {
            click(client, slot, 0, ClickType.QUICK_MOVE);
        } else {
            click(client, slot, 1, ClickType.PICKUP);
        }
        emeraldPurchaseAttempt++;
        next(State.WAIT_EMERALDS);
    }

    private static void waitEmeralds(Minecraft client) {
        int emeralds = client.player.getInventory().countItem(Items.EMERALD);
        if (emeralds > emeraldsBeforeShop) {
            closeContainer(client);
            armorPatternIndex = 0;
            next(State.FIND_CRAFTING_TABLE);
            return;
        }
        if (stateTicks > BUY_RETRY_DELAY_TICKS && emeraldPurchaseAttempt < BUY_CLICK_ATTEMPTS) {
            next(State.BUY_EMERALDS);
            return;
        }
        if (stateTicks > BUY_RETRY_DELAY_TICKS) {
            closeContainer(client);
            disable("Emerald balance did not change");
        }
    }

    private static void findCraftingTable(Minecraft client) {
        targetBlock = findNearestBlock(client, Blocks.CRAFTING_TABLE);
        if (targetBlock == null) {
            disable("Crafting table was not found");
            return;
        }
        if (!pathTo(targetBlock)) {
            disable("Baritone is not available");
            return;
        }
        next(State.PATH_TO_CRAFTING_TABLE);
    }

    private static void pathToCraftingTable(Minecraft client) {
        if (isNear(client.player, targetBlock)) {
            stopBaritone();
            next(State.OPEN_CRAFTING_TABLE);
            return;
        }
        timeout("Path to crafting table timed out", PATH_TIMEOUT_TICKS);
    }

    private static void waitCrafting(Minecraft client) {
        if (client.player.containerMenu instanceof CraftingMenu) {
            next(State.CRAFT_ARMOR);
            return;
        }
        timeout("Crafting table did not open");
    }

    private static void craftArmorPiece(Minecraft client) {
        if (!(client.player.containerMenu instanceof CraftingMenu menu)) {
            next(State.WAIT_CRAFTING);
            return;
        }
        if (armorPatternIndex >= ARMOR_PATTERNS.length) {
            closeContainer(client);
            next(State.FIND_CHEST);
            return;
        }
        if (client.player.getInventory().countItem(Items.EMERALD) < ARMOR_PATTERNS[armorPatternIndex].length) {
            closeContainer(client);
            next(State.CHECK_BALANCE);
            return;
        }
        int dirtySlot = findDirtyCraftingSlot(menu);
        if (dirtySlot >= 0) {
            click(client, dirtySlot, 0, ClickType.QUICK_MOVE);
            waitAction();
            return;
        }
        emeraldSourceSlot = findSlot(menu, Items.EMERALD, 10, menu.slots.size());
        if (emeraldSourceSlot < 0) {
            closeContainer(client);
            next(State.CHECK_BALANCE);
            return;
        }
        craftGridIndex = 0;
        click(client, emeraldSourceSlot, 0, ClickType.PICKUP);
        next(State.PLACE_CRAFT_SLOT);
    }

    private static void placeCraftSlot(Minecraft client) {
        if (!(client.player.containerMenu instanceof CraftingMenu)) {
            next(State.WAIT_CRAFTING);
            return;
        }
        int[] pattern = ARMOR_PATTERNS[armorPatternIndex];
        if (craftGridIndex >= pattern.length) {
            next(State.RETURN_EMERALDS);
            return;
        }
        click(client, pattern[craftGridIndex], 1, ClickType.PICKUP);
        craftGridIndex++;
        waitAction();
    }

    private static void returnEmeralds(Minecraft client) {
        if (!(client.player.containerMenu instanceof CraftingMenu menu)) {
            next(State.WAIT_CRAFTING);
            return;
        }
        if (emeraldSourceSlot < 10 || emeraldSourceSlot >= menu.slots.size()) {
            emeraldSourceSlot = firstEmptyPlayerMenuSlot(menu);
        }
        if (emeraldSourceSlot < 0) {
            disable("Could not return emerald stack to inventory");
            return;
        }
        click(client, emeraldSourceSlot, 0, ClickType.PICKUP);
        next(State.TAKE_CRAFT_RESULT);
    }

    private static void takeCraftResult(Minecraft client) {
        AbstractContainerMenu menu = client.player.containerMenu;
        ItemStack result = menu.getSlot(0).getItem();
        if (result.isEmpty()) {
            timeout("Emerald armor recipe did not produce a result");
            return;
        }
        click(client, 0, 0, ClickType.QUICK_MOVE);
        armorPatternIndex++;
        next(State.CRAFT_ARMOR);
    }

    private static void findChest(Minecraft client) {
        targetBlock = findNearestBlock(client, Blocks.CHEST);
        if (targetBlock == null) {
            disable("Chest was not found");
            return;
        }
        if (!pathTo(targetBlock)) {
            disable("Baritone is not available");
            return;
        }
        next(State.PATH_TO_CHEST);
    }

    private static void pathToChest(Minecraft client) {
        if (isNear(client.player, targetBlock)) {
            stopBaritone();
            next(State.OPEN_CHEST);
            return;
        }
        timeout("Path to chest timed out", PATH_TIMEOUT_TICKS);
    }

    private static void waitChest(Minecraft client) {
        if (client.player.containerMenu instanceof ChestMenu) {
            next(State.DEPOSIT_ARMOR);
            return;
        }
        timeout("Chest did not open");
    }

    private static void depositArmor(Minecraft client) {
        if (!(client.player.containerMenu instanceof ChestMenu menu)) {
            next(State.WAIT_CHEST);
            return;
        }
        int playerInventoryStart = menu.getRowCount() * 9;
        for (int slot = playerInventoryStart; slot < menu.slots.size(); slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (isEmeraldArmor(stack)) {
                click(client, slot, 0, ClickType.QUICK_MOVE);
                waitAction();
                return;
            }
        }
        closeContainer(client);
        next(State.LOOP_DELAY);
    }

    private static void openBlock(Minecraft client, State nextState, Block expectedBlock) {
        if (targetBlock == null || !client.level.getBlockState(targetBlock).is(expectedBlock)) {
            disable("Target block changed");
            return;
        }
        if (!isNear(client.player, targetBlock)) {
            next(expectedBlock == Blocks.CRAFTING_TABLE ? State.PATH_TO_CRAFTING_TABLE : State.PATH_TO_CHEST);
            return;
        }
        Vec3 hit = new Vec3(targetBlock.getX() + 0.5D, targetBlock.getY() + 0.5D, targetBlock.getZ() + 0.5D);
        BlockHitResult hitResult = new BlockHitResult(hit, Direction.UP, targetBlock, false);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hitResult);
        client.player.swing(InteractionHand.MAIN_HAND);
        next(nextState);
    }

    private static int findDirtyCraftingSlot(AbstractContainerMenu menu) {
        for (int slot = 1; slot <= 9; slot++) {
            if (!menu.getSlot(slot).getItem().isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static int firstEmptyPlayerMenuSlot(AbstractContainerMenu menu) {
        for (int slot = 10; slot < menu.slots.size(); slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static int findSlot(AbstractContainerMenu menu, net.minecraft.world.item.Item item, int from, int to) {
        int end = Math.min(to, menu.slots.size());
        for (int slot = Math.max(0, from); slot < end; slot++) {
            if (menu.getSlot(slot).getItem().is(item)) {
                return slot;
            }
        }
        return -1;
    }

    private static int findShopSlot(ChestMenu menu, net.minecraft.world.item.Item item) {
        return findSlot(menu, item, 0, menu.getRowCount() * 9);
    }

    private static void click(Minecraft client, int slot, int button, ClickType clickType) {
        client.gameMode.handleInventoryMouseClick(client.player.containerMenu.containerId, slot, button, clickType, client.player);
    }

    private static void closeContainer(Minecraft client) {
        if (client.player != null) {
            client.player.closeContainer();
        }
        client.setScreen(null);
    }

    private static BlockPos findNearestBlock(Minecraft client, Block block) {
        BlockPos origin = client.player.blockPosition();
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (int y = -6; y <= 6; y++) {
            for (int x = -BLOCK_SEARCH_RADIUS; x <= BLOCK_SEARCH_RADIUS; x++) {
                for (int z = -BLOCK_SEARCH_RADIUS; z <= BLOCK_SEARCH_RADIUS; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (!client.level.getBlockState(pos).is(block)) {
                        continue;
                    }
                    double distance = origin.distSqr(pos);
                    if (distance < nearestDistance) {
                        nearest = pos;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private static boolean hasEmptyInventorySlot(LocalPlayer player) {
        return player.getInventory().getFreeSlot() >= 0;
    }

    private static boolean isEmeraldArmor(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String name = stack.getHoverName().getString().toLowerCase();
        boolean emeraldNamed = name.contains("emerald") || name.contains("изумруд");
        boolean armorNamed = name.contains("helmet") || name.contains("шлем")
                || name.contains("chestplate") || name.contains("нагруд")
                || name.contains("leggings") || name.contains("понож")
                || name.contains("boots") || name.contains("ботин");
        return emeraldNamed && armorNamed;
    }

    private static boolean isNear(LocalPlayer player, BlockPos pos) {
        if (pos == null) {
            return false;
        }
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= INTERACT_RANGE_SQ;
    }

    private static boolean pathTo(BlockPos pos) {
        try {
            Object provider = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object process = baritone.getClass().getMethod("getGetToBlockProcess").invoke(baritone);
            Method pathTo = process.getClass().getMethod("pathTo", BlockPos.class);
            pathTo.invoke(process, pos);
            return true;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static void stopBaritone() {
        try {
            Object provider = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            try {
                baritone.getClass().getMethod("cancelEverything").invoke(baritone);
            } catch (NoSuchMethodException ignored) {
                Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);
                pathingBehavior.getClass().getMethod("cancelEverything").invoke(pathingBehavior);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void next(State nextState) {
        state = nextState;
        stateTicks = 0;
        waitAction();
    }

    private static void reset(State nextState) {
        state = nextState;
        delayTicks = 0;
        stateTicks = 0;
        emeraldsBeforeShop = 0;
        emeraldPurchaseAttempt = 0;
        armorPatternIndex = 0;
        craftGridIndex = 0;
        emeraldSourceSlot = -1;
        targetBlock = null;
        if (nextState != State.IDLE) {
            lastError = "";
        }
    }

    private static void waitAction() {
        delayTicks = ACTION_DELAY_TICKS;
    }

    private static void timeout(String message) {
        timeout(message, GUI_TIMEOUT_TICKS);
    }

    private static void timeout(String message, int ticks) {
        if (stateTicks > ticks) {
            disable(message);
        }
    }

    private static void disable(String reason) {
        lastError = reason;
        setEnabled(false);
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("[Almaty] Emerald AutoCraft disabled: " + reason)
                    .withStyle(ChatFormatting.RED), false);
        }
    }

    private enum State {
        IDLE("Idle"),
        CHECK_BALANCE("Checking emeralds"),
        OPEN_SHOP("Opening /shop"),
        WAIT_SHOP("Waiting shop"),
        CLICK_SHOP_GOLD("Opening gold category"),
        WAIT_EMERALD_CATEGORY("Waiting emerald category"),
        BUY_EMERALDS("Buying emeralds"),
        WAIT_EMERALDS("Waiting emeralds"),
        FIND_CRAFTING_TABLE("Finding crafting table"),
        PATH_TO_CRAFTING_TABLE("Walking to crafting table"),
        OPEN_CRAFTING_TABLE("Opening crafting table"),
        WAIT_CRAFTING("Waiting crafting table"),
        CRAFT_ARMOR("Crafting armor"),
        PLACE_CRAFT_SLOT("Placing recipe"),
        RETURN_EMERALDS("Returning emeralds"),
        TAKE_CRAFT_RESULT("Taking craft result"),
        FIND_CHEST("Finding chest"),
        PATH_TO_CHEST("Walking to chest"),
        OPEN_CHEST("Opening chest"),
        WAIT_CHEST("Waiting chest"),
        DEPOSIT_ARMOR("Depositing armor"),
        LOOP_DELAY("Restarting");

        private final String title;

        State(String title) {
            this.title = title;
        }
    }
}
