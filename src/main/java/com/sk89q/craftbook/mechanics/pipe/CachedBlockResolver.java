package com.sk89q.craftbook.mechanics.pipe;

import org.bukkit.block.Block;

public interface CachedBlockResolver {

  int getCachedBlock(Block block) throws LoadingChunkException;

}
