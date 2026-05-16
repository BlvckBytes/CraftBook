package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.util.ItemUtil;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Crafter;
import org.bukkit.block.Furnace;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.IntStream;

public class InventoryUtil {

    public static List<ItemStack> addItemsToInventory(InventoryHolder container, Iterable<ItemStack> stacks, EnumSet<InventoryAddFlag> flags) {
        if (container instanceof Furnace)
            return addItemsToFurnace((Furnace) container, stacks, flags.contains(InventoryAddFlag.ADD_TO_FURNACE_RESULT));

        if (container instanceof BrewingStand)
            return addItemsToBrewingStand((BrewingStand) container, stacks);

        if (container instanceof Crafter)
            return addItemsToCrafter((Crafter) container, stacks);

        if (container instanceof ChiseledBookshelf)
            return addItemsToChiseledBookshelf((ChiseledBookshelf) container, stacks);

        // Basic inventories like chests, dispensers, storage carts, etc.

        var leftovers = new ArrayList<ItemStack>();
        var isAddingToShulkerBox = container instanceof ShulkerBox;
        var inventory = container.getInventory();

        for (var stack : stacks) {
            if (stack == null)
                continue;

            // Shulker-boxes do not nest
            if (isAddingToShulkerBox && Tag.SHULKER_BOXES.isTagged(stack.getType())) {
                leftovers.add(stack);
                continue;
            }

            leftovers.addAll(inventory.addItem(stack).values());
        }

        return leftovers;
    }

    private static List<ItemStack> addItemsToFurnace(Furnace furnace, Iterable<ItemStack> stacks, boolean addToResult) {
        var inventory = furnace.getInventory();
        var leftovers = new ArrayList<ItemStack>();

        ItemStack leftover;

        for (var stack : stacks) {
            if (!ItemUtil.isStackValid(stack))
                continue;

            if (ItemUtil.isFurnacable(stack, furnace)) {
                if (inventory.getSmelting() == null) {
                    inventory.setSmelting(stack);
                    continue;
                }

                if ((leftover = ItemUtil.addToStack(inventory.getSmelting(), stack)) != null)
                    leftovers.add(leftover);

                continue;
            }

            if (ItemUtil.isAFuel(stack)) {
                if (inventory.getFuel() == null) {
                    inventory.setFuel(stack);
                    continue;
                }

                if ((leftover = ItemUtil.addToStack(inventory.getFuel(), stack)) != null)
                    leftovers.add(leftover);

                continue;
            }

            if (addToResult) {
                if (inventory.getResult() == null) {
                    inventory.setResult(stack);
                    continue;
                }

                if ((leftover = ItemUtil.addToStack(inventory.getResult(), stack)) != null)
                    leftovers.add(leftover);

                continue;
            }

            // Not compatible with any of the furnace input-slots
            leftovers.add(stack);
        }

        return leftovers;
    }

    private static List<ItemStack> addItemsToBrewingStand(BrewingStand brewingStand, Iterable<ItemStack> stacks) {
        List<ItemStack> leftovers = new ArrayList<>();

        stackLoop: for (ItemStack stack : stacks) {
            BrewerInventory inv = brewingStand.getInventory();

            if (ItemUtil.isAPotionIngredient(stack)) {
                if (inv.getIngredient() == null) {
                    inv.setIngredient(stack);
                    continue;
                }

                stack = ItemUtil.addToStack(inv.getIngredient(), stack);

                if (stack == null)
                    continue;
            }

            if (stack.getType() == Material.BLAZE_POWDER) {
                if (inv.getFuel() == null) {
                    inv.setFuel(stack);
                    continue;
                }

                stack = ItemUtil.addToStack(inv.getFuel(), stack);

                if (stack == null)
                    continue;
            }

            if (stack.getType() == Material.GLASS_BOTTLE
                    || stack.getType() == Material.POTION
                    || stack.getType() == Material.LINGERING_POTION
                    || stack.getType() == Material.SPLASH_POTION) {
                for (int i = 0; i < 3; i++) {
                    var currentItem = inv.getItem(i);

                    if (currentItem == null) {
                        inv.setItem(i, stack);
                        continue stackLoop;
                    }

                    stack = ItemUtil.addToStack(currentItem, stack);

                    if (stack == null)
                        continue stackLoop;
                }
            }

            leftovers.add(stack);
        }

        return leftovers;
    }

    private static List<ItemStack> addItemsToCrafter(Crafter crafter, Iterable<ItemStack> stacks) {

        List<ItemStack> leftovers = new ArrayList<>();
        int[] availableSlots = IntStream.rangeClosed(0, crafter.getInventory().getSize() - 1).filter(slot -> !crafter.isSlotDisabled(slot)).toArray();

        for(ItemStack stack : stacks) {
            Inventory inv = crafter.getInventory();

            for (int i : availableSlots) {
                if (stack == null) {
                    break;
                }
                if (inv.getItem(i) == null) {
                    inv.setItem(i, stack);
                    stack = null;
                } else {
                    stack = ItemUtil.addToStack(inv.getItem(i), stack);
                }
            }
            if (stack != null) {
                leftovers.add(stack);
            }
        }
        return leftovers;
    }

    private static List<ItemStack> addItemsToChiseledBookshelf(ChiseledBookshelf chiseledBookshelf, Iterable<ItemStack> stacks) {
        var leftovers = new ArrayList<ItemStack>();
        var inventory = chiseledBookshelf.getInventory();

        for (var stack : stacks) {
            if (!Tag.ITEMS_BOOKSHELF_BOOKS.isTagged(stack.getType())) {
                leftovers.add(stack);
                continue;
            }

            leftovers.addAll(inventory.addItem(stack).values());
        }

        return leftovers;
    }
}
