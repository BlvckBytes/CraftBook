package com.sk89q.craftbook.mechanics.pipe;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PipeRequestEvent extends PipeSuckEvent {

    private static final HandlerList handlers = new HandlerList();

    public PipeRequestEvent(Block theBlock, List<ItemStack> items, Block sucked) {
        super(theBlock, items, sucked, CachedBlock.NULL_SENTINEL);
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