package com.sk89q.craftbook.util;

import org.bukkit.*;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.Furnace;
import org.bukkit.block.Smoker;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class ItemUtil {

    private static final Map<Material, ItemStack> furnaceResults;
    private static final Map<Material, ItemStack> blastFurnaceResults;

    static {
        furnaceResults = new HashMap<>();
        blastFurnaceResults = new HashMap<>();

        var recipes = Bukkit.recipeIterator();

        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();

            if (!(recipe instanceof CookingRecipe<?> cookingRecipe))
                continue;

            if (!(cookingRecipe.getInputChoice() instanceof RecipeChoice.MaterialChoice materialChoice))
                continue;

            Map<Material, ItemStack> targetMap;

            switch (cookingRecipe) {
                case FurnaceRecipe ignored -> targetMap = furnaceResults;
                case BlastingRecipe ignored -> targetMap = blastFurnaceResults;
                default -> { continue; }
            }

            for (var choice : materialChoice.getChoices())
                targetMap.put(choice, cookingRecipe.getResult());
        }
    }

    /**
     * Add an itemstack to an existing itemstack.
     * 
     * @param base The itemstack to be added to.
     * @param toAdd The itemstack to add to the base.
     * @return The unaddable items.
     */
    public static ItemStack addToStack(ItemStack base, ItemStack toAdd) {
        var spaceOnBase = base.getMaxStackSize() - base.getAmount();

        if (spaceOnBase <= 0)
            return toAdd;

        if (!base.isSimilar(toAdd))
            return toAdd;

        if (toAdd.getAmount() > spaceOnBase) {
            toAdd.setAmount(toAdd.getAmount() - spaceOnBase);
            base.setAmount(base.getMaxStackSize());
            return toAdd;
        }

        base.setAmount(base.getAmount() + toAdd.getAmount());
        return null;
    }

    public static boolean isStackValid(ItemStack item) {
        if (item == null)
            return false;

      return item.getAmount() > 0;
    }

    public static boolean isCookable(ItemStack item) {

        return getCookedResult(item) != null;
    }

    public static ItemStack getCookedResult(ItemStack item) {
        return furnaceResults.get(item.getType());
    }

    public static boolean isSmeltable(ItemStack item) {
        return getSmeltedResult(item) != null;
    }

    public static ItemStack getSmeltedResult(ItemStack item) {
        return furnaceResults.get(item.getType());
    }

    public static boolean isBlastSmeltable(ItemStack item) {

        return getBlastSmeltedResult(item) != null;
    }

    public static ItemStack getBlastSmeltedResult(ItemStack item) {
        return blastFurnaceResults.get(item.getType());
    }

    /**
     * Checks whether the item is usable as a fuel in a furnace.
     * 
     * @param item The item to check.
     * @return Whether it is usable in a furnace.
     */
    public static boolean isAFuel(ItemStack item) {

        return item.getType().isFuel();
    }

    /**
     * Checks whether an item is a potion ingredient.
     * 
     * @param item The item to check.
     * @return If the item is a potion ingredient.
     */
    public static boolean isAPotionIngredient(ItemStack item) {

        switch(item.getType()) {
            case NETHER_WART:
            case GLOWSTONE_DUST:
            case REDSTONE:
            case SPIDER_EYE:
            case MAGMA_CREAM:
            case SUGAR:
            case GLISTERING_MELON_SLICE:
            case GHAST_TEAR:
            case BLAZE_POWDER:
            case FERMENTED_SPIDER_EYE:
            case GUNPOWDER:
            case GOLDEN_CARROT:
            case RABBIT_FOOT:
            case PUFFERFISH:
            case PHANTOM_MEMBRANE:
            case DRAGON_BREATH:
            case TURTLE_HELMET:
            case SLIME_BLOCK:
                return true;
            default:
                return false;
        }
    }

    public static boolean isFurnacable(ItemStack item, @Nullable Furnace furnace) {
        if (furnace instanceof BlastFurnace) {
            return isBlastSmeltable(item);
        }

        if (furnace instanceof Smoker) {
            return isCookable(item);
        }

        return isSmeltable(item) || isCookable(item) || isBlastSmeltable(item);
    }
}
