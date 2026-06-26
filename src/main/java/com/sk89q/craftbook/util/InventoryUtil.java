package com.sk89q.craftbook.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class InventoryUtil {

    private static final int FURNACE_SMELTING_INDEX = 0;
    private static final int FURNACE_FUEL_INDEX = 1;
    private static final int FURNACE_RESULT_INDEX = 2;

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

    public static List<ItemStack> addItemsToInventory(
      AddOnlyInventory inventory,
      Material blockMaterial,
      List<ItemStack> itemsToAdd,
      EnumSet<InventoryAddFlag> flags
    ) {
        var furnaceIgredientSet = switch (blockMaterial) {
            case FURNACE -> furnaceIngredients;
            case SMOKER -> smokerIngredients;
            case BLAST_FURNACE -> blastFurnaceIngredients;
            default -> null;
        };

        if (furnaceIgredientSet != null)
            return addItemsToFurnace(inventory, furnaceIgredientSet, itemsToAdd, flags.contains(InventoryAddFlag.ADD_TO_FURNACE_RESULT));

        if (blockMaterial == Material.BREWING_STAND)
            return addItemsToBrewingStand(inventory, itemsToAdd);

        if (blockMaterial == Material.CRAFTER)
            return distributeItemsToMakeEvenAndGetRemainders(itemsToAdd, inventory, (slot, vacant) -> !inventory.isSlotDisabled(slot));

        // Basic inventories like chests, dispensers, storage carts, etc.

        var leftovers = new ArrayList<ItemStack>();
        var isAddingToShulkerBox = Tag.SHULKER_BOXES.isTagged(blockMaterial);

        for (var itemToAdd : itemsToAdd) {
            if (!ItemUtil.isStackValid(itemToAdd))
                continue;

            // Shulker-boxes do not nest
            if (isAddingToShulkerBox && Tag.SHULKER_BOXES.isTagged(itemToAdd.getType())) {
                leftovers.add(itemToAdd);
                continue;
            }

            var amountToAdd = itemToAdd.getAmount();
            var addedAmount = inventory.addItemAndGetAddedAmount(itemToAdd, amountToAdd);

            if (addedAmount >= itemToAdd.getAmount())
                continue;

            itemToAdd.setAmount(amountToAdd - addedAmount);

            leftovers.add(itemToAdd);
        }

        return leftovers;
    }

    private static List<ItemStack> addItemsToFurnace(
      AddOnlyInventory inventory,
      Set<Material> ingredientSet,
      List<ItemStack> itemsToAdd,
      boolean addToResult
    ) {
        var leftovers = new ArrayList<ItemStack>();

        for (var itemToAdd : itemsToAdd) {
            if (!ItemUtil.isStackValid(itemToAdd))
                continue;

            var remainingAmount = itemToAdd.getAmount();

            if (itemToAdd.getType().isFuel()) {
                remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(FURNACE_FUEL_INDEX, itemToAdd, remainingAmount);

                if (remainingAmount <= 0)
                    continue;
            }

            if (ingredientSet.contains(itemToAdd.getType())) {
                remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(FURNACE_SMELTING_INDEX, itemToAdd, remainingAmount);

                if (remainingAmount <= 0)
                    continue;
            }

            if (addToResult) {
                remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(FURNACE_RESULT_INDEX, itemToAdd, remainingAmount);

                if (remainingAmount <= 0)
                    continue;
            }

            itemToAdd.setAmount(remainingAmount);

            leftovers.add(itemToAdd);
        }

        return leftovers;
    }

    private static List<ItemStack> addItemsToBrewingStand(
      AddOnlyInventory inventory,
      List<ItemStack> itemsToAdd
    ) {
        var leftovers = new ArrayList<ItemStack>();

        addLoop:
        for (ItemStack itemToAdd : itemsToAdd) {
            if (!ItemUtil.isStackValid(itemToAdd))
                continue;

            var remainingAmount = itemToAdd.getAmount();

            if (itemToAdd.getType() == Material.BLAZE_POWDER) {
                remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(BREWER_FUEL_INDEX, itemToAdd, remainingAmount);

                if (remainingAmount <= 0)
                    continue;
            }

            if (isAPotionIngredient(itemToAdd)) {
                remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(BREWER_INGREDIENT_INDEX, itemToAdd, remainingAmount);

                if (remainingAmount <= 0)
                    continue;
            }

            if (isABottleItem(itemToAdd)) {
                for (int bottleSlot = 0; bottleSlot < 3; bottleSlot++) {
                    remainingAmount -= inventory.addItemToSlotAndGetAddedAmount(bottleSlot, itemToAdd, remainingAmount);

                    if (remainingAmount <= 0)
                        continue addLoop;
                }
            }

            itemToAdd.setAmount(remainingAmount);

            leftovers.add(itemToAdd);
        }

        return leftovers;
    }

    private static boolean isABottleItem(ItemStack item) {
        return switch (item.getType()) {
            case GLASS_BOTTLE, POTION, LINGERING_POTION, SPLASH_POTION -> true;
            default -> false;
        };
    }

    public static int distributeToMakeEvenAndGetRemainder(
      AddOnlyInventory inventory,
      @Nullable SlotPredicate slotPredicate,
      ItemStack itemToAdd
    ) {
        var stackSize = itemToAdd.getMaxStackSize();
        var spaceByIndex = new int[inventory.getSize()];
        var addedAmountByIndex = new int[inventory.getSize()];

        for (var index = 0; index < spaceByIndex.length; ++index) {
            var availableSpace = inventory.getSpaceFor(index, itemToAdd);

            if (slotPredicate != null && !slotPredicate.test(index, availableSpace >= stackSize))
                continue;

            spaceByIndex[index] = availableSpace;
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

            actualRemainingAmount -= inventory.addItemToSlotAndGetAddedAmount(index, itemToAdd, simulatedAddedAmount);

            if (actualRemainingAmount <= 0)
                break;
        }

        return Math.max(0, actualRemainingAmount);
    }

    public static List<ItemStack> distributeItemsToMakeEvenAndGetRemainders(
      List<ItemStack> itemsToAdd,
      AddOnlyInventory inventory,
      @Nullable SlotPredicate slotPredicate
    ) {
        var leftovers = new ArrayList<ItemStack>();

        for (var itemToAdd : itemsToAdd) {
            if (!ItemUtil.isStackValid(itemToAdd))
                continue;

            var remainingAmount = distributeToMakeEvenAndGetRemainder(inventory, slotPredicate, itemToAdd);

            itemToAdd.setAmount(remainingAmount);

            if (remainingAmount <= 0)
                continue;

            leftovers.add(itemToAdd);
        }

        return leftovers;
    }
}
