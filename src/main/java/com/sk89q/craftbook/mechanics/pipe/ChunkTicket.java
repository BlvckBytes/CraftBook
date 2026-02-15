package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import org.bukkit.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;

public class ChunkTicket {

    private @Nullable Chunk chunk;
    private boolean didStartPipe;

    public long expiryTicksStamp;

    public boolean hasChunkSet() {
        return chunk != null;
    }

    public void setChunk(@NotNull Chunk chunk) {
        if (this.chunk != null)
            throw new IllegalStateException("Tried to override an already set chunk");

        this.chunk = chunk;
        this.expiryTicksStamp = 0;
        this.didStartPipe = false;

        if (!chunk.addPluginChunkTicket(CraftBookPlugin.inst()))
            CraftBookPlugin.logger().log(Level.WARNING, "Could not add plugin-ticket to chunk at " + chunk.getX() + " " + chunk.getZ());
    }

    public void onPipeStart() {
        didStartPipe = true;
    }

    public void handleExpiration(int ticksNow, boolean force) {
        if (chunk == null)
            return;

        if (!force && !didStartPipe && ticksNow < expiryTicksStamp)
            return;

        if (!chunk.removePluginChunkTicket(CraftBookPlugin.inst()))
            CraftBookPlugin.logger().log(Level.WARNING, "Could not remove plugin-ticket from chunk at " + chunk.getX() + " " + chunk.getZ());

        chunk = null;
    }
}
