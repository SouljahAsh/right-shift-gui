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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

public final class EmeraldArmorAutomation {
    private static final String ENABLED_KEY = "feature.emeraldArmorAutoCraft";
    private static final String TARGET_KEY = "emeraldAutoCraft.target";
    private static final int EMERALDS_PER_BUY = 64;
    private static final int ACTION_DELAY_TICKS = 8;
    private static final int CRAFT_DELAY_TICKS = 1;
    private static final int GUI_TIMEOUT_TICKS = 100;
    private static final int BUY_RETRY_DELAY_TICKS = 12;
    private static final int BUY_CLICK_ATTEMPTS = 6;
    private static final int PATH_TIMEOUT_TICKS = 20 * 45;
    private static final int BLOCK_SEARCH_RADIUS = 32;
    private static final double INTERACT_RANGE_SQ = 4.5D * 4.5D;
    private static final RecipeSpec[] ARMOR_RECIPES = {
            recipe(group(Items.EMERALD, 1, 2, 3, 4, 6)),
            recipe(group(Items.EMERALD, 1, 3, 4, 5, 6, 7, 8, 9)),
            recipe(group(Items.EMERALD, 1, 2, 3, 4, 6, 7, 9)),
            recipe(group(Items.EMERALD, 4, 6, 7, 9))
    };
    private static final RecipeSpec SWORD_RECIPE = recipe(
            group(Items.EMERALD, 2, 5),
            group(Items.STICK, 8)
    );

    private static State state = State.IDLE;
    private static int delayTicks;
    private static int stateTicks;
    private static int emeraldsBeforeShop;
    private static int emeraldPurchaseAttempt;
    private static int armorPatternIndex;
    private static int recipeGroupIndex;
    private static int craftGridIndex;
    private static int ingredientSourceSlot;
    private static RecipeSpec currentRecipe;
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

    public static String targetText() {
        return target().title;
    }

    public static void cycleTarget() {
        setTarget(target() == CraftTarget.ARMOR ? CraftTarget.SWORD : CraftTarget.ARMOR);
    }

    private static CraftTarget target() {
        String raw = AlmatyConfig.getString(TARGET_KEY, CraftTarget.ARMOR.name());
        try {
            return CraftTarget.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return CraftTarget.ARMOR;
        }
    }

