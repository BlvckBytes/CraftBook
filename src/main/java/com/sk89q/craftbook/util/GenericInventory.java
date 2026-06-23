package com.sk89q.craftbook.util;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class GenericInventory {

  public abstract boolean isSlotDisabled(int slot);

  public abstract ItemStack[] getContents();

  public abstract void set(int slot, ItemStack item);

  public abstract void set(NamedSlot namedSlot, ItemStack item);

  public abstract @Nullable ItemStack get(int slot);

  public abstract @Nullable ItemStack get(NamedSlot namedSlot);

  public @Nullable ItemStack addAndGetRemainder(ItemStack item) {
    var remainingAmount = addItemToInventoryAndGetRemainingAmount(item);

    if (remainingAmount <= 0)
      return null;

    var remainder = new ItemStack(item);

    remainder.setAmount(remainingAmount);

    return remainder;
  }

  private int addItemToInventoryAndGetRemainingAmount(ItemStack itemToAdd) {
    var firstVacantSlotIndex = -1;
    var remainingAmount = itemToAdd.getAmount();

    var contents = getContents();

    // 1. Fill up all partial stacks

    for (var slotIndex = 0; slotIndex < contents.length; ++slotIndex) {
      var currentItem = contents[slotIndex];

      if (currentItem == null || currentItem.getType().isAir()) {
        if (firstVacantSlotIndex < 0)
          firstVacantSlotIndex = slotIndex;

        continue;
      }

      if (!itemToAdd.isSimilar(currentItem))
        continue;

      var remainingSpace = currentItem.getMaxStackSize() - currentItem.getAmount();

      if (remainingSpace <= 0)
        continue;

      var amountToAdd = Math.min(remainingAmount, remainingSpace);

      currentItem.setAmount(currentItem.getAmount() + amountToAdd);

      remainingAmount -= amountToAdd;

      if (remainingAmount <= 0)
        return 0;
    }

    if (remainingAmount <= 0)
      return 0;

    // There's no need to once more scan for more vacant slots, as there aren't any left.
    if (firstVacantSlotIndex < 0)
      return remainingAmount;

    // 2. Since there's still some left to add, put as much as possible into the first vacant
    //    slot we've discovered while scanning for partial stacks.

    var remainder = new ItemStack(itemToAdd);

    var remainderAmount = Math.min(itemToAdd.getMaxStackSize(), remainingAmount);
    remainder.setAmount(remainderAmount);

    set(firstVacantSlotIndex, remainder);
    remainingAmount -= remainderAmount;

    if (remainingAmount <= 0)
      return 0;

    // 3. If there's still more, we need to fill up more than one vacant slot. Seeing how that's
    //    a rather seldom, special case, we don't keep a list of vacant slots but rather just
    //    iterate again - that's plenty fast.

    for (var slotIndex = 0; slotIndex < contents.length; ++slotIndex) {
      var currentItem = contents[slotIndex];

      if (currentItem == null || currentItem.getType().isAir()) {
        remainder = new ItemStack(itemToAdd);

        remainderAmount = Math.min(itemToAdd.getMaxStackSize(), remainingAmount);
        remainder.setAmount(remainderAmount);

        set(slotIndex, remainder);
        remainingAmount -= remainderAmount;

        if (remainingAmount <= 0)
          return 0;
      }
    }

    return remainingAmount;
  }
}
