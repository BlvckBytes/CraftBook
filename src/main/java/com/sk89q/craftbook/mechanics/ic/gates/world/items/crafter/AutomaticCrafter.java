package com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.ic.*;
import com.sk89q.craftbook.util.InventoryUtil;
import com.sk89q.craftbook.mechanics.pipe.PipePutEvent;
import com.sk89q.craftbook.util.ItemUtil;
import com.sk89q.craftbook.util.LiveAddOnlyInventory;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.*;

import java.util.*;

public class AutomaticCrafter extends IC implements SelfTriggeredIC, PipeInputIC {

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

    public AutomaticCrafter(ChangedSign block) {
        super(block);
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
    public void think() {
        collectAndCraft();
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

    public void craft() {
        // Only called from itself or doStuff - caches already setup

        ItemStack[] contents = cachedDispenserOrDropperInventory.getContents();

        for (ItemStack it : contents) {
            if (!ItemUtil.isStackValid(it))
                continue;
            if (it.getAmount() < 2) return;
        }

        if (cachedRecipe == null) {
            computeRecipe();
        }

        if (cachedRecipe == null) return;

        var doesMatrixEqual = cachedRecipeMatrixMsb == computeMatrixMsb() && cachedRecipeMatrixLsb == computeMatrixLsb();

        if (!doesMatrixEqual && !isValidRecipe(cachedRecipe)) {
            cachedRecipe = null;
            craft();
            return;
        }

        ItemStack result = cachedRecipe.getHandle().getResult();

        if(!ItemUtil.isStackValid(result)) {
            if (!hasWarnedNoResult) {
                CraftBookPlugin.inst().getLogger().warning("An Automatic Crafter IC had a valid recipe, but there was no result! This means Bukkit"
                        + " has an invalid recipe! Result: " + result);
                hasWarnedNoResult = true;
            }
            return;
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

        items.add(result);

        List<ItemStack> leftovers = new ArrayList<>();

        cachedDispenserOrDropperInventory.setContents(replace);

        if (cachedOutputBlock.getState(false) instanceof InventoryHolder inventoryHolder) {
            var outputInventory = inventoryHolder.getInventory();

            for (ItemStack stack : items)
                leftovers.addAll(outputInventory.addItem(stack).values());
        } else {
            leftovers.addAll(items);
        }

        if (!leftovers.isEmpty()) {
            var dispenserWorld = cachedDispenserOrDropperBlock.getWorld();
            var dispenserLocation = cachedDispenserOrDropperBlock.getLocation();

            for (ItemStack leftover : leftovers)
                dispenserWorld.dropItemNaturally(dispenserLocation, leftover);

            leftovers.clear();
        }
    }

    private void collect() {
        // Only called from doStuff - caches already setup

        if (cachedRecipe == null) {
            computeRecipe();
            if (cachedRecipe == null) {
                return; // Only collect items if valid recipe.
            }
        }

        for (var itemEntity : getItemsAtBlock(getSign().getBlock())) {
            if (itemEntity.isDead() || !itemEntity.isValid())
                continue;

            var itemStack = itemEntity.getItemStack();

            var remainingAmount = InventoryUtil.distributeToMakeEvenAndGetRemainder(
              new LiveAddOnlyInventory(cachedDispenserOrDropperInventory),
              (slot, vacant) -> !vacant,
              itemStack
            );

            itemStack.setAmount(remainingAmount);
            itemEntity.setItemStack(itemStack);

            if (remainingAmount <= 0)
                itemEntity.remove();
        }
    }

    private boolean updateCachesAndGetIfIsMalformed() {
        if (cachedDispenserOrDropperBlock == null)
            cachedDispenserOrDropperBlock = getBackBlock().getRelative(0, 1, 0);

        Material blockType;

        if (
          !cachedDispenserOrDropperBlock.getWorld().isChunkLoaded(cachedDispenserOrDropperBlock.getX() >> 4, cachedDispenserOrDropperBlock.getZ() >> 4)
            || (blockType = cachedDispenserOrDropperBlock.getType()) != Material.DISPENSER && blockType != Material.DROPPER
        ) {
            cachedDispenserOrDropperInventory = null;
            cachedOutputBlock = null;
            return true;
        }

        if (cachedDispenserOrDropperInventory == null || cachedOutputBlock == null) {
            BlockState state = cachedDispenserOrDropperBlock.getState(false);
            cachedDispenserOrDropperInventory = ((InventoryHolder) state).getInventory();

            BlockFace facing = ((Directional) cachedDispenserOrDropperBlock.getBlockData()).getFacing();
            cachedOutputBlock = cachedDispenserOrDropperBlock.getRelative(facing);
        }

        return false;
    }

    private void collectAndCraft() {
        if (updateCachesAndGetIfIsMalformed())
            return;

        collect();
        craft();
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

    public static class Factory extends ICFactory {
        @Override
        public String getId() {
            return "MC1219";
        }

        @Override
        public IC create(ChangedSign sign) {
            return new AutomaticCrafter(sign);
        }

        @Override
        public void verify(ChangedSign sign) {}
    }

    @Override
    public void onPipeTransfer(PipePutEvent event) {
        if (updateCachesAndGetIfIsMalformed())
            return;

        var remainders = InventoryUtil.distributeItemsToMakeEvenAndGetRemainders(
          event.getItems(),
          new LiveAddOnlyInventory(cachedDispenserOrDropperInventory),
          (slot, vacant) -> !vacant
        );

        event.getItems().clear();
        event.setItems(remainders);
    }

    @Override
    public void load() {}

    @Override
    public void unload() {
        cachedDispenserOrDropperBlock = null;
        cachedDispenserOrDropperInventory = null;
        cachedOutputBlock = null;
        cachedRecipe = null;
    }

    private List<Item> getItemsAtBlock(Block block) {
        var items = new ArrayList<Item>();

        for (Entity nearbyEntity : block.getLocation().getNearbyEntities(2, 2, 2)) {
            if (!(nearbyEntity instanceof Item item))
                continue;

            if (item.isDead() || !item.isValid())
                continue;

            var itemLocation = item.getLocation();

            if (
              itemLocation.getBlockX() == block.getX()
                && itemLocation.getBlockY() == block.getY()
                && itemLocation.getBlockZ() == block.getZ()
            ) {
                items.add(item);
            }
        }

        return items;
    }
}
