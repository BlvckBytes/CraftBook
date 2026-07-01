package com.sk89q.craftbook.mechanics.pipe;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ItemAndOriginSlot {

  public final int originSlot;
  public @Nullable ItemStack item;

  public ItemAndOriginSlot(int originSlot, @NotNull ItemStack item) {
    this.originSlot = originSlot;
    this.item = item;
  }
}
