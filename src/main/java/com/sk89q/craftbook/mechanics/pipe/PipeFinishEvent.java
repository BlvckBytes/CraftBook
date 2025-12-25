package com.sk89q.craftbook.mechanics.pipe;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

public class PipeFinishEvent extends PipeEvent {

    private Block origin;
    private int cachedOrigin;

    private boolean request;

    public PipeFinishEvent(Block theBlock, List<ItemStack> items, Block origin, int cachedOrigin, boolean request) {
        super(theBlock, items);
        this.origin = origin;
        this.cachedOrigin = cachedOrigin;
        this.request = request;
    }

    public Block getOrigin() {
        return origin;
    }

    public int getCachedOrigin() {
        return cachedOrigin;
    }

    public boolean isRequest() {
        return request;
    }
}