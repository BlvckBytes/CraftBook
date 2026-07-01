package com.sk89q.craftbook.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.*;

import java.util.*;

public class InventoryUtil {

    // We want to have bottles, buckets, etc. stack as efficiently as possible, such that
    // the crafter can operate at its full speed without having to await another pipe-refill.
    private static final int CRAFTER_MAX_STACK_SIZE = 64;

    private static final int FURNACE_SMELTING_INDEX = 0;
    private static final int FURNACE_FUEL_INDEX = 1;

    private static final int BREWER_INGREDIENT_INDEX = 3;
    private static final int BREWER_FUEL_INDEX = 4;

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

    private static boolean isAPotionIngredient(ItemStack item) {
      return switch (item.getType()) {
        case NETHER_WART, GLOWSTONE_DUST, REDSTONE, SPIDER_EYE, MAGMA_CREAM, SUGAR, GLISTERING_MELON_SLICE, GHAST_TEAR,
             BLAZE_POWDER, FERMENTED_SPIDER_EYE, GUNPOWDER, GOLDEN_CARROT, RABBIT_FOOT, PUFFERFISH, PHANTOM_MEMBRANE,
             DRAGON_BREATH, TURTLE_HELMET, SLIME_BLOCK -> true;
        default -> false;
      };
    }

    public static int addItemToInventoryAndGetRemainingAmount(
      AddOnlyInventory inventory,
      Material blockMaterial,
      ItemStack itemToAdd
    ) {
        if (!ItemUtil.isStackValid(itemToAdd))
            return 0;

        var furnaceIgredientSet = switch (blockMaterial) {
            case FURNACE -> furnaceIngredients;
            case SMOKER -> smokerIngredients;
            case BLAST_FURNACE -> blastFurnaceIngredients;
            default -> null;
        };

        if (furnaceIgredientSet != null)
            return addItemToFurnaceAndGetRemainingAmount(inventory, furnaceIgredientSet, itemToAdd);

        if (blockMaterial == Material.BREWING_STAND)
            return addItemToBrewingStandAndGetRemainingAmount(inventory, itemToAdd);

        if (blockMaterial == Material.CRAFTER)
            return distributeIntoCrafterToMakeEvenAndGetRemainingAmount(inventory, itemToAdd);

        // Basic inventories like chests, dispensers, storage carts, etc.

        var isAddingToShulkerBox = Tag.SHULKER_BOXES.isTagged(blockMaterial);

        var amountToAdd = itemToAdd.getAmount();

        // Shulker-boxes do not nest
        if (isAddingToShulkerBox && Tag.SHULKER_BOXES.isTagged(itemToAdd.getType()))
            return amountToAdd;

        var addedAmount = inventory.addItemAndGetAddedAmount(itemToAdd, amountToAdd);

        if (addedAmount >= amountToAdd)
            return 0;

        return amountToAdd - addedAmount;
    }

    private static int addItemToFurnaceAndGetRemainingAmount(
      AddOnlyInventory inventory,
      Set<Material> ingredientSet,
      ItemStack itemToAdd
    ) {
        var remainingAmount = itemToAdd.getAmount();

        if (itemToAdd.getType().isFuel()) {
            remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(FURNACE_FUEL_INDEX, itemToAdd, remainingAmount);

            if (remainingAmount <= 0)
                return 0;
        }

        if (ingredientSet.contains(itemToAdd.getType())) {
            remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(FURNACE_SMELTING_INDEX, itemToAdd, remainingAmount);

            if (remainingAmount <= 0)
                return 0;
        }

        return remainingAmount;
    }

    private static int addItemToBrewingStandAndGetRemainingAmount(
      AddOnlyInventory inventory,
      ItemStack itemToAdd
    ) {
        var remainingAmount = itemToAdd.getAmount();

        if (itemToAdd.getType() == Material.BLAZE_POWDER) {
            remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(BREWER_FUEL_INDEX, itemToAdd, remainingAmount);

            if (remainingAmount <= 0)
                return 0;
        }

        if (isAPotionIngredient(itemToAdd)) {
            remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(BREWER_INGREDIENT_INDEX, itemToAdd, remainingAmount);

            if (remainingAmount <= 0)
                return 0;
        }

        if (isABottleItem(itemToAdd)) {
            for (int bottleSlot = 0; bottleSlot < 3; bottleSlot++) {
                remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(bottleSlot, itemToAdd, remainingAmount);

                if (remainingAmount <= 0)
                    return 0;
            }
        }

        return remainingAmount;
    }

    private static boolean isABottleItem(ItemStack item) {
        return switch (item.getType()) {
            case GLASS_BOTTLE, POTION, LINGERING_POTION, SPLASH_POTION -> true;
            default -> false;
        };
    }

    private static int distributeIntoCrafterToMakeEvenAndGetRemainingAmount(
      AddOnlyInventory inventory,
      ItemStack itemToAdd
    ) {
        var spaceByIndex = new int[inventory.getSize()];
        var addedAmountByIndex = new int[inventory.getSize()];

        for (var index = 0; index < spaceByIndex.length; ++index) {
            if (inventory.isSlotDisabled(index))
                continue;

            var currentAmount = inventory.getAmountIfIsSimilarOrVacant(index, itemToAdd);

            if (currentAmount == null)
                continue;

            var currentSpace = CRAFTER_MAX_STACK_SIZE - currentAmount;

            if (currentSpace <= 0)
                continue;

            spaceByIndex[index] = currentSpace;
        }

        var simulatedRemainingAmount = itemToAdd.getAmount();

        while (simulatedRemainingAmount > 0) {
            var maxSpace = 0;
            var maxSpaceIndex = -1;

            for (var index = 0; index < spaceByIndex.length; ++index) {
                var currentSpace = spaceByIndex[index];

                if (currentSpace <= 0)
                    continue;

                if (maxSpaceIndex < 0 || currentSpace > maxSpace) {
                    maxSpaceIndex = index;
                    maxSpace = currentSpace;
                }
            }

            if (maxSpaceIndex < 0)
                break;

            ++addedAmountByIndex[maxSpaceIndex];
            --spaceByIndex[maxSpaceIndex];
            --simulatedRemainingAmount;
        }

        var actualRemainingAmount = itemToAdd.getAmount();

        for (var index = 0; index < addedAmountByIndex.length; ++index) {
            var simulatedAddedAmount = addedAmountByIndex[index];

            if (simulatedAddedAmount <= 0)
                continue;

            actualRemainingAmount -= inventory.addItemToSlotAndGetAddedAmount(index, itemToAdd, simulatedAddedAmount, CRAFTER_MAX_STACK_SIZE);

            if (actualRemainingAmount <= 0)
                break;
        }

        return Math.max(0, actualRemainingAmount);
    }
}
