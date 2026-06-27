package com.sk89q.craftbook.mechanics.pipe;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PipeFinishEvent extends PipeEvent {

    private static final HandlerList handlers = new HandlerList();

    private final Block origin;
    private final int cachedOrigin;

    public PipeFinishEvent(Block theBlock, List<ItemStack> items, Block origin, int cachedOrigin) {
        super(theBlock, items);
        this.origin = origin;
        this.cachedOrigin = cachedOrigin;
    }

    public Block getOrigin() {
        return origin;
    }

    public int getCachedOrigin() {
        return cachedOrigin;
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