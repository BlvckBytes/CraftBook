package com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.crafting.CustomCrafting;
import com.sk89q.craftbook.mechanics.ic.*;
import com.sk89q.craftbook.mechanics.pipe.PipePutEvent;
import com.sk89q.craftbook.mechanics.pipe.PipeRequestEvent;
import com.sk89q.craftbook.util.InventoryUtil;
import com.sk89q.craftbook.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Item;
import org.bukkit.inventory.*;

import java.util.*;

public class AutomaticCrafter extends AbstractSelfTriggeredIC implements PipeInputIC {

    private static boolean hasWarned = false;
    private static boolean hasWarnedNoResult = false;

    private Block cachedDispenserOrDropperBlock;
    private Inventory cachedDispenserOrDropperInventory;
    private Block cachedOutputBlock;
    private CachedRecipe cachedRecipe;

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

    private void computeRecipe() {
        // Only called from craft/collect - caches already setup

        try {
            for (var cachedRecipe : RecipeCache.getRecipes()) {
                if (!isValidRecipe(cachedRecipe))
                    continue;

                this.cachedRecipe = cachedRecipe;
                break;
            }
        } catch (Exception e) {
            CraftBookBukkitUtil.printStacktrace(e);
            // I'm not quite sure why we need this, but let's keep it.
            cachedDispenserOrDropperInventory.setContents(cachedDispenserOrDropperInventory.getContents());
        }
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

        if (!isValidRecipe(cachedRecipe)) {
            cachedRecipe = null;
            return craft();
        }

        ItemStack result = CustomCrafting.craftItem(cachedRecipe.getHandle());

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
            if (InventoryUtil.doesBlockHaveInventory(cachedOutputBlock)) {
                Inventory outputInventory = ((InventoryHolder) cachedOutputBlock.getState()).getInventory();

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

        ItemStack[] contents = cachedDispenserOrDropperInventory.getContents();

        for (Item item : ItemUtil.getItemsAtBlock(CraftBookBukkitUtil.toSign(getSign()).getBlock())) {
            boolean delete = true;

            ItemStack stack = item.getItemStack();

            int newAmount = stack.getAmount();
            for (int i = 0; i < stack.getAmount(); i++) {
                ItemStack it = ItemUtil.getSmallestStackOfType(contents, stack);
                if (it == null) break;
                if (it.getAmount() < 64) {
                    it.setAmount(it.getAmount() + 1);
                    newAmount -= 1;
                } else if (newAmount > 0) {
                    delete = false;
                    break;
                }
            }

            if (newAmount > 0) delete = false;

            if (delete) {
                item.remove();
            } else {
                stack.setAmount(newAmount);
                item.setItemStack(stack);
            }
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

                ItemStack ingredient = null;
                char ingredientChar = ' ';

                if (columnIndex < shapeRow.length())
                    ingredientChar = shapeRow.charAt(columnIndex);

                if (ingredientChar != ' ')
                    ingredient = shape.getIngredient(ingredientChar);

                if (ingredient != null && ingredient.getType() != Material.AIR)
                    ++validRecipeItems;

                if (!ItemUtil.areItemsIdentical(ingredient, matrixItem))
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

            List<ItemStack> remainingIngredients = new ArrayList<>(shape.cachedIngredientList);

            // If it's empty already, something is wrong with the recipe.
            if (remainingIngredients.isEmpty())
                return false;

            for (ItemStack matrixItem : cachedDispenserOrDropperInventory.getContents()) {
                if (!ItemUtil.isStackValid(matrixItem))
                    continue;

                // No more required ingredients left, but there are still additional items in the crafting-matrix => mismatch.
                if (remainingIngredients.isEmpty())
                    return false;

                for (Iterator<ItemStack> iterator = remainingIngredients.iterator(); iterator.hasNext();) {
                    ItemStack requiredIngredient = iterator.next();

                    if (!ItemUtil.isStackValid(requiredIngredient)) {
                        iterator.remove();
                        continue;
                    }

                    if (ItemUtil.areItemsIdentical(matrixItem, requiredIngredient)) {
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

        ItemStack[] contents = cachedDispenserOrDropperInventory.getContents();

        boolean delete = true;
        List<ItemStack> newItems = new ArrayList<>(event.getItems());
        for (ItemStack ite : event.getItems()) {
            if (!ItemUtil.isStackValid(ite)) continue;
            int iteind = newItems.indexOf(ite);
            int newAmount = ite.getAmount();
            for (int i = 0; i < ite.getAmount(); i++) {
                ItemStack it = ItemUtil.getSmallestStackOfType(contents, ite);
                if (!ItemUtil.isStackValid(it) || !ItemUtil.areItemsIdentical(ite, it)) continue;
                if (it.getAmount() < 64) {
                    it.setAmount(it.getAmount() + 1);
                    newAmount -= 1;
                } else {
                    if (newAmount > 0) {
                        delete = false;
                        break;
                    }
                }
            }
            if (newAmount > 0) delete = false;
            if(newAmount != ite.getAmount())
                ite.setAmount(newAmount);
            if (delete) newItems.remove(iteind);
            else newItems.set(iteind, ite);
        }
        event.getItems().clear();
        event.setItems(newItems);
    }

    @Override
    public void unload() {
        super.unload();

        cachedDispenserOrDropperBlock = null;
        cachedDispenserOrDropperInventory = null;
        cachedOutputBlock = null;
        cachedRecipe = null;
    }
}
