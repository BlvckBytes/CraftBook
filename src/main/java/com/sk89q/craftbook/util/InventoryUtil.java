package com.sk89q.craftbook.util;

import com.sk89q.craftbook.mechanics.pipe.CachedBlock;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
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

    public static List<ItemStack> addItemsToInventory(GenericInventory inventory, int cachedBlock, List<ItemStack> stacks, EnumSet<InventoryAddFlag> flags) {
        var furnaceIgredientSet = getFurnaceIngredientSetForBlock(cachedBlock);

        if (furnaceIgredientSet != null)
            return addItemsToFurnace(inventory, furnaceIgredientSet, stacks, flags.contains(InventoryAddFlag.ADD_TO_FURNACE_RESULT));

        if (CachedBlock.isMaterial(cachedBlock, Material.BREWING_STAND))
            return addItemsToBrewingStand(inventory, stacks);

        if (CachedBlock.isMaterial(cachedBlock, Material.CRAFTER))
            return distributeItemsToMakeEvenAndGetRemainders(stacks, inventory, (slot, contents) -> !inventory.isSlotDisabled(slot));

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

            var remainder = inventory.addAndGetRemainder(stack);

            if (remainder != null)
                leftovers.add(remainder);
        }

        return leftovers;
    }

    private static @Nullable Set<Material> getFurnaceIngredientSetForBlock(int cachedBlock) {
        if (CachedBlock.isMaterial(cachedBlock, Material.FURNACE))
            return furnaceIngredients;

        if (CachedBlock.isMaterial(cachedBlock, Material.SMOKER))
            return smokerIngredients;

        if (CachedBlock.isMaterial(cachedBlock, Material.BLAST_FURNACE))
            return blastFurnaceIngredients;

        return null;
    }

    private static List<ItemStack> addItemsToFurnace(GenericInventory inventory, Set<Material> ingredientSet, List<ItemStack> stacks, boolean addToResult) {
        var leftovers = new ArrayList<ItemStack>();

        ItemStack leftover;
        ItemStack slot;

        for (var stack : stacks) {
            if (!ItemUtil.isStackValid(stack))
                continue;

            if (ingredientSet.contains(stack.getType())) {
                if ((slot = inventory.get(NamedSlot.FURNACE_SMELTING)) == null) {
                    inventory.set(NamedSlot.FURNACE_SMELTING, stack);
                    continue;
                }

                if ((leftover = addToStack(slot, stack)) != null)
                    leftovers.add(leftover);

                continue;
            }

            if (stack.getType().isFuel()) {
                if ((slot = inventory.get(NamedSlot.FURNACE_FUEL)) == null) {
                    inventory.set(NamedSlot.FURNACE_FUEL, stack);
                    continue;
                }

                if ((leftover = addToStack(slot, stack)) != null)
                    leftovers.add(leftover);

                continue;
            }

            if (addToResult) {
                if ((slot = inventory.get(NamedSlot.FURNACE_RESULT)) == null) {
                    inventory.set(NamedSlot.FURNACE_RESULT, stack);
                    continue;
                }

                if ((leftover = addToStack(slot, stack)) != null)
                    leftovers.add(leftover);

                continue;
            }

            // Not compatible with any of the furnace input-slots
            leftovers.add(stack);
        }

        return leftovers;
    }

    private static List<ItemStack> addItemsToBrewingStand(GenericInventory inventory, Iterable<ItemStack> stacks) {
        List<ItemStack> leftovers = new ArrayList<>();

        ItemStack slot;

        stackLoop: for (ItemStack stack : stacks) {
            if (isAPotionIngredient(stack)) {
                if ((slot = inventory.get(NamedSlot.BREWER_INGREDIENT)) == null) {
                    inventory.set(NamedSlot.BREWER_INGREDIENT, stack);
                    continue;
                }

                stack = addToStack(slot, stack);

                if (stack == null)
                    continue;
            }

            if (stack.getType() == Material.BLAZE_POWDER) {
                if ((slot = inventory.get(NamedSlot.BREWER_FUEL)) == null) {
                    inventory.set(NamedSlot.BREWER_FUEL, stack);
                    continue;
                }

                stack = addToStack(slot, stack);

                if (stack == null)
                    continue;
            }

            if (stack.getType() == Material.GLASS_BOTTLE
                    || stack.getType() == Material.POTION
                    || stack.getType() == Material.LINGERING_POTION
                    || stack.getType() == Material.SPLASH_POTION) {
                for (int i = 0; i < 3; i++) {
                    var currentItem = inventory.get(i);

                    if (currentItem == null) {
                        inventory.set(i, stack);
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
      GenericInventory inventory,
      @Nullable SlotPredicate slotPredicate,
      @NotNull ItemStack similarItem
    ) {
        ItemStack smallest = null;
        var smallestIndex = -1;

        for (var index = 0; index < inventory.getSize(); ++index) {
            var candidate = inventory.get(index);

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

    public static int distributeToMakeEvenAndGetRemainder(
      GenericInventory inventory,
      @Nullable SlotPredicate slotPredicate,
      ItemStack source
    ) {
        var remainingAmount = source.getAmount();

        while (remainingAmount > 0) {
            var destinationIndex = InventoryUtil.getSmallestAmountOrPossiblyVacantIndex(inventory, slotPredicate, source);

            if (destinationIndex < 0)
                break;

            var destination = inventory.get(destinationIndex);

            if (!ItemUtil.isStackValid(destination)) {
                destination = new ItemStack(source);
                destination.setAmount(1);
                inventory.set(destinationIndex, destination);
                --remainingAmount;
                continue;
            }

            if (destination.getAmount() >= 64)
                break;

            destination.setAmount(destination.getAmount() + 1);
            --remainingAmount;
        }

        return remainingAmount;
    }

    public static List<ItemStack> distributeItemsToMakeEvenAndGetRemainders(
      Collection<ItemStack> itemsToAdd,
      GenericInventory inventory,
      @Nullable SlotPredicate slotPredicate
    ) {
        var remainders = new ArrayList<>(itemsToAdd);

        for (var itemIndex = remainders.size() - 1; itemIndex >= 0; --itemIndex) {
            var itemToPut = remainders.get(itemIndex);

            if (!ItemUtil.isStackValid(itemToPut)) {
                remainders.remove(itemIndex);
                continue;
            }

            var remainingAmount = distributeToMakeEvenAndGetRemainder(inventory, slotPredicate, itemToPut);

            itemToPut.setAmount(remainingAmount);

            if (remainingAmount <= 0)
                remainders.remove(itemIndex);
        }

        return remainders;
    }
}
