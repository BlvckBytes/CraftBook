package com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Collections;
import java.util.Map;

public class CachedShapedRecipe implements CachedRecipe {

  private final Recipe handle;

  public final ItemStack cachedResult;
  public final String[] cachedShape;
  public final Map<Character, ItemStack> cachedIngredientsMap;

  CachedShapedRecipe(ShapedRecipe handle) {
    this.handle = handle;
    this.cachedResult = handle.getResult();
    this.cachedShape = handle.getShape();
    this.cachedIngredientsMap = Collections.unmodifiableMap(handle.getIngredientMap());
  }

  @Override
  public Recipe getHandle() {
    return handle;
  }
}
