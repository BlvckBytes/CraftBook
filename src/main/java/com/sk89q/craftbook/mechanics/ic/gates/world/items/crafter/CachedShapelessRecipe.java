package com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter;

import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public class CachedShapelessRecipe implements CachedRecipe {

  private final Recipe handle;

  public final Collection<RecipeChoice> cachedIngredientList;
  public final String cachedKey;

  CachedShapelessRecipe(ShapelessRecipe handle) {
    this.handle = handle;
    this.cachedIngredientList = Collections.unmodifiableCollection(withoutNulls(handle.getChoiceList()));
    this.cachedKey = handle.getKey().getKey();
  }

  @Override
  public Recipe getHandle() {
    return handle;
  }

  private static <T> Collection<T> withoutNulls(Collection<T> list) {
    list.removeIf(Objects::isNull);
    return list;
  }
}
