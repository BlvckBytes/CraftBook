package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.*;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.IntPredicate;

public class InventoryUtil {

    private static final Set<Material> furnaceIngredients;
    private static final Set<Material> blastFurnaceIngredients;
    private static final Set<Material> smokerIngredients;

    static {
        furnaceIngredients = new HashSet<>();
        blastFurnaceIngredients = new HashSet<>();
        smokerIngredients = new HashSet<>();

        var recipes = Bukkit.recipeIterator();

        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();

            if (!(recipe instanceof CookingRecipe<?> cookingRecipe))
                continue;

            if (!(cookingRecipe.getInputChoice() instanceof RecipeChoice.MaterialChoice materialChoice))
                continue;

            if (cookingRecipe instanceof FurnaceRecipe) {
                furnaceIngredients.addAll(materialChoice.getChoices());
                continue;
            }

            if (cookingRecipe instanceof BlastingRecipe) {
                blastFurnaceIngredients.addAll(materialChoice.getChoices());
                continue;
            }

            if (cookingRecipe instanceof SmokingRecipe) {
                smokerIngredients.addAll(materialChoice.getChoices());
            }
        }
    }

    private static ItemStack addToStack(ItemStack base, ItemStack toAdd) {
        var spaceOnBase = base.getMaxStackSize() - base.getAmount();

        if (spaceOnBase <= 0)
            return toAdd;

        if (!base.isSimilar(toAdd))
            return toAdd;

        if (toAdd.getAmount() > spaceOnBase) {
            toAdd.setAmount(toAdd.getAmount() - spaceOnBase);
            base.setAmount(base.getAmount() + spaceOnBase);
            return toAdd;
        }

        base.setAmount(base.getAmount() + toAdd.getAmount());
        return null;
    }

    private static boolean isAPotionIngredient(ItemStack item) {
      return switch (item.getType()) {
        case NETHER_WART, GLOWSTONE_DUST, REDSTONE, SPIDER_EYE, MAGMA_CREAM, SUGAR, GLISTERING_MELON_SLICE, GHAST_TEAR,
             BLAZE_POWDER, FERMENTED_SPIDER_EYE, GUNPOWDER, GOLDEN_CARROT, RABBIT_FOOT, PUFFERFISH, PHANTOM_MEMBRANE,
             DRAGON_BREATH, TURTLE_HELMET, SLIME_BLOCK -> true;
        default -> false;
      };
    }

    private static boolean isFurnaceIngredient(ItemStack item, @Nullable Furnace furnace) {
        var type = item.getType();

        if (furnace instanceof BlastFurnace)
            return blastFurnaceIngredients.contains(type);

        if (furnace instanceof Smoker)
            return smokerIngredients.contains(type);

        return furnaceIngredients.contains(type) || blastFurnaceIngredients.contains(type) || smokerIngredients.contains(type);
    }

    public static List<ItemStack> addItemsToInventory(InventoryHolder container, Collection<ItemStack> stacks, EnumSet<InventoryAddFlag> flags) {
        if (container instanceof Furnace)
            return addItemsToFurnace((Furnace) container, stacks, flags.contains(InventoryAddFlag.ADD_TO_FURNACE_RESULT));

        if (container instanceof BrewingStand)
            return addItemsToBrewingStand((BrewingStand) container, stacks);

        if (container instanceof Crafter crafter)
            return distributeItemsToMakeEvenlyAndGetRemainders(stacks, crafter.getInventory(), slot -> !crafter.isSlotDisabled(slot));

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

            if (isFurnaceIngredient(stack, furnace)) {
                if (inventory.getSmelting() == null) {
                    inventory.setSmelting(stack);
                    continue;
                }

                if ((leftover = addToStack(inventory.getSmelting(), stack)) != null)
                    leftovers.add(leftover);

                continue;
            }

            if (stack.getType().isFuel()) {
                if (inventory.getFuel() == null) {
                    inventory.setFuel(stack);
                    continue;
                }

                if ((leftover = addToStack(inventory.getFuel(), stack)) != null)
                    leftovers.add(leftover);

                continue;
            }

            if (addToResult) {
                if (inventory.getResult() == null) {
                    inventory.setResult(stack);
                    continue;
                }

                if ((leftover = addToStack(inventory.getResult(), stack)) != null)
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

            if (isAPotionIngredient(stack)) {
                if (inv.getIngredient() == null) {
                    inv.setIngredient(stack);
                    continue;
                }

                stack = addToStack(inv.getIngredient(), stack);

                if (stack == null)
                    continue;
            }

            if (stack.getType() == Material.BLAZE_POWDER) {
                if (inv.getFuel() == null) {
                    inv.setFuel(stack);
                    continue;
                }

                stack = addToStack(inv.getFuel(), stack);

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

                    stack = addToStack(currentItem, stack);

                    if (stack == null)
                        continue stackLoop;
                }
            }

            leftovers.add(stack);
        }

        return leftovers;
    }

    public static ItemStack getSmallestSimilarStack(
      Inventory inventory,
      @Nullable IntPredicate slotPredicate,
      @NotNull ItemStack similarItem
    ) {
        ItemStack smallest = null;

        for (var slotIndex = 0; slotIndex < inventory.getSize(); ++slotIndex) {
            if (slotPredicate != null && !slotPredicate.test(slotIndex))
                continue;

            var candidate = inventory.getItem(slotIndex);

            if (!ItemUtil.isStackValid(candidate) || !similarItem.isSimilar(candidate))
                continue;

            if (smallest == null || candidate.getAmount() < smallest.getAmount())
                smallest = candidate;
        }

        return smallest;
    }

    public static int distributeToMakeEvenAndGetRemainder(
      Inventory inventory,
      @Nullable IntPredicate slotPredicate,
      ItemStack source
    ) {
        var remainingAmount = source.getAmount();

        while (remainingAmount > 0) {
            var destination = InventoryUtil.getSmallestSimilarStack(inventory, slotPredicate, source);

            if (destination == null)
                return remainingAmount;

            if (destination.getAmount() >= 64)
                return remainingAmount;

            destination.setAmount(destination.getAmount() + 1);
            --remainingAmount;
        }

        return remainingAmount;
    }

    public static List<ItemStack> distributeItemsToMakeEvenlyAndGetRemainders(
      Collection<ItemStack> itemsToAdd,
      Inventory inventory,
      @Nullable IntPredicate slotPredicate
    ) {
        var remainders = new ArrayList<>(itemsToAdd);

        for (var itemIndex = remainders.size() - 1; itemIndex >= 0; --itemIndex) {
            var itemToPut = remainders.get(itemIndex);

            if (!ItemUtil.isStackValid(itemToPut)) {
                remainders.remove(itemIndex);
                continue;
            }

            var remainder = distributeToMakeEvenAndGetRemainder(inventory, slotPredicate, itemToPut);

            itemToPut.setAmount(remainder);

            if (remainder <= 0)
                remainders.remove(itemIndex);
        }

        return remainders;
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
