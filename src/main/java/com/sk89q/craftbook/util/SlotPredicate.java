package com.sk89q.craftbook.util;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface SlotPredicate {

  boolean test(int slot, @Nullable ItemStack contents);

}
