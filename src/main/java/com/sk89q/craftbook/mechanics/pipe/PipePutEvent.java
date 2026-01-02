package com.sk89q.craftbook.mechanics.pipe;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PipePutEvent extends PipeEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Block put;
    private final int cachedPut;

    public PipePutEvent(Block theBlock, List<ItemStack> items, Block put, int cachedPut) {
        super(theBlock, items);
        this.put = put;
        this.cachedPut = cachedPut;
    }

    public int getCachedPuttingBlock() {
        return cachedPut;
    }

    public Block getPuttingBlock() {

        return put;
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
