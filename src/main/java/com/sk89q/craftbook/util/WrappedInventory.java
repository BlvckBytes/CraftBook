package com.sk89q.craftbook.util;

import org.bukkit.block.Crafter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class WrappedInventory extends GenericInventory {

  private final @Nullable InventoryHolder holder;
  private final Inventory inventory;
  private final int size;

  public WrappedInventory(@Nullable InventoryHolder holder, Inventory inventory) {
    this.holder = holder;
    this.inventory = inventory;
    this.size = inventory.getSize();
  }

  @Override
  public boolean isSlotDisabled(int slot) {
    if (!(holder instanceof Crafter crafter))
      return false;

    return crafter.isSlotDisabled(slot);
  }

  @Override
  public int getSize() {
    return size;
  }

  @Override
  public void set(int slot, ItemStack item) {
    inventory.setItem(slot, item);
  }

  @Override
  public @Nullable ItemStack get(int slot) {
    return inventory.getItem(slot);
  }
}
