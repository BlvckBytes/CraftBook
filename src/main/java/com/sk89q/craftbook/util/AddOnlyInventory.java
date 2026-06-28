package com.sk89q.craftbook.util;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class AddOnlyInventory {

  // NOTE: We intentionally *never* store an item by reference, as to absolutely avoid
  //       untracked mutations. This is far more important than the little allocation.

  private final Inventory inventory;
  private final int size;

  protected AddOnlyInventory(Inventory inventory) {
    this.inventory = inventory;
    this.size = inventory.getSize();
  }

  protected abstract void onAddition(ItemStack addedItem, int addedAmount);

  public abstract boolean isSlotDisabled(int slot);

  public int getSize() {
    return size;
  }

  public int addItemAndGetAddedAmount(ItemStack itemToAdd, int amountToAdd) {
    var addedItem = new ItemStack(itemToAdd);
    addedItem.setAmount(amountToAdd);

    var remainderByParameterIndex = inventory.addItem(addedItem);

    if (remainderByParameterIndex.isEmpty()) {
      onAddition(itemToAdd, amountToAdd);
      return amountToAdd;
    }

    var valueIterator = remainderByParameterIndex.values().iterator();
    var remainder = valueIterator.next();

    if (valueIterator.hasNext())
      throw new IllegalStateException("We only provided one parameter, so there can be at most one map-entry");

    var remainingAmount = remainder.getAmount();

    if (remainingAmount >= amountToAdd)
      return 0;

    var addedAmount = amountToAdd - remainingAmount;

    onAddition(itemToAdd, addedAmount);

    return addedAmount;
  }

  public int addItemToSlotAndGetAddedAmount(int slot, ItemStack itemToAdd, int amountToAdd) {
    return addItemToSlotAndGetAddedAmount(slot, itemToAdd, amountToAdd, 0);
  }

  public int addItemToSlotAndGetAddedAmount(int slot, ItemStack itemToAdd, int amountToAdd, int stackSizeOverride) {
    var currentItem = inventory.getItem(slot);

    if (!ItemUtil.isStackValid(currentItem)) {
      var newItem = new ItemStack(itemToAdd);
      newItem.setAmount(amountToAdd);

      inventory.setItem(slot, newItem);
      onAddition(itemToAdd, amountToAdd);

      return amountToAdd;
    }

    if (!currentItem.isSimilar(itemToAdd))
      return 0;

    var stackSize = currentItem.getMaxStackSize();

    if (stackSizeOverride > 0)
      stackSize = stackSizeOverride;

    var remainingSpace = stackSize - currentItem.getAmount();

    if (remainingSpace <= 0)
      return 0;

    var addedAmount = Math.min(remainingSpace, amountToAdd);

    currentItem.setAmount(currentItem.getAmount() + addedAmount);
    onAddition(itemToAdd, addedAmount);

    return addedAmount;
  }

  public @Nullable Integer getAmountIfIsSimilarOrVacant(int slot, ItemStack item) {
    var currentItem = inventory.getItem(slot);

    if (!ItemUtil.isStackValid(currentItem))
      return 0;

    if (!currentItem.isSimilar(item))
      return null;

    return currentItem.getAmount();
  }
}
