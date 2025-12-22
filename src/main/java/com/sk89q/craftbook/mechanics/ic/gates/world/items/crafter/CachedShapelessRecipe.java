package com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter;

import com.sk89q.craftbook.util.VerifyUtil;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.Collection;
import java.util.Collections;

public class CachedShapelessRecipe implements CachedRecipe {

  private final Recipe handle;

  public final Collection<ItemStack> cachedIngredientList;
  public final String cachedKey;

  CachedShapelessRecipe(ShapelessRecipe handle) {
    this.handle = handle;
    this.cachedIngredientList = Collections.unmodifiableCollection(
      VerifyUtil.withoutNulls(handle.getIngredientList())
    );
    this.cachedKey = handle.getKey().getKey();
  }

  @Override
  public Recipe getHandle() {
    return handle;
  }
}
