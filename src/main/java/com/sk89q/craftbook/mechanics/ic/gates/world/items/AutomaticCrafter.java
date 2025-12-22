package com.sk89q.craftbook.mechanics.ic.gates.world.items;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.crafting.CustomCrafting;
import com.sk89q.craftbook.mechanics.ic.*;
import com.sk89q.craftbook.mechanics.pipe.PipePutEvent;
import com.sk89q.craftbook.mechanics.pipe.PipeRequestEvent;
import com.sk89q.craftbook.util.InventoryUtil;
import com.sk89q.craftbook.util.ItemUtil;
import com.sk89q.craftbook.util.VerifyUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Item;
import org.bukkit.inventory.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AutomaticCrafter extends AbstractSelfTriggeredIC implements PipeInputIC {

    private static boolean hasWarned = false;
    private static boolean hasWarnedNoResult = false;

    private Block cachedDispenserOrDropperBlock;
    private Inventory cachedDispenserOrDropperInventory;
    private Block cachedOutputBlock;

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

    // Cache the recipe - makes it faster
    private Recipe recipe;

    @Override
    public void trigger(ChipState chip) {

        if (chip.getInput(0)) {
            chip.setOutput(0, doStuff(true, true));
        }
    }

    @Override
    public void think(ChipState state) {

        state.setOutput(0, doStuff(true, true));
    }

    private void computeRecipe() {
        // Only called from craft/collect - caches already setup

        Iterator<Recipe> recipes = Bukkit.recipeIterator();

        try {
            while (recipes.hasNext()) {
                Recipe temprecipe = recipes.next();
                if (isValidRecipe(temprecipe, cachedDispenserOrDropperInventory)) {
                    recipe = temprecipe;
                    break; //There should only be 1 valid recipe.
                }
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

        if (recipe == null) {
            computeRecipe();
        }

        if (recipe == null) return false;

        if (!isValidRecipe(recipe, cachedDispenserOrDropperInventory)) {
            recipe = null;
            return craft();
        }

        ItemStack result = CustomCrafting.craftItem(recipe);

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

            replace[i] = new ItemStack(contents[i]);

            if(replace[i].getType() == Material.WATER_BUCKET || replace[i].getType() == Material.LAVA_BUCKET || replace[i].getType() == Material.MILK_BUCKET)
                items.add(new ItemStack(Material.BUCKET, 1));

            replace[i].setAmount(replace[i].getAmount() - 1);
        }

        cachedDispenserOrDropperInventory.clear();

        CraftBookPlugin.logDebugMessage("AutoCrafter is dispensing a " + result.getType().name() + " with data: " + result.getDurability() + " and amount: " + result.getAmount(), "ic-mc1219");

        items.add(result);

        PipeRequestEvent event = new PipeRequestEvent(cachedOutputBlock, items, cachedDispenserOrDropperBlock);
        Bukkit.getPluginManager().callEvent(event);

        items = event.getItems();

        List<ItemStack> leftovers = new ArrayList<>();

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

        cachedDispenserOrDropperInventory.setContents(replace);
        return true;
    }

    private boolean collect() {
        // Only called from doStuff - caches already setup

        if (recipe == null) {
            computeRecipe();
            if (recipe == null) {
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

    /**
     * @param craft Whether to craft.
     * @param collect Whether to collect.
     *
     * @return If it performed an action
     */
    private boolean doStuff(boolean craft, boolean collect) {
        if (updateCachesAndGetIfIsMalformed())
            return false;

        boolean ret = false;

        if (collect)
            ret |= collect();

        if (craft)
            ret |= craft();

        return ret;
    }

    private boolean isValidRecipe(Recipe r, Inventory inv) {
        if (r instanceof ShapedRecipe && (recipe == null || recipe instanceof ShapedRecipe)) {
            ShapedRecipe shape = (ShapedRecipe) r;
            Map<Character, ItemStack> ingredientMap = shape.getIngredientMap();
            String[] shapeArr = shape.getShape();
            if (shape.getShape().length != shapeArr.length  || shapeArr[0].length() != shape.getShape()[0].length()) return false;
            int c = -1, in = 0;
            int validRecipeItems = 0;
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = inv.getItem(slot);
                try {
                    c++;
                    if (c >= 3) {
                        c = 0;
                        in++;
                        if (in >= 3) {
                            break;
                        }
                    }
                    String shapeSection;
                    if(in < shapeArr.length)
                        shapeSection = shapeArr[in];
                    else
                        shapeSection = "   ";
                    ItemStack require = null;
                    try {
                        Character item;
                        if(c < shapeSection.length())
                            item = shapeSection.charAt(c);
                        else
                            item = ' ';
                        if(item == ' ')
                            require = null;
                        else
                            require = ingredientMap.get(item);
                    }
                    catch(Exception e){
                        CraftBookBukkitUtil.printStacktrace(e);
                    }
                    if (require != null && require.getType() != Material.AIR) {
                        validRecipeItems ++;
                    }
                    if (!ItemUtil.areItemsIdentical(require, stack))
                        return false;
                } catch (Exception e) {
                    CraftBookBukkitUtil.printStacktrace(e);
                    return false;
                }
            }
            if (validRecipeItems == 0) {
                if (!hasWarned) {
                    CraftBookPlugin.logger().warning("Found invalid recipe! This is an issue with Bukkit/Spigot/etc, please report to them. All recipe ingredients are air. Recipe result: " + r.getResult().toString());
                    hasWarned = true;
                }
                return false;
            }

            return true;
        } else if (r instanceof ShapelessRecipe && (recipe == null || recipe instanceof ShapelessRecipe)) {
            if (((ShapelessRecipe) r).getKey().getKey().equals("shulker_box_coloring")) {
                return false;
            }
            ShapelessRecipe shape = (ShapelessRecipe) r;
            List<ItemStack> ing = new ArrayList<>(VerifyUtil.withoutNulls(shape.getIngredientList()));
            if (ing.isEmpty()) {
                return false; // If it's empty already, something is wrong with the recipe.
            }
            for (ItemStack it : inv.getContents()) {
                if (!ItemUtil.isStackValid(it)) continue;
                if(ing.isEmpty())
                    return false;
                Iterator<ItemStack> ingIterator = ing.iterator();
                while (ingIterator.hasNext()) {
                    if(ing.isEmpty())
                        break;
                    ItemStack stack = ingIterator.next();
                    if (!ItemUtil.isStackValid(stack)) {
                        ingIterator.remove();
                        continue;
                    }
                    if (ItemUtil.areItemsIdentical(it, stack)) {
                        ingIterator.remove();
                        break;
                    }
                }
            }
            return ing.isEmpty();

        } else
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
    }
}
