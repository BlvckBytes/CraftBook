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

          var setToAddTo = switch (cookingRecipe) {
            case FurnaceRecipe ignored -> furnaceIngredients;
            case BlastingRecipe ignored -> blastFurnaceIngredients;
            case SmokingRecipe ignored -> smokerIngredients;
            default -> null;
          };

          if (setToAddTo != null)
              setToAddTo.addAll(materialChoice.getChoices());
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

    public static List<ItemStack> addItemsToInventory(Inventory inventory, int cachedBlock, List<ItemStack> stacks, EnumSet<InventoryAddFlag> flags) {
        if (inventory instanceof FurnaceInventory furnaceInventory)
            return addItemsToFurnace(furnaceInventory, cachedBlock, stacks, flags.contains(InventoryAddFlag.ADD_TO_FURNACE_RESULT));

        if (inventory instanceof BrewerInventory brewerInventory)
            return addItemsToBrewingStand(brewerInventory, stacks);

        // CrafterInventory is currently an empty interface and Paper API is not returning an
        // instanceof; thus, let's simply do a material-check instead.
        if (CachedBlock.isMaterial(cachedBlock, Material.CRAFTER) && inventory.getHolder() instanceof Crafter crafter)
            return distributeItemsToMakeEvenAndGetRemainders(stacks, inventory, (slot, contents) -> !crafter.isSlotDisabled(slot));

        if (inventory instanceof ChiseledBookshelfInventory)
            return addItemsToChiseledBookshelf(inventory, stacks);

        // Basic inventories like chests, dispensers, storage carts, etc.

        var leftovers = new ArrayList<ItemStack>();
        var isAddingToShulkerBox = Tag.SHULKER_BOXES.isTagged(CachedBlock.getMaterial(cachedBlock));

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

    private static List<ItemStack> addItemsToFurnace(FurnaceInventory inventory, int cachedBlock, List<ItemStack> stacks, boolean addToResult) {
        Set<Material> ingredientSet;

        if (CachedBlock.isMaterial(cachedBlock, Material.FURNACE))
            ingredientSet = furnaceIngredients;
        else if (CachedBlock.isMaterial(cachedBlock, Material.SMOKER))
            ingredientSet = smokerIngredients;
        else if (CachedBlock.isMaterial(cachedBlock, Material.BLAST_FURNACE))
            ingredientSet = blastFurnaceIngredients;
        else
            return stacks;

        var leftovers = new ArrayList<ItemStack>();

        ItemStack leftover;

        for (var stack : stacks) {
            if (!ItemUtil.isStackValid(stack))
                continue;

            if (ingredientSet.contains(stack.getType())) {
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

    private static List<ItemStack> addItemsToBrewingStand(BrewerInventory inventory, Iterable<ItemStack> stacks) {
        List<ItemStack> leftovers = new ArrayList<>();

        stackLoop: for (ItemStack stack : stacks) {
            if (isAPotionIngredient(stack)) {
                if (inventory.getIngredient() == null) {
                    inventory.setIngredient(stack);
                    continue;
                }

                stack = addToStack(inventory.getIngredient(), stack);

                if (stack == null)
                    continue;
            }

            if (stack.getType() == Material.BLAZE_POWDER) {
                if (inventory.getFuel() == null) {
                    inventory.setFuel(stack);
                    continue;
                }

                stack = addToStack(inventory.getFuel(), stack);

                if (stack == null)
                    continue;
            }

            if (stack.getType() == Material.GLASS_BOTTLE
                    || stack.getType() == Material.POTION
                    || stack.getType() == Material.LINGERING_POTION
                    || stack.getType() == Material.SPLASH_POTION) {
                for (int i = 0; i < 3; i++) {
                    var currentItem = inventory.getItem(i);

                    if (currentItem == null) {
                        inventory.setItem(i, stack);
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

    private static int getSmallestAmountOrPossiblyVacantIndex(
      ItemStack[] candidates,
      @Nullable SlotPredicate slotPredicate,
      @NotNull ItemStack similarItem
    ) {
        ItemStack smallest = null;
        var smallestIndex = -1;

        for (var index = 0; index < candidates.length; ++index) {
            var candidate = candidates[index];

            if (slotPredicate != null && !slotPredicate.test(index, candidate))
                continue;

            if (!ItemUtil.isStackValid(candidate))
                return index;

            if (!similarItem.isSimilar(candidate))
                continue;

            if (smallest == null || candidate.getAmount() < smallest.getAmount()) {
                smallest = candidate;
                smallestIndex = index;
            }
        }

        return smallestIndex;
    }

    public static DistributeResult distributeToMakeEven(
      ItemStack[] destinations,
      @Nullable SlotPredicate slotPredicate,
      ItemStack source
    ) {
        var remainingAmount = source.getAmount();
        var createdStack = false;

        while (remainingAmount > 0) {
            var destinationIndex = InventoryUtil.getSmallestAmountOrPossiblyVacantIndex(destinations, slotPredicate, source);

            if (destinationIndex < 0)
                break;

            var destination = destinations[destinationIndex];

            if (!ItemUtil.isStackValid(destination)) {
                destination = new ItemStack(source);
                destination.setAmount(1);
                destinations[destinationIndex] = destination;
                --remainingAmount;
                createdStack = true;
                continue;
            }

            if (destination.getAmount() >= 64)
                break;

            destination.setAmount(destination.getAmount() + 1);
            --remainingAmount;
        }

        return new DistributeResult(remainingAmount, createdStack);
    }

    public static List<ItemStack> distributeItemsToMakeEvenAndGetRemainders(
      Collection<ItemStack> itemsToAdd,
      Inventory inventory,
      @Nullable SlotPredicate slotPredicate
    ) {
        var remainders = new ArrayList<>(itemsToAdd);
        var createdStack = false;

        var inventoryContents = inventory.getContents();

        for (var itemIndex = remainders.size() - 1; itemIndex >= 0; --itemIndex) {
            var itemToPut = remainders.get(itemIndex);

            if (!ItemUtil.isStackValid(itemToPut)) {
                remainders.remove(itemIndex);
                continue;
            }

            var result = distributeToMakeEven(inventoryContents, slotPredicate, itemToPut);
            createdStack |= result.createdStack();

            itemToPut.setAmount(result.remainder());

            if (result.remainder() <= 0)
                remainders.remove(itemIndex);
        }

        // Write back the contents manually - this branch (currently) only executes
        // with vanilla Crafters, who drop their disabled slots on a #setContents...
        if (createdStack) {
            for (var i = 0; i < inventoryContents.length; ++i) {
                var contentsItem = inventoryContents[i];

                // When distributing, we only ever add, never remove, so null-slots stay constant.
                if (contentsItem != null)
                    inventory.setItem(i, contentsItem);
            }
        }

        return remainders;
    }

    private static List<ItemStack> addItemsToChiseledBookshelf(Inventory inventory, Iterable<ItemStack> stacks) {
        var leftovers = new ArrayList<ItemStack>();

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
