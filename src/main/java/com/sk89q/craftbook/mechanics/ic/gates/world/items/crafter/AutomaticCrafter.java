package com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.ic.*;
import com.sk89q.craftbook.mechanics.pipe.PipePutEvent;
import com.sk89q.craftbook.mechanics.pipe.PipeRequestEvent;
import com.sk89q.craftbook.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AutomaticCrafter extends AbstractSelfTriggeredIC implements PipeInputIC {

    private static final ItemStack AIR_STACK = new ItemStack(Material.AIR);

    private static boolean hasWarned = false;
    private static boolean hasWarnedNoResult = false;

    static {
        if (Material.values().length > 4096)
            throw new IllegalStateException("There are more than 4K materials, which exceeds our expectation - cannot bit-pack the matrix into two longs without a loss of information!");
    }

    private Block cachedDispenserOrDropperBlock;
    private Inventory cachedDispenserOrDropperInventory;
    private Block cachedOutputBlock;

    private CachedRecipe cachedRecipe;
    private long cachedRecipeMatrixMsb;
    private long cachedRecipeMatrixLsb;
    private TriState wasMatrixInvalid = TriState.NULL;

    public AutomaticCrafter(Server server, ChangedSign block, ICFactory factory) {

        super(server, block, factory);
    }

    @Override
    public String getTitle() {

        return "Automatic Crafter";
    }

    @Override
    public String getSignTitle() {

        return "AUTO CRAFT";
    }

    @Override
    public void trigger(ChipState chip) {
        if (chip.getInput(0))
            chip.setOutput(0, doStuff());
    }

    @Override
    public void think(ChipState state) {
        state.setOutput(0, doStuff());
    }

    private long getSlotTypeOrdinal(int slot) {
        var item = cachedDispenserOrDropperInventory.getItem(slot);

        if (item == null)
            return Material.AIR.ordinal();

        return item.getType().ordinal();
    }

    private long computeMatrixMsb() {
        return (
          getSlotTypeOrdinal(5)
            | (getSlotTypeOrdinal(6) << 12)
            | (getSlotTypeOrdinal(7) << (12 * 2))
            | (getSlotTypeOrdinal(8) << (12 * 3))
        );
    }

    private long computeMatrixLsb() {
        return (
          getSlotTypeOrdinal(0)
            | (getSlotTypeOrdinal(1) << 12)
            | (getSlotTypeOrdinal(2) << (12 * 2))
            | (getSlotTypeOrdinal(3) << (12 * 3))
            | (getSlotTypeOrdinal(4) << (12 * 4))
        );
    }

    private void computeRecipe() {
        // Only called from craft/collect - caches already setup

        var priorRecipeMatrixMsb = this.cachedRecipeMatrixMsb;
        var priorRecipeMatrixLsb = this.cachedRecipeMatrixLsb;

        this.cachedRecipeMatrixMsb = computeMatrixMsb();
        this.cachedRecipeMatrixLsb = computeMatrixLsb();

        // Do not retry malformed matrix-constellations over and over again - remember the failure and only retry after a reconfiguration.
        if (wasMatrixInvalid == TriState.TRUE && priorRecipeMatrixMsb == cachedRecipeMatrixMsb && priorRecipeMatrixLsb == cachedRecipeMatrixLsb)
            return;

        try {
            for (var cachedRecipe : RecipeCache.getRecipes()) {
                if (!isValidRecipe(cachedRecipe))
                    continue;

                this.cachedRecipe = cachedRecipe;
                this.wasMatrixInvalid = TriState.FALSE;
                return;
            }
        } catch (Exception e) {
            CraftBookBukkitUtil.printStacktrace(e);
            // I'm not quite sure why we need this, but let's keep it.
            cachedDispenserOrDropperInventory.setContents(cachedDispenserOrDropperInventory.getContents());
        }

        this.wasMatrixInvalid = TriState.TRUE;
    }

    public boolean craft() {
        // Only called from itself or doStuff - caches already setup

        ItemStack[] contents = cachedDispenserOrDropperInventory.getContents();

        for (ItemStack it : contents) {
            if (!ItemUtil.isStackValid(it))
                continue;
            if (it.getAmount() < 2) return false;
        }

        if (cachedRecipe == null) {
            computeRecipe();
        }

        if (cachedRecipe == null) return false;

        var doesMatrixEqual = cachedRecipeMatrixMsb == computeMatrixMsb() && cachedRecipeMatrixLsb == computeMatrixLsb();

        if (!doesMatrixEqual && !isValidRecipe(cachedRecipe)) {
            cachedRecipe = null;
            return craft();
        }

        ItemStack result = cachedRecipe.getHandle().getResult();

        if(!ItemUtil.isStackValid(result)) {
            if (!hasWarnedNoResult) {
                CraftBookPlugin.inst().getLogger().warning("An Automatic Crafter IC had a valid recipe, but there was no result! This means Bukkit"
                        + " has an invalid recipe! Result: " + result);
                hasWarnedNoResult = true;
            }
            return false;
        }

        List<ItemStack> items = new ArrayList<>();

        ItemStack[] replace = new ItemStack[9];

        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == null) {
                continue;
            }

            ItemStack replaceItem = replace[i] = new ItemStack(contents[i]);

            if (replaceItem.getType() == Material.WATER_BUCKET || replaceItem.getType() == Material.LAVA_BUCKET || replaceItem.getType() == Material.MILK_BUCKET)
                items.add(new ItemStack(Material.BUCKET, 1));

            if (replaceItem.getType() == Material.HONEY_BOTTLE)
                items.add(new ItemStack(Material.GLASS_BOTTLE, 1));

            replaceItem.setAmount(replaceItem.getAmount() - 1);
        }

        cachedDispenserOrDropperInventory.clear();

        CraftBookPlugin.logDebugMessage("AutoCrafter is dispensing a " + result.getType().name() + " with data: " + result.getDurability() + " and amount: " + result.getAmount(), "ic-mc1219");

        items.add(result);

        PipeRequestEvent event = new PipeRequestEvent(cachedOutputBlock, items, cachedDispenserOrDropperBlock);
        Bukkit.getPluginManager().callEvent(event);

        items = event.getItems();

        List<ItemStack> leftovers = new ArrayList<>();

        // The Pipe will have put remainders back into the dispenser/dropper, so let's also collect them
        // into the leftovers, as to not override them when setting back the replace-matrix afterward.
        for (ItemStack item : cachedDispenserOrDropperInventory.getContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0)
                leftovers.add(item);
        }

        cachedDispenserOrDropperInventory.setContents(replace);

        if(!items.isEmpty()) {
            if (cachedOutputBlock.getState() instanceof InventoryHolder inventoryHolder) {
                var outputInventory = inventoryHolder.getInventory();

                for (ItemStack stack : items)
                    leftovers.addAll(outputInventory.addItem(stack).values());
            } else {
                leftovers.addAll(items);
            }
        }

        if (!leftovers.isEmpty()) {
            var dispenserWorld = cachedDispenserOrDropperBlock.getWorld();
            var dispenserLocation = cachedDispenserOrDropperBlock.getLocation();

            for (ItemStack leftover : leftovers)
                dispenserWorld.dropItemNaturally(dispenserLocation, leftover);

            leftovers.clear();
        }

        return true;
    }

    private boolean collect() {
        // Only called from doStuff - caches already setup

        if (cachedRecipe == null) {
            computeRecipe();
            if (cachedRecipe == null) {
                return false; // Only collect items if valid recipe.
            }
        }

        var matrixContents = cachedDispenserOrDropperInventory.getContents();

        for (var itemEntity : ItemUtil.getItemsAtBlock(getSign().getBlock())) {
            if (itemEntity.isDead() || !itemEntity.isValid())
                continue;

            var itemStack = itemEntity.getItemStack();

            var remainder = distributeToMakeEvenAndGetRemainder(itemStack, matrixContents);

            itemStack.setAmount(remainder);
            itemEntity.setItemStack(itemStack);

            if (remainder <= 0)
                itemEntity.remove();
        }

        return true;
    }

    private boolean updateCachesAndGetIfIsMalformed() {
        if (cachedDispenserOrDropperBlock == null)
            cachedDispenserOrDropperBlock = getBackBlock().getRelative(0, 1, 0);

        if (cachedDispenserOrDropperBlock.getType() != Material.DISPENSER && cachedDispenserOrDropperBlock.getType() != Material.DROPPER) {
            cachedDispenserOrDropperInventory = null;
            cachedOutputBlock = null;
            return true;
        }

        if (cachedDispenserOrDropperInventory == null || cachedOutputBlock == null) {
            BlockState state = cachedDispenserOrDropperBlock.getState();
            cachedDispenserOrDropperInventory = ((InventoryHolder) state).getInventory();

            BlockFace facing = ((Directional) cachedDispenserOrDropperBlock.getBlockData()).getFacing();
            cachedOutputBlock = cachedDispenserOrDropperBlock.getRelative(facing);
        }

        return false;
    }

    private boolean doStuff() {
        if (updateCachesAndGetIfIsMalformed())
            return false;

        boolean ret = false;

        ret |= collect();
        ret |= craft();

        return ret;
    }

    private boolean isValidRecipe(CachedRecipe recipe) {
        if (recipe instanceof CachedShapedRecipe shape && (cachedRecipe == null || cachedRecipe instanceof CachedShapedRecipe)) {
            int columnIndex = -1, rowIndex = 0;
            int validRecipeItems = 0;

            for (int slot = 0; slot < 9; slot++) {
                ItemStack matrixItem = cachedDispenserOrDropperInventory.getItem(slot);

                if (++columnIndex >= 3) {
                    columnIndex = 0;

                    if (++rowIndex >= 3)
                        break;
                }

                String shapeRow = shape.getShapeRow(rowIndex);

                RecipeChoice ingredient = null;
                char ingredientChar = ' ';

                if (columnIndex < shapeRow.length())
                    ingredientChar = shapeRow.charAt(columnIndex);

                if (ingredientChar != ' ')
                    ingredient = shape.getIngredient(ingredientChar);

                if (ingredient == null) {
                    if (matrixItem != null && matrixItem.getType() != Material.AIR)
                        return false;

                    continue;
                }

                ++validRecipeItems;

                if (matrixItem == null)
                    matrixItem = AIR_STACK;

                if (!ingredient.test(matrixItem))
                    return false;
            }

            if (validRecipeItems == 0) {
                if (!hasWarned) {
                    CraftBookPlugin.logger().warning("Found invalid recipe! This is an issue with Bukkit/Spigot/etc, please report to them. All recipe ingredients are air. Recipe result: " + shape.cachedResult);
                    hasWarned = true;
                }
                return false;
            }

            return true;
        }

        if (recipe instanceof CachedShapelessRecipe shape && (cachedRecipe == null || cachedRecipe instanceof CachedShapelessRecipe)) {
            if (shape.cachedKey.equals("shulker_box_coloring"))
                return false;

            var remainingIngredients = new ArrayList<>(shape.cachedIngredientList);

            // If it's empty already, something is wrong with the recipe.
            if (remainingIngredients.isEmpty())
                return false;

            for (ItemStack matrixItem : cachedDispenserOrDropperInventory.getContents()) {
                if (!ItemUtil.isStackValid(matrixItem))
                    continue;

                // No more required ingredients left, but there are still additional items in the crafting-matrix => mismatch.
                if (remainingIngredients.isEmpty())
                    return false;

                for (var iterator = remainingIngredients.iterator(); iterator.hasNext();) {
                    var requiredIngredient = iterator.next();

                    if (requiredIngredient.test(matrixItem)) {
                        iterator.remove();
                        break;
                    }
                }
            }

            return remainingIngredients.isEmpty();
        }

        return false;
    }

    public static class Factory extends AbstractICFactory {

        public Factory(Server server) {

            super(server);
        }

        @Override
        public IC create(ChangedSign sign) {

            return new AutomaticCrafter(getServer(), sign, this);
        }

        @Override
        public String getShortDescription() {

            return "Auto-crafts recipes in the above dispenser/dropper.";
        }

        @Override
        public String[] getLineHelp() {

            return new String[] {null, null};
        }
    }

    @Override
    public void onPipeTransfer(PipePutEvent event) {
        if (updateCachesAndGetIfIsMalformed())
            return;

        var matrixContents = cachedDispenserOrDropperInventory.getContents();
        var remainders = new ArrayList<>(event.getItems());

        for (var itemIndex = remainders.size() - 1; itemIndex >= 0; --itemIndex) {
            var itemToPut = remainders.get(itemIndex);

            if (!ItemUtil.isStackValid(itemToPut)) {
                remainders.remove(itemIndex);
                continue;
            }

            var remainder = distributeToMakeEvenAndGetRemainder(itemToPut, matrixContents);

            itemToPut.setAmount(remainder);

            if (remainder <= 0)
                remainders.remove(itemIndex);
        }

        event.getItems().clear();
        event.setItems(remainders);
    }

    @Override
    public void unload() {
        super.unload();

        cachedDispenserOrDropperBlock = null;
        cachedDispenserOrDropperInventory = null;
        cachedOutputBlock = null;
        cachedRecipe = null;
    }

    private int distributeToMakeEvenAndGetRemainder(ItemStack source, ItemStack[] destinations) {
        var remainingAmount = source.getAmount();

        while (remainingAmount > 0) {
            var destination = getSmallestSimilarStack(destinations, source);

            if (destination == null)
                return remainingAmount;

            if (destination.getAmount() >= 64)
                return remainingAmount;

            destination.setAmount(destination.getAmount() + 1);
            --remainingAmount;
        }

        return remainingAmount;
    }

    private ItemStack getSmallestSimilarStack(ItemStack[] candidates, @NotNull ItemStack similarItem) {
        ItemStack smallest = null;

        for (ItemStack candidate : candidates) {
            if (!ItemUtil.isStackValid(candidate) || !similarItem.isSimilar(candidate))
                continue;

            if (smallest == null || candidate.getAmount() < smallest.getAmount())
                smallest = candidate;
        }

        return smallest;
    }
}
