package com.sk89q.craftbook.mechanics.pipe;

import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;

public abstract class PipeEvent extends BlockEvent {

    private List<ItemStack> items;

    public PipeEvent(Block theBlock, List<ItemStack> items) {
        super(theBlock);
        this.items = items;
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public void setItems(List<ItemStack> items) {
        this.items = items;
    }

    public void addItems(List<ItemStack> items) {
        this.items.addAll(items);
    }
}