package com.sk89q.craftbook.mechanics.pipe;

import org.bukkit.Chunk;

public class ChunkTicket {

    public final Chunk chunk;
    public long expiryTicksStamp;

    public ChunkTicket(Chunk chunk, long expiryTicksStamp) {
        this.chunk = chunk;
        this.expiryTicksStamp = expiryTicksStamp;
    }
}
