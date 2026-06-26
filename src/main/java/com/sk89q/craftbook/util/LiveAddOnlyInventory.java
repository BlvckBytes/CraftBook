package com.sk89q.craftbook.util;

import org.bukkit.block.Crafter;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class LiveAddOnlyInventory extends AddOnlyInventory {

  private final @Nullable InventoryHolder inventoryHolder;

  public LiveAddOnlyInventory(Inventory inventory) {
    super(inventory);
    this.inventoryHolder = null;
  }

  public LiveAddOnlyInventory(InventoryHolder inventoryHolder) {
    super(inventoryHolder.getInventory());
    this.inventoryHolder = inventoryHolder;
  }

  @Override
  public boolean isSlotDisabled(int slot) {
    if (!(inventoryHolder instanceof Crafter crafter))
      return false;

    return crafter.isSlotDisabled(slot);
  }

  @Override
  protected void onAddition(ItemStack addedItem, int addedAmount) {}
}
