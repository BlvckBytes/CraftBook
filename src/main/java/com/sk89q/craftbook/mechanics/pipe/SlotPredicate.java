package com.sk89q.craftbook.mechanics.pipe;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface SlotPredicate {

  boolean test(int slot, @Nullable ItemStack contents);

}
