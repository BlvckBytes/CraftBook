package com.sk89q.craftbook.util;

import org.bukkit.block.Crafter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class WrappedInventory implements GenericInventory {

  private final @Nullable InventoryHolder holder;
  private final Inventory inventory;

  public WrappedInventory(@Nullable InventoryHolder holder, Inventory inventory) {
    this.holder = holder;
    this.inventory = inventory;
  }

  @Override
  public boolean isSlotDisabled(int slot) {
    if (!(holder instanceof Crafter crafter))
      return false;

    return crafter.isSlotDisabled(slot);
  }

  @Override
  public ItemStack[] getContents() {
    return inventory.getContents();
  }

  @Override
  public void set(int slot, ItemStack item) {
    inventory.setItem(slot, item);
  }

  @Override
  public void set(NamedSlot namedSlot, ItemStack item) {
    inventory.setItem(namedSlot.slot, item);
  }

  @Override
  public @Nullable ItemStack get(int slot) {
    return inventory.getItem(slot);
  }

  @Override
  public @Nullable ItemStack get(NamedSlot namedSlot) {
    return inventory.getItem(namedSlot.slot);
  }

  @Override
  public Collection<ItemStack> addAndGetRemainders(ItemStack item) {
    return inventory.addItem(item).values();
  }
}
