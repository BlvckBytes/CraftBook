package com.sk89q.craftbook.util;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface GenericInventory {

  boolean isSlotDisabled(int slot);

  ItemStack[] getContents();

  void set(int slot, ItemStack item);

  void set(NamedSlot namedSlot, ItemStack item);

  @Nullable ItemStack get(int slot);

  @Nullable ItemStack get(NamedSlot namedSlot);

  Collection<ItemStack> addAndGetRemainders(ItemStack item);

}
