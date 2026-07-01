package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.util.ItemUtil;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public class PipeItems {

  private static class ItemAndOriginSlot {
    private final int originSlot;
    private @Nullable ItemStack item;
    private boolean reduced;

    private ItemAndOriginSlot(int originSlot, @NotNull ItemStack item) {
      this.originSlot = originSlot;
      this.item = item;
    }
  }

  private final List<ItemAndOriginSlot> contents;
  private final AtomicBoolean reducedAny;

  private PipeItems(List<ItemAndOriginSlot> contents, AtomicBoolean reducedAny) {
    this.contents = contents;
    this.reducedAny = reducedAny;
  }

  public PipeItems() {
    this(new ArrayList<>(), new AtomicBoolean());
  }

  public boolean addIfNonDuplicate(int originSlot, @NotNull ItemStack item) {
    if (!ItemUtil.isStackValid(item))
      return false;

    for (var itemAndSlot : contents) {
      if (item.isSimilar(itemAndSlot.item))
        return false;
    }

    contents.add(new ItemAndOriginSlot(originSlot, item));
    return true;
  }

  public boolean isEmpty() {
    return contents.stream().noneMatch(this::isActive);
  }

  private boolean isActive(ItemAndOriginSlot itemAndSlot) {
    // Once we reduced an item, it from now on becomes the only active content of the pipe.
    // We merely carried all others along the way to try and avoid stalling due to an
    // incompatible item occupying the first slot of an input-container.
    return (!reducedAny.get() || itemAndSlot.reduced) && ItemUtil.isStackValid(itemAndSlot.item);
  }

  public void forEachActiveItemAndBreakAfterReduce(PipeItemReduceHandler handler) {
    for (var itemAndSlot : contents) {
      if (!isActive(itemAndSlot))
        continue;

      assert itemAndSlot.item != null;

      var previousAmount = itemAndSlot.item.getAmount();
      var remainingAmount = handler.handleAndGetRemainingAmount(itemAndSlot.originSlot, itemAndSlot.item);
      var newAmount = Math.min(previousAmount, remainingAmount);

      if (newAmount == previousAmount)
        continue;

      itemAndSlot.item.setAmount(newAmount);
      itemAndSlot.reduced = true;
      reducedAny.set(true);

      if (newAmount <= 0)
        itemAndSlot.item = null;

      break;
    }
  }

  public void forEachRemainingItem(PipeItemViewHandler handler) {
    for (var itemAndSlot : contents) {
      if (ItemUtil.isStackValid(itemAndSlot.item))
        handler.handle(itemAndSlot.originSlot, itemAndSlot.item);
    }
  }

  public PipeItems filterAndMakeSub(Predicate<ItemStack> predicate) {
    var filteredContents = new ArrayList<ItemAndOriginSlot>(contents.size());

    for (var itemAndSlot : contents) {
      if (!isActive(itemAndSlot))
        continue;

      if (predicate.test(itemAndSlot.item))
        filteredContents.add(itemAndSlot);
    }

    return new PipeItems(filteredContents, reducedAny);
  }
}
