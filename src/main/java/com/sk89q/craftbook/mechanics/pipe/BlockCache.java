package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.pipe.notification.PipeNotification;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Chest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class BlockCache implements CachedBlockResolver {

    private static final int CHUNK_BUCKET_DIMENSION = 32;
    private static final int CHUNK_BUCKET_SIZE = CHUNK_BUCKET_DIMENSION * CHUNK_BUCKET_DIMENSION * CHUNK_BUCKET_DIMENSION;
    private static final int CHUNK_BUCKET_MASK = CHUNK_BUCKET_DIMENSION - 1;
    private static final int CHUNK_BUCKET_BIT_COUNT = Integer.numberOfTrailingZeros(CHUNK_BUCKET_DIMENSION);

    private static final BlockFace[] DIRECT_FACES = new BlockFace[] {
      BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final BlockCacheRegistry registry;

    private final Long2ObjectMap<ChunkTicket> chunkTicketByCompactId;
    private final Long2ObjectMap<int[]> cachedBlockByRelativeIdByChunkBucketId;
    private final Long2ObjectMap<PipeSign> pipeSignByPistonCompactId;
    private final Long2ObjectMap<BukkitTask> tempPowerResetTaskByCompactId;

    private int cacheLoadCounter = 0;

    public BlockCache(BlockCacheRegistry registry) {
        this.registry = registry;

        this.chunkTicketByCompactId = new Long2ObjectOpenHashMap<>();
        this.cachedBlockByRelativeIdByChunkBucketId = new Long2ObjectOpenHashMap<>();
        this.pipeSignByPistonCompactId = new Long2ObjectOpenHashMap<>(15_000, .5f);
        this.tempPowerResetTaskByCompactId = new Long2ObjectOpenHashMap<>();
    }

    public void temporarilyPowerBlock(Block block, int ticks) {
        if (!setBlockPower(block, true))
            return;

        var compactId = CompactId.computeWorldlessBlockId(block);

        var resetTask = Bukkit.getScheduler().runTaskLater(CraftBookPlugin.inst(), () -> {
            tempPowerResetTaskByCompactId.remove(compactId);
            setBlockPower(block, false);
        }, ticks);

        var priorResetTask = tempPowerResetTaskByCompactId.put(compactId, resetTask);

        if (priorResetTask != null)
            priorResetTask.cancel();
    }

    public void expireChunkTickets(boolean force) {
        var now = registry.getRelativeTimeTicks();

        for (var chunkTicket : chunkTicketByCompactId.values())
            chunkTicket.handleExpiration(now, force);
    }

    public void disable() {
        this.cachedBlockByRelativeIdByChunkBucketId.clear();
        this.pipeSignByPistonCompactId.clear();
        expireChunkTickets(true);
    }

    public void resetCacheLoadCounter() {
        cacheLoadCounter = 0;
    }

    public int getCacheLoadCounter() {
        return cacheLoadCounter;
    }

    public void invalidateCache(Block block) {
        invalidateCacheForSingleBlock(block);

        // Signs are cached by their corresponding piston's position, so the following
        // tries to resolve that mounted-on block to then invalidate the pipe-sign.

        BlockData data = block.getBlockData();

        if (data instanceof org.bukkit.block.data.type.Sign) {
            // Since, unfortunately, pipe-signs are accepted above and below, we have to invalidate both possibilities
            invalidateSignBlock(block, BlockFace.UP);
            invalidateSignBlock(block, BlockFace.DOWN);
            return;
        }

        if (data instanceof WallSign wallSign)
            invalidateSignBlock(block, wallSign.getFacing().getOppositeFace());

        if (data instanceof Chest chest) {
            var otherChestBlock = CachedBlock.getOtherChestBlock(block, chest.getType(), chest.getFacing());

            if (otherChestBlock != null)
                invalidateCacheForSingleBlock(otherChestBlock);
        }
    }

    private void invalidateCacheForSingleBlock(Block block) {
        var chunkBucket = cachedBlockByRelativeIdByChunkBucketId.get(computeChunkBucketId(block));

        if (chunkBucket != null)
            chunkBucket[computeRelativeId(block)] = CachedBlock.NULL_SENTINEL;
    }

    private void invalidateSignBlock(Block signBlock, BlockFace mountingFace) {
        Block pistonBlock = signBlock.getRelative(mountingFace);

        if (pipeSignByPistonCompactId.remove(CompactId.computeWorldlessBlockId(pistonBlock)) != null)
            Bukkit.getPluginManager().callEvent(new PipeSignCacheInvalidedEvent(pistonBlock));
    }

    @Override
    public int getCachedBlock(Block block) throws LoadingChunkException {
        return getCachedBlock(block, true);
    }

    public int getCachedBlock(Block block, boolean doTouchChunkTickets) throws LoadingChunkException {
        var bucketId = computeChunkBucketId(block);

        var chunkBucket = cachedBlockByRelativeIdByChunkBucketId.computeIfAbsent(bucketId, key -> {
            var newBucket = new int[CHUNK_BUCKET_SIZE];
            Arrays.fill(newBucket, CachedBlock.NULL_SENTINEL);
            return newBucket;
        });

        var relativeId = computeRelativeId(block);
        var cachedBlock = chunkBucket[relativeId];

        if (cachedBlock != CachedBlock.NULL_SENTINEL) {
            if (!doTouchChunkTickets)
                return cachedBlock;

            if (CachedBlock.shouldContinueToRetainChunks(cachedBlock))
                ensureChunkIsLoaded(block, () -> addOrTouchChunkTicket(block, false));
            else
                addOrTouchChunkTicket(block, true);

            return cachedBlock;
        }

        ensureChunkIsLoaded(block, () -> {
            var loadedBlock = CachedBlock.fromBlock(block);

            // Do not cache this intermediate state - it can trip the whole system up.
            // Let's simply get the real state from the world until it finalized.
            if (!CachedBlock.isMaterial(loadedBlock, Material.MOVING_PISTON)) {
                chunkBucket[relativeId] = loadedBlock;
                ++cacheLoadCounter;
            }

            if (doTouchChunkTickets)
                addOrTouchChunkTicket(block, false);
        });

        return chunkBucket[relativeId];
    }

    public PipeSign getSignOnPiston(Block pistonBlock, int cachedPistonBlock, @Nullable List<PipeNotification> notificationOutput) throws LoadingChunkException {
        long pistonCompactId = CompactId.computeWorldlessBlockId(pistonBlock);

        PipeSign cachedSign = pipeSignByPistonCompactId.get(pistonCompactId);

        if (cachedSign != null)
            return cachedSign;

        BlockFace facing = CachedBlock.getFacing(cachedPistonBlock);

        for (BlockFace face : DIRECT_FACES) {
            if (face == facing)
                continue;

            Block faceBlock = pistonBlock.getRelative(face);
            int cachedFaceBlock = getCachedBlock(faceBlock);

            if (CachedBlock.isStandingSign(cachedFaceBlock)) {
                // Standing-signs may only be on or under the piston
                if (face != BlockFace.UP && face != BlockFace.DOWN)
                    continue;
            } else if (CachedBlock.isWallSign(cachedFaceBlock)) {
                // Wall-signs may only be attached N/E/S/W on the piston
                if (face == BlockFace.UP || face == BlockFace.DOWN)
                    continue;

                // The sign has to be mounted on this piston, not on an adjacent one
                if (CachedBlock.getFacing(cachedFaceBlock) != face)
                    continue;
            } else {
                // Not a sign at all, do not needlessly try to get its state
                continue;
            }

            if (!(faceBlock.getState() instanceof Sign sign))
                continue;

            String[] lines = sign.getLines();

            if (!lines[1].equalsIgnoreCase("[Pipe]"))
                continue;

            cachedSign = PipeSign.fromSign(sign, lines, notificationOutput);
            Bukkit.getPluginManager().callEvent(new PipeSignCacheCreatedEvent(pistonBlock, sign, lines));
            break;
        }

        if (cachedSign == null)
            cachedSign = PipeSign.NO_SIGN;

        pipeSignByPistonCompactId.put(pistonCompactId, cachedSign);

        return cachedSign;
    }

    public void onItemsCarryingPipeStart(Block block) {
        accessChunkTicket(block).onPipeStart();
    }

    private ChunkTicket accessChunkTicket(Block block) {
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        var compactChunkId = CompactId.computeWorldlessChunkId(chunkX, chunkZ);

        return chunkTicketByCompactId.computeIfAbsent(compactChunkId, k -> new ChunkTicket());
    }

    private void ensureChunkIsLoaded(Block block, @Nullable Runnable whenLoadedHandler) throws LoadingChunkException {
        int chunkX = block.getX() >> 4;
        int chunkZ = block.getZ() >> 4;
        World world = block.getWorld();

        if (world.isChunkLoaded(chunkX, chunkZ)) {
            if (whenLoadedHandler != null)
                whenLoadedHandler.run();

            return;
        }

        world.getChunkAtAsync(chunkX, chunkZ, true, chunk -> {
            if (whenLoadedHandler != null)
                whenLoadedHandler.run();
        });

        throw new LoadingChunkException();
    }

    private void addOrTouchChunkTicket(Block block, boolean doNotCreate) {
        var chunkTicket = accessChunkTicket(block);

        if (chunkTicket.hasChunkSet()) {
            if (registry.getContinuedChunkTicketDurationTicks() > 0)
                chunkTicket.expiryTicksStamp = registry.getRelativeTimeTicks() + registry.getContinuedChunkTicketDurationTicks();

            return;
        }

        if (doNotCreate || registry.getInitialChunkTicketDurationTicks() <= 0)
            return;

        chunkTicket.setChunk(block.getChunk());
        chunkTicket.expiryTicksStamp = registry.getRelativeTimeTicks() + registry.getInitialChunkTicketDurationTicks();
    }

    private boolean setBlockPower(Block block, boolean state) {
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4))
            return false;

        var blockData = block.getBlockData();

        if (!(blockData instanceof Powerable powerable))
            return false;

        // No-op; no need to overwrite an already reflected state.
        if (powerable.isPowered() == state)
            return true;

        powerable.setPowered(state);
        block.setBlockData(powerable);
        return true;
    }

    private long computeChunkBucketId(Block block) {
        return CompactId.computeWorldlessBlockId(
          block.getX() >> CHUNK_BUCKET_BIT_COUNT,
          block.getY() >> CHUNK_BUCKET_BIT_COUNT,
          block.getZ() >> CHUNK_BUCKET_BIT_COUNT
        );
    }

    private int computeRelativeId(Block block) {
        return (
          ((block.getZ() & CHUNK_BUCKET_MASK) << 2 * CHUNK_BUCKET_BIT_COUNT)
            | ((block.getY() & CHUNK_BUCKET_MASK) << CHUNK_BUCKET_BIT_COUNT)
            | (block.getX() & CHUNK_BUCKET_MASK)
        );
    }
}
