package com.sk89q.craftbook.mechanics.pipe;

public enum ChunkLoadReason {
  UPDATE_BLOCK_CACHE(30),
  ACCESS_BLOCK_INVENTORY(50),
  ;

  public final long expiryTimeTicks;

  ChunkLoadReason(long expiryTimeTicks) {
    this.expiryTimeTicks = expiryTimeTicks;
  }
}
