package com.sk89q.craftbook.mechanics.pipe;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PipeSuckEvent extends PipeEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Block sucked;
    private final int cachedSucked;

    public PipeSuckEvent(Block theBlock, List<ItemStack> items, Block sucked, int cachedSucked) {
        super(theBlock, items);
        this.sucked = sucked;
        this.cachedSucked = cachedSucked;
    }

    public int getCachedSuckedBlock() {
        return cachedSucked;
    }

    public Block getSuckedBlock() {
        return sucked;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }

    @Override
    public void setCancelled(boolean arg0) {
        isCancelled = arg0;
    }

    private boolean isCancelled = false;

    public boolean isValid() {
        return !isCancelled && !getItems().isEmpty();
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