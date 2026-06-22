package com.sk89q.craftbook.util;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.blocks.BaseItem;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * The Standard Item Syntax. This class is built to be able to survive on its own, without CraftBook.
 *
 * @author Me4502
 *
 */
public final class ItemSyntax {

    private static final ParserContext ITEM_CONTEXT = new ParserContext();

    static {
        ITEM_CONTEXT.setPreferringWildcard(true);
        ITEM_CONTEXT.setRestricted(false);
    }

    private static final LoadingCache<String, ItemStack> itemCache = CacheBuilder.newBuilder().maximumSize(1024).expireAfterAccess(10, TimeUnit.MINUTES).build(new CacheLoader<>() {

        @Override
        public @NotNull ItemStack load(@NotNull String line) {
            BaseItem item = null;

            try {
                item = WorldEdit.getInstance().getItemFactory().parseFromInput(line, ITEM_CONTEXT);
            } catch (InputParseException ignored) {}

            ItemStack result;

            if (item == null || item.getType() == null) {
                result = new ItemStack(Material.STONE);
            } else {
                result = BukkitAdapter.adapt(new BaseItemStack(item.getType()));
            }

            return result;
        }
    });

    public static ItemStack getItem(String line) {
        if (line == null || line.isEmpty())
            return null;

        return itemCache.getUnchecked(line);
    }
}
