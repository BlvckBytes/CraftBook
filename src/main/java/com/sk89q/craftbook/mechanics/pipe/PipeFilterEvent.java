package com.sk89q.craftbook.mechanics.pipe;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PipeFilterEvent extends PipeEvent {

    private static final HandlerList handlers = new HandlerList();

    private final List<ItemStack> includeFilters;
    private final List<ItemStack> excludeFilters;
    private List<ItemStack> filteredItems;

    public PipeFilterEvent(Block theBlock, List<ItemStack> items, List<ItemStack> includeFilters, List<ItemStack> excludeFilters, List<ItemStack> filteredItems) {
        super(theBlock, items);

        this.includeFilters = includeFilters;
        this.excludeFilters = excludeFilters;
        this.filteredItems = filteredItems;
    }

    public List<ItemStack> getIncludeFilters() {
        return includeFilters;
    }

    public List<ItemStack> getExcludeFilters() {
        return excludeFilters;
    }

    public List<ItemStack> getFilteredItems() {
        return filteredItems;
    }

    public void setFilteredItems(List<ItemStack> filteredItems) {
        this.filteredItems = filteredItems;
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
}
