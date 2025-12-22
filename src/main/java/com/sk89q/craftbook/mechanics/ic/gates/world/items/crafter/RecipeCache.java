package com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter;

import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.*;

public class RecipeCache {

  // The recipe-iterator as well as getting the ingredients-map or the ingredients-list are expensive
  // operations, and since there is no need (for us, at least) to register custom recipes during runtime,
  // they can easily be cached, avoiding internal conversion allocations; if need ever be, calling the
  // update-method can still support said currently non-required feature.
  private static final List<CachedRecipe> cachedRecipes = new ArrayList<>();

  public static void update() {
    Iterator<Recipe> recipes = Bukkit.recipeIterator();

    cachedRecipes.clear();

    while (recipes.hasNext()) {
      Recipe recipe = recipes.next();

      try {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
          cachedRecipes.add(new CachedShapedRecipe(shapedRecipe));
          continue;
        }

        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
          cachedRecipes.add(new CachedShapelessRecipe(shapelessRecipe));
        }
      } catch (Exception e) {
        CraftBookBukkitUtil.printStacktrace(e);
      }
    }
  }

  public static List<CachedRecipe> getRecipes() {
    return Collections.unmodifiableList(cachedRecipes);
  }
}
