package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.util.ItemUtil;
import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class PipePredicateEvent extends BlockEvent {

  private static final HandlerList handlers = new HandlerList();

  private final List<ItemStack> includeFilters;
  private final List<ItemStack> excludeFilters;

  private @Nullable Predicate<ItemStack> predicate;

  public PipePredicateEvent(Block theBlock, List<ItemStack> includeFilters, List<ItemStack> excludeFilters) {
    super(theBlock);

    this.includeFilters = includeFilters;
    this.excludeFilters = excludeFilters;
  }

  public boolean testItem(@Nullable ItemStack item) {
    if (!ItemUtil.isStackValid(item))
      return false;

    if (this.predicate == null)
      return doesItemPassFilters(item);

    return this.predicate.test(item);
  }

  public List<ItemStack> getIncludeFilters() {
    return includeFilters;
  }

  public List<ItemStack> getExcludeFilters() {
    return excludeFilters;
  }

  public void setPredicate(@Nullable Predicate<ItemStack> predicate) {
    this.predicate = predicate;
  }

  public @Nullable Predicate<ItemStack> getPredicate() {
    return predicate;
  }

  @Override
  @NotNull
  public HandlerList getHandlers() {
    return handlers;
  }

  @NotNull
  public static HandlerList getHandlerList() {
    return handlers;
  }

  private boolean doesItemPassFilters(ItemStack stack) {
    for (ItemStack includeFilter : includeFilters) {
      if (!ItemUtil.isStackValid(includeFilter))
        continue;

      if (!includeFilter.isSimilar(stack))
        return false;
    }

    for (ItemStack excludeFilter : excludeFilters) {
      if (!ItemUtil.isStackValid(excludeFilter))
        continue;

      if (excludeFilter.isSimilar(stack))
        return false;
    }

    return true;
  }
}