    private static void setTarget(CraftTarget target) {
        AlmatyConfig.setString(TARGET_KEY, target.name());
        if (isEnabled()) {
            reset(State.CHECK_BALANCE);
        }
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
            case PICKUP_CRAFT_ITEM -> pickupCraftItem(client);
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
        RecipeSelection selection = nextCraftableRecipe(client.player, 0);
        if (selection != null) {
            armorPatternIndex = selection.armorIndex;
            currentRecipe = selection.recipe;
            next(State.FIND_CRAFTING_TABLE);
            return;
        }
        if (target() == CraftTarget.SWORD && client.player.getInventory().countItem(Items.STICK) <= 0) {
            disable("Sticks are required for sword crafting");
            return;
        }
        if (!hasEmptyInventorySlot(client.player)) {
            disable("Inventory is full");
            return;
        }
        emeraldsBeforeShop = client.player.getInventory().countItem(Items.EMERALD);
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
        if (findShopEmeraldSlot(menu) >= 0) {
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
        int slot = findShopEmeraldSlot(menu);
        if (slot < 0) {
            disable("Emerald item was not found in shop");
            return;
        }
        if (emeraldPurchaseAttempt == 0) {
            click(client, slot, 1, ClickType.QUICK_MOVE);
        } else if (emeraldPurchaseAttempt == 1) {
            click(client, slot, 0, ClickType.QUICK_MOVE);
        } else if (emeraldPurchaseAttempt == 2) {
            click(client, slot, 1, ClickType.PICKUP);
        } else if (emeraldPurchaseAttempt == 3) {
            click(client, slot, 0, ClickType.PICKUP);
        } else if (emeraldPurchaseAttempt == 4) {
            click(client, slot, 1, ClickType.PICKUP_ALL);
        } else {
            click(client, slot, 0, ClickType.PICKUP_ALL);
        }
        emeraldPurchaseAttempt++;
        next(State.WAIT_EMERALDS);
    }

    private static void waitEmeralds(Minecraft client) {
        int emeralds = client.player.getInventory().countItem(Items.EMERALD);
        if (emeralds > emeraldsBeforeShop) {
            closeContainer(client);
            armorPatternIndex = 0;
            currentRecipe = null;
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
        RecipeSelection selection = nextCraftableRecipe(client.player, armorPatternIndex);
        if (selection == null) {
            closeContainer(client);
            if (target() == CraftTarget.SWORD && client.player.getInventory().countItem(Items.STICK) <= 0 && !hasCraftedTargetInInventory(client.player)) {
                disable("Sticks are required for sword crafting");
            } else {
                next(hasCraftedTargetInInventory(client.player) ? State.FIND_CHEST : State.CHECK_BALANCE);
            }
            return;
        }
        armorPatternIndex = selection.armorIndex;
        currentRecipe = selection.recipe;

        int dirtySlot = findDirtyCraftingSlot(menu);
        if (dirtySlot >= 0) {
            click(client, dirtySlot, 0, ClickType.QUICK_MOVE);
            waitCraftAction();
            return;
        }
        recipeGroupIndex = 0;
        craftGridIndex = 0;
        nextFast(State.PICKUP_CRAFT_ITEM);
    }

    private static void pickupCraftItem(Minecraft client) {
        if (!(client.player.containerMenu instanceof CraftingMenu menu) || currentRecipe == null) {
            next(State.WAIT_CRAFTING);
            return;
        }
        if (recipeGroupIndex >= currentRecipe.groups.length) {
            nextFast(State.TAKE_CRAFT_RESULT);
            return;
        }

        IngredientGroup group = currentRecipe.groups[recipeGroupIndex];
        ingredientSourceSlot = findSlot(menu, group.item, 10, menu.slots.size());
        if (ingredientSourceSlot < 0) {
            closeContainer(client);
            next(State.CHECK_BALANCE);
            return;
        }
        craftGridIndex = 0;
        click(client, ingredientSourceSlot, 0, ClickType.PICKUP);
        nextFast(State.PLACE_CRAFT_SLOT);
    }

    private static void placeCraftSlot(Minecraft client) {
        if (!(client.player.containerMenu instanceof CraftingMenu) || currentRecipe == null) {
            next(State.WAIT_CRAFTING);
            return;
        }
        IngredientGroup group = currentRecipe.groups[recipeGroupIndex];
        if (craftGridIndex >= group.slots.length) {
            nextFast(State.RETURN_EMERALDS);
            return;
        }
        click(client, group.slots[craftGridIndex], 1, ClickType.PICKUP);
        craftGridIndex++;
        waitCraftAction();
    }

    private static void returnEmeralds(Minecraft client) {
        if (!(client.player.containerMenu instanceof CraftingMenu menu)) {
            next(State.WAIT_CRAFTING);
            return;
        }
        if (ingredientSourceSlot < 10 || ingredientSourceSlot >= menu.slots.size()) {
            ingredientSourceSlot = firstEmptyPlayerMenuSlot(menu);
        }
        if (ingredientSourceSlot < 0) {
            disable("Could not return ingredient stack to inventory");
            return;
        }
        click(client, ingredientSourceSlot, 0, ClickType.PICKUP);
        recipeGroupIndex++;
        nextFast(recipeGroupIndex >= currentRecipe.groups.length ? State.TAKE_CRAFT_RESULT : State.PICKUP_CRAFT_ITEM);
    }

    private static void takeCraftResult(Minecraft client) {
        AbstractContainerMenu menu = client.player.containerMenu;
        ItemStack result = menu.getSlot(0).getItem();
        if (result.isEmpty()) {
            timeout("Emerald armor recipe did not produce a result");
            return;
        }
        click(client, 0, 0, ClickType.QUICK_MOVE);
        if (target() == CraftTarget.ARMOR) {
            armorPatternIndex++;
        }
        currentRecipe = null;
        nextFast(State.CRAFT_ARMOR);
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
            if (isCraftedTarget(stack)) {
                click(client, slot, 0, ClickType.QUICK_MOVE);
                waitCraftAction();
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
        nextFast(nextState);
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

    private static RecipeSelection nextCraftableRecipe(LocalPlayer player, int fromArmorIndex) {
        if (target() == CraftTarget.SWORD) {
            return canCraftRecipe(player, SWORD_RECIPE) ? new RecipeSelection(0, SWORD_RECIPE) : null;
        }

        for (int index = Math.max(0, fromArmorIndex); index < ARMOR_RECIPES.length; index++) {
            if (canCraftRecipe(player, ARMOR_RECIPES[index])) {
                return new RecipeSelection(index, ARMOR_RECIPES[index]);
            }
        }
        return null;
    }

    private static boolean canCraftRecipe(LocalPlayer player, RecipeSpec recipe) {
        for (IngredientGroup group : recipe.groups) {
            if (player.getInventory().countItem(group.item) < group.slots.length) {
                return false;
            }
        }
        return true;
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

    private static int findShopEmeraldSlot(ChestMenu menu) {
        int slot = findShopSlot(menu, Items.EMERALD);
        if (slot >= 0) {
            return slot;
        }

        int end = Math.min(menu.getRowCount() * 9, menu.slots.size());
        for (int index = 0; index < end; index++) {
            ItemStack stack = menu.getSlot(index).getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String name = stack.getHoverName().getString().toLowerCase();
            if (name.contains("emerald") || name.contains("изумруд")) {
                return index;
            }
        }
        return -1;
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

    private static boolean hasCraftedTargetInInventory(LocalPlayer player) {
        for (int slot = 0; slot < player.inventoryMenu.slots.size(); slot++) {
            if (isCraftedTarget(player.inventoryMenu.getSlot(slot).getItem())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCraftedTarget(ItemStack stack) {
        return target() == CraftTarget.SWORD ? isEmeraldSword(stack) : isEmeraldArmor(stack);
    }

    private static boolean isEmeraldSword(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String name = stack.getHoverName().getString().toLowerCase();
        boolean emeraldNamed = name.contains("emerald") || name.contains("РёР·СѓРјСЂСѓРґ");
        boolean swordNamed = name.contains("sword") || name.contains("РјРµС‡");
        return emeraldNamed && swordNamed;
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
            Object process = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
            Class<?> goalType = Class.forName("baritone.api.pathing.goals.Goal");
            Object goal = Class.forName("baritone.api.pathing.goals.GoalGetToBlock")
                    .getConstructor(BlockPos.class)
                    .newInstance(pos);
            Method setGoalAndPath = process.getClass().getMethod("setGoalAndPath", goalType);
            setGoalAndPath.invoke(process, goal);
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

    private static void nextFast(State nextState) {
        state = nextState;
        stateTicks = 0;
        waitCraftAction();
    }

    private static void reset(State nextState) {
        state = nextState;
        delayTicks = 0;
        stateTicks = 0;
        emeraldsBeforeShop = 0;
        emeraldPurchaseAttempt = 0;
        armorPatternIndex = 0;
        recipeGroupIndex = 0;
        craftGridIndex = 0;
        ingredientSourceSlot = -1;
        currentRecipe = null;
        targetBlock = null;
        if (nextState != State.IDLE) {
            lastError = "";
        }
    }

    private static void waitAction() {
        delayTicks = ACTION_DELAY_TICKS;
    }

    private static void waitCraftAction() {
        delayTicks = CRAFT_DELAY_TICKS;
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

    private static RecipeSpec recipe(IngredientGroup... groups) {
        return new RecipeSpec(groups);
    }

    private static IngredientGroup group(Item item, int... slots) {
        return new IngredientGroup(item, slots);
    }

    private enum CraftTarget {
        ARMOR("Armor"),
        SWORD("Sword");

        private final String title;

        CraftTarget(String title) {
            this.title = title;
        }
    }

    private record RecipeSpec(IngredientGroup[] groups) {
    }

    private record IngredientGroup(Item item, int[] slots) {
    }

    private record RecipeSelection(int armorIndex, RecipeSpec recipe) {
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
        CRAFT_ARMOR("Crafting item"),
        PICKUP_CRAFT_ITEM("Picking ingredient"),
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
