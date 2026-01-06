package com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter;

import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectOpenHashMap;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

public class CachedShapedRecipe implements CachedRecipe {

  private final Recipe handle;

  public final ItemStack cachedResult;
  private final String[] cachedShape;
  private final Char2ObjectMap<RecipeChoice> cachedIngredientsMap;

  CachedShapedRecipe(ShapedRecipe handle) {
    this.handle = handle;
    this.cachedResult = handle.getResult();
    this.cachedShape = handle.getShape();
    this.cachedIngredientsMap = new Char2ObjectOpenHashMap<>();
    cachedIngredientsMap.putAll(handle.getChoiceMap());
  }

  public String getShapeRow(int index) {
    if(index < cachedShape.length)
      return cachedShape[index];

    return "   ";
  }

  public RecipeChoice getIngredient(char c) {
    return cachedIngredientsMap.get(c);
  }

  @Override
  public Recipe getHandle() {
    return handle;
  }
}
