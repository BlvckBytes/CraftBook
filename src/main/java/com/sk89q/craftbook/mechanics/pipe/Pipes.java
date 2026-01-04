package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.AbstractCraftBookMechanic;
import com.sk89q.craftbook.CraftBookPlayer;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.pipe.notification.*;
import com.sk89q.craftbook.util.*;
import com.sk89q.craftbook.util.events.SourcedBlockRedstoneEvent;
import com.sk89q.util.yaml.YAMLProcessor;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.type.Piston;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.HopperInventorySearchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class Pipes extends AbstractCraftBookMechanic implements PipesApi {

    private int currentTubeBlockCounter;
    private int currentPistonBlockCounter;

    private final BlockCacheRegistry cacheRegistry;

    private BlockCache currentBlockCache;

    private final NotificationDebouncer notificationDebouncer;

    public Pipes() {
        this.cacheRegistry = new BlockCacheRegistry();
        this.notificationDebouncer = new NotificationDebouncer();
    }

    @Override
    public void disable() {
        super.disable();

        cacheRegistry.disable();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {

        if (!EventUtil.passesFilter(event)) return;

        if (!event.getLine(1).equalsIgnoreCase("[pipe]")) return;

        CraftBookPlayer player = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());

        if (!player.hasPermission("craftbook.circuits.pipes")) {
            if (CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                player.printError("mech.create-permission");
            SignUtil.cancelSign(event);
            return;
        }

        if (ProtectionUtil.shouldUseProtection()) {
            Block pistonBlock = null;

            if (SignUtil.isWallSign(event.getBlock())) {
                pistonBlock = SignUtil.getBackBlock(event.getBlock());
            } else if (SignUtil.isStandingSign(event.getBlock())) {
                if (isPiston(event.getBlock().getRelative(BlockFace.DOWN))) {
                    pistonBlock = event.getBlock().getRelative(BlockFace.DOWN);
                } else if (isPiston(event.getBlock().getRelative(BlockFace.UP))) {
                    pistonBlock = event.getBlock().getRelative(BlockFace.UP);
                }
            }
            if (pistonBlock != null && isPiston(pistonBlock)) {
                Piston pis = (Piston) pistonBlock.getBlockData();
                Block off = pistonBlock.getRelative(pis.getFacing());
                if (InventoryUtil.doesBlockHaveInventory(off)) {
                    if (!ProtectionUtil.canAccessInventory(event.getPlayer(), off)) {
                        if (CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                            player.printError("area.use-permission");
                        SignUtil.cancelSign(event);
                        return;
                    }
                }
            } else {
                player.printError("circuits.pipes.pipe-not-found");
                SignUtil.cancelSign(event);
                return;
            }
        }

        event.setLine(1, "[Pipe]");
        player.print("circuits.pipes.create");
    }

    private static boolean isPiston(Block block) {
        Material type = block.getType();
        return type == Material.PISTON || type == Material.STICKY_PISTON;
    }

    private EnumerationResult locateExitNodesForItems(Block inputPistonBlock, LongSet visitedBlocks, EnumSet<LocateFlag> flags, List<ItemStack> itemsInPipe, List<PipeNotification> notification) {
        // Only reset the limit-counters once, at the very top of the call-stack, seeing
        // how they do apply to the pipe as a whole, including sub-pipes.
        boolean resetCounters = flags.remove(LocateFlag.RESET_COUNTERS);

        return _enumeratePipeBlocks(inputPistonBlock, visitedBlocks, EnumSet.noneOf(EnumerationBehavior.class), resetCounters, (pipeBlock, cachedPipeBlock) -> {
            if (itemsInPipe.isEmpty())
                return EnumerationDecision.STOP;

            if (!CachedBlock.isMaterial(cachedPipeBlock, Material.PISTON))
                return EnumerationDecision.CONTINUE;

            Block putBlock = pipeBlock.getRelative(CachedBlock.getFacing(cachedPipeBlock));
            int cachedPutBlock = currentBlockCache.getCachedBlock(putBlock);
            boolean isSubPipe = CachedBlock.isTube(cachedPutBlock) && !CachedBlock.isPane(cachedPutBlock);

            // Add the sub-pipe tube-block to the visited-set as to avoid it being walked into again
            // by the current enumerator on the next iteration, which would render filters useless.
            if (isSubPipe)
                visitedBlocks.add(CompactId.computeWorldlessBlockId(putBlock));

            PipeSign sign = currentBlockCache.getSignOnPiston(pipeBlock, cachedPipeBlock, notification);

            if (pipeRequireSign) {
                if (sign != PipeSign.NO_SIGN)
                    flags.add(LocateFlag.ENCOUNTERED_SIGN);

                // Do walk into sub-pipes without requiring a sign
                if (!isSubPipe && !flags.contains(LocateFlag.ENCOUNTERED_SIGN))
                    return EnumerationDecision.CONTINUE;
            }

            List<ItemStack> filteredPipeItems = new ArrayList<>(VerifyUtil.withoutNulls(ItemUtil.filterItems(itemsInPipe, sign.includeFilters, sign.excludeFilters)));

            PipeFilterEvent filterEvent = new PipeFilterEvent(pipeBlock, itemsInPipe, sign.includeFilters, sign.excludeFilters, filteredPipeItems);
            Bukkit.getPluginManager().callEvent(filterEvent);

            filteredPipeItems = filterEvent.getFilteredItems();

            if (filteredPipeItems.isEmpty())
                return EnumerationDecision.CONTINUE;

            PipePutEvent putEvent = new PipePutEvent(pipeBlock, new ArrayList<>(filteredPipeItems), putBlock, cachedPutBlock);
            Bukkit.getPluginManager().callEvent(putEvent);

            if (putEvent.isCancelled())
                return EnumerationDecision.CONTINUE;

            List<ItemStack> itemsToPut = putEvent.getItems();
            List<ItemStack> leftovers = new ArrayList<>();

            EnumerationResult subWalkResult = EnumerationResult.COMPLETED;

            if (
                CachedBlock.hasHandledOutputInventory(cachedPutBlock)
                    && putBlock.getState() instanceof InventoryHolder holder
            ) {
                leftovers.addAll(InventoryUtil.addItemsToInventory(holder, itemsToPut));
            } else if (CachedBlock.isMaterial(cachedPutBlock, Material.JUKEBOX)) {
                Jukebox jukebox = (Jukebox) putBlock.getState();

                for (ItemStack item : itemsToPut) {
                    if (jukebox.hasRecord() || !item.getType().isRecord()) {
                        leftovers.add(item);
                        continue;
                    }

                    jukebox.setRecord(item);
                    jukebox.update();
                }
            } else if (isSubPipe) {
                // Handle sub-pipes which continue the walk from here on forwards with a (possibly) limited set of items.
                List<ItemStack> subPipeItems = new ArrayList<>(itemsToPut);
                subWalkResult = locateExitNodesForItems(putBlock, visitedBlocks, flags, subPipeItems, notification);
                leftovers.addAll(subPipeItems);
            } else {
                leftovers.addAll(itemsToPut);
            }

            itemsInPipe.removeAll(filteredPipeItems);
            itemsInPipe.addAll(leftovers);

            if (itemsInPipe.isEmpty() || subWalkResult != EnumerationResult.COMPLETED)
                return EnumerationDecision.STOP;

            return EnumerationDecision.CONTINUE;
        });
    }

    @Override
    public EnumerationResult enumeratePipeBlocks(Block firstBlock, @Nullable LongSet visitedBlocks, EnumSet<EnumerationBehavior> behaviorFlags, PipeEnumerationHandler enumerationHandler) {
        return _enumeratePipeBlocks(firstBlock, visitedBlocks, behaviorFlags, true, enumerationHandler);
    }

    private EnumerationResult _enumeratePipeBlocks(Block firstBlock, @Nullable LongSet visitedBlocks, EnumSet<EnumerationBehavior> behaviorFlags, boolean resetCounters, PipeEnumerationHandler enumerationHandler) {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("This method must be called on the main server thread");

        try {
            // Seeing how this is public API, assign the current block-cache again, because it
            // will only be correctly set when called through #startPipe.
            this.currentBlockCache = cacheRegistry.getBlockCache(firstBlock.getWorld());

            if (visitedBlocks == null)
                visitedBlocks = new LongOpenHashSet();

            if (resetCounters) {
                currentTubeBlockCounter = currentPistonBlockCounter = 0;
                currentBlockCache.resetCacheLoadCounter();
            }

            Deque<Block> searchQueue = new ArrayDeque<>();
            searchQueue.addFirst(firstBlock);
            visitedBlocks.add(CompactId.computeWorldlessBlockId(firstBlock));

            while (!searchQueue.isEmpty()) {
                Block pipeBlock = searchQueue.poll();
                int cachedPipeBlock = currentBlockCache.getCachedBlock(pipeBlock);

                if (CachedBlock.isTube(cachedPipeBlock)) {
                    ++currentTubeBlockCounter;

                    if (maxTubeBlockCount >= 0 && currentTubeBlockCounter > maxTubeBlockCount)
                        return EnumerationResult.EXCEEDED_TUBE_COUNT_LIMIT;
                }

                if (CachedBlock.isMaterial(cachedPipeBlock, Material.PISTON)) {
                    ++currentPistonBlockCounter;

                    if (maxPistonBlockCount >= 0 && currentPistonBlockCounter > maxPistonBlockCount)
                        return EnumerationResult.EXCEEDED_PISTON_COUNT_LIMIT;
                }

                EnumerationDecision handleResult = enumerationHandler.handle(pipeBlock, cachedPipeBlock);

                if (handleResult != EnumerationDecision.CONTINUE)
                    return EnumerationResult.COMPLETED;

                // While we could check for exceeding the load-counter at countless call-sites, and while there already have been
                // a few cache-lookups prior to enumerating, a hand-full blocks more don't matter in the grand scheme of things.
                if (maxCacheLoadCount >= 0 && currentBlockCache.getCacheLoadCounter() >= maxCacheLoadCount)
                    return EnumerationResult.EXCEEDED_CACHE_LOAD_LIMIT;

                for (int x = -1; x < 2; x++) {
                    for (int y = -1; y < 2; y++) {
                        for (int z = -1; z < 2; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;

                            if (!pipesDiagonal) {
                                if (x != 0 && y != 0) continue;
                                if (x != 0 && z != 0) continue;
                                if (y != 0 && z != 0) continue;
                            } else if (pipeInsulator != null) {
                                boolean xIsY = Math.abs(x) == Math.abs(y);
                                boolean xIsZ = Math.abs(x) == Math.abs(z);
                                if (xIsY && xIsZ) {
                                    if (CachedBlock.isMaterial(currentBlockCache.getCachedBlock(pipeBlock.getRelative(x, 0, 0)), pipeInsulator)
                                      && CachedBlock.isMaterial(currentBlockCache.getCachedBlock(pipeBlock.getRelative(0, y, 0)), pipeInsulator)
                                      && CachedBlock.isMaterial(currentBlockCache.getCachedBlock(pipeBlock.getRelative(0, 0, z)), pipeInsulator)) {
                                        continue;
                                    }
                                } else if (xIsY) {
                                    if (CachedBlock.isMaterial(currentBlockCache.getCachedBlock(pipeBlock.getRelative(x, 0, 0)), pipeInsulator)
                                      && CachedBlock.isMaterial(currentBlockCache.getCachedBlock(pipeBlock.getRelative(0, y, 0)), pipeInsulator)) {
                                        continue;
                                    }
                                } else if (xIsZ) {
                                    if (CachedBlock.isMaterial(currentBlockCache.getCachedBlock(pipeBlock.getRelative(x, 0, 0)), pipeInsulator)
                                      && CachedBlock.isMaterial(currentBlockCache.getCachedBlock(pipeBlock.getRelative(0, 0, z)), pipeInsulator)) {
                                        continue;
                                    }
                                } else {
                                    if (CachedBlock.isMaterial(currentBlockCache.getCachedBlock(pipeBlock.getRelative(0, y, 0)), pipeInsulator)
                                      && CachedBlock.isMaterial(currentBlockCache.getCachedBlock(pipeBlock.getRelative(0, 0, z)), pipeInsulator)) {
                                        continue;
                                    }
                                }
                            }

                            Block enumeratedBlock = pipeBlock.getRelative(x, y, z);
                            int cachedEnumeratedBlock = currentBlockCache.getCachedBlock(enumeratedBlock);

                            if (!CachedBlock.isValidPipeBlock(cachedEnumeratedBlock))
                                continue;

                            // Ensure that the block we came from is of the same color as the one we're enumerating.
                            // [1]: Do this first, as to not mark blocks as visited that have not been walked into.
                            //      This could become a problem if that other-colored block is a part of the future path.
                            if (CachedBlock.doTubeColorsMismatch(cachedPipeBlock, cachedEnumeratedBlock))
                                continue;

                            if (!visitedBlocks.add(CompactId.computeWorldlessBlockId(enumeratedBlock)))
                                continue;

                            if (!CachedBlock.isTube(cachedEnumeratedBlock)) {
                                // Pistons are treated with higher priority.
                                if (CachedBlock.isMaterial(cachedEnumeratedBlock, Material.PISTON)) {
                                    if (!behaviorFlags.contains(EnumerationBehavior.IGNORE_CHECK_VALVES)) {
                                        var oppositePistonFacing = CachedBlock.getFacing(cachedEnumeratedBlock).getOppositeFace();

                                        // Do not walk into the extending side of a piston - this makes it behave
                                        // like a check-valve, which has numerous helpful applications.
                                        if (oppositePistonFacing.getModX() == x && oppositePistonFacing.getModY() == y && oppositePistonFacing.getModZ() == z)
                                            continue;
                                    }

                                    searchQueue.addFirst(enumeratedBlock);
                                }

                                continue;
                            }

                            if (!CachedBlock.isPane(cachedEnumeratedBlock)) {
                                searchQueue.add(enumeratedBlock);
                                continue;
                            }

                            Block nextEnumeratedBlock = enumeratedBlock.getRelative(x, y, z);
                            int cachedNextEnumeratedBlock = currentBlockCache.getCachedBlock(nextEnumeratedBlock);

                            if (!CachedBlock.isValidPipeBlock(cachedNextEnumeratedBlock))
                                continue;

                            // Ensure that the pane is allowed to link with the block we're jumping across to
                            // Same reasoning here as with [1]
                            if (CachedBlock.doTubeColorsMismatch(cachedEnumeratedBlock, cachedNextEnumeratedBlock))
                                continue;

                            if (!visitedBlocks.add(CompactId.computeWorldlessBlockId(nextEnumeratedBlock)))
                                continue;

                            searchQueue.add(nextEnumeratedBlock);
                        }
                    }
                }
            }

            return EnumerationResult.COMPLETED;
        } catch (LoadingChunkException e) {
            return EnumerationResult.NEEDS_CHUNK_LOADING;
        }
    }

    @Override
    public int getMaxTubeBlockCount() {
        return maxTubeBlockCount;
    }

    @Override
    public int getMaxPistonBlockCount() {
        return maxPistonBlockCount;
    }

    @Override
    public int getMaxCacheLoadCount() {
        return maxCacheLoadCount;
    }

    private void startPipe(Block inputPistonBlock, @Nullable List<ItemStack> itemsInPipe, boolean wasRequest, List<PipeNotification> notifications) {
        this.currentBlockCache = cacheRegistry.getBlockCache(inputPistonBlock.getWorld());

        PipeSign sign;
        Block containerBlock;
        int cachedContainerBlock;

        try {
            int cachedInputPistonBlock = currentBlockCache.getCachedBlock(inputPistonBlock);

            if (!CachedBlock.isMaterial(cachedInputPistonBlock, Material.STICKY_PISTON))
                return;

            sign = currentBlockCache.getSignOnPiston(inputPistonBlock, cachedInputPistonBlock, notifications);

            containerBlock = inputPistonBlock.getRelative(CachedBlock.getFacing(cachedInputPistonBlock));
            cachedContainerBlock = currentBlockCache.getCachedBlock(containerBlock);
        }
        // If the very beginning of the pipe already (partially) is within an unloaded chunk,
        // there's no need to start the process at all.
        catch (LoadingChunkException ignored) {
            notifications.add(new WarmupNotification(currentPistonBlockCounter, currentTubeBlockCounter));
            return;
        }

        if (itemsInPipe == null)
            itemsInPipe = new ArrayList<>();

        LongSet visitedBlocks = new LongOpenHashSet();
        visitedBlocks.add(CompactId.computeWorldlessBlockId(containerBlock));

        // Suck items from container-block

        InventoryHolder inventoryHolder = null;
        Jukebox jukebox = null;

        if (
            CachedBlock.hasHandledInputInventory(cachedContainerBlock)
                && containerBlock.getState() instanceof InventoryHolder holder
        ) {
            inventoryHolder = holder;
            Inventory blockInventory = inventoryHolder.getInventory();

            if (blockInventory instanceof FurnaceInventory furnaceInventory) {
                ItemStack result = furnaceInventory.getResult();

                if (ItemUtil.isStackValid(result) && ItemUtil.doesItemPassFilters(result, sign.includeFilters, sign.excludeFilters)) {
                    itemsInPipe.add(result);
                    furnaceInventory.setResult(null);
                }
            } else if (inventoryHolder instanceof BrewingStand brewingStand) {
                if (brewingStand.getBrewingTime() <= 0) {
                    BrewerInventory inventory = brewingStand.getInventory();

                    for (int i = 0; i < 3; ++i) {
                        ItemStack item = inventory.getItem(i);

                        if (ItemUtil.isStackValid(item) && ItemUtil.doesItemPassFilters(item, sign.includeFilters, sign.excludeFilters)) {
                            itemsInPipe.add(item);
                            inventory.setItem(i, null);

                            if (pipeStackPerPull)
                                break;
                        }
                    }
                }
            } else {
                for (int slot = 0; slot < blockInventory.getSize(); ++slot) {
                    ItemStack stack = blockInventory.getItem(slot);

                    if (!ItemUtil.isStackValid(stack))
                        continue;

                    if (!ItemUtil.doesItemPassFilters(stack, sign.includeFilters, sign.excludeFilters))
                        continue;

                    itemsInPipe.add(stack);
                    blockInventory.setItem(slot, null);

                    if (pipeStackPerPull)
                        break;
                }
            }
        } else if (CachedBlock.isMaterial(cachedContainerBlock, Material.JUKEBOX)) {
            jukebox = (Jukebox) containerBlock.getState();

            if (jukebox.hasRecord()) {
                itemsInPipe.add(jukebox.getRecord());
                jukebox.setRecord(null);
                jukebox.update();
            }
        }

        PipeSuckEvent suckEvent = new PipeSuckEvent(inputPistonBlock, new ArrayList<>(itemsInPipe), containerBlock, cachedContainerBlock);
        Bukkit.getPluginManager().callEvent(suckEvent);

        itemsInPipe.clear();

        for (ItemStack item : suckEvent.getItems()) {
            if (ItemUtil.isStackValid(item))
                itemsInPipe.add(item);
        }

        if (itemsInPipe.isEmpty())
            return;

        // Walk pipe to store as many items as possible

        EnumerationResult enumerationResult = EnumerationResult.COMPLETED;
        EnumSet<LocateFlag> locateFlags = EnumSet.of(LocateFlag.RESET_COUNTERS);

        if (sign != PipeSign.NO_SIGN)
            locateFlags.add(LocateFlag.ENCOUNTERED_SIGN);

        if (!suckEvent.isCancelled())
            enumerationResult = locateExitNodesForItems(inputPistonBlock, visitedBlocks, locateFlags, itemsInPipe, notifications);

        // Try to put leftovers back into the block and drop the rest at the input-piston.

        List<ItemStack> leftovers = new ArrayList<>();

        // Do not cause leftovers to be dropped when not having encountered a sign yet during the
        // warmup process; signs are only missed if the pipe completed fully.
        boolean missedSign = pipeRequireSign && enumerationResult == EnumerationResult.COMPLETED && !locateFlags.contains(LocateFlag.ENCOUNTERED_SIGN);

        if (!itemsInPipe.isEmpty()) {
            boolean exceededLimits = enumerationResult == EnumerationResult.EXCEEDED_TUBE_COUNT_LIMIT || enumerationResult == EnumerationResult.EXCEEDED_PISTON_COUNT_LIMIT;

            // Drop leftovers for "malformed" pipes, if configured.
            if ((dropNoSign && missedSign) || (dropExceededLimits && exceededLimits)) {
                leftovers.addAll(itemsInPipe);
            } else if (inventoryHolder != null) {
                // Allow to put items that have been sucked from the result-slot back into the furnace.
                leftovers.addAll(InventoryUtil.addItemsToInventory(inventoryHolder, itemsInPipe, EnumSet.of(InventoryAddFlag.ADD_TO_FURNACE_RESULT)));
            } else if (jukebox != null) {
                for (ItemStack item : itemsInPipe) {
                    if (jukebox.hasRecord() || !item.getType().isRecord()) {
                        leftovers.add(item);
                        continue;
                    }

                    jukebox.setRecord(item);
                    jukebox.update();
                }
            } else {
                leftovers.addAll(itemsInPipe);
            }
        }

        // Finish up the pipe and possibly drop leftovers

        PipeFinishEvent finishEvent = new PipeFinishEvent(inputPistonBlock, leftovers, containerBlock, cachedContainerBlock, wasRequest);
        Bukkit.getPluginManager().callEvent(finishEvent);

        leftovers = finishEvent.getItems();
        itemsInPipe.clear();

        if (!leftovers.isEmpty()) {
            for (ItemStack item : leftovers) {
                if (!ItemUtil.isStackValid(item))
                    continue;

                inputPistonBlock.getWorld().dropItemNaturally(inputPistonBlock.getLocation().add(0.5, 0.5, 0.5), item);
            }
        }

        if (missedSign) {
            notifications.add(new NoSignNotification());
            return;
        }

        switch (enumerationResult) {
            case NEEDS_CHUNK_LOADING, EXCEEDED_CACHE_LOAD_LIMIT -> notifications.add(new WarmupNotification(currentPistonBlockCounter, currentTubeBlockCounter));
            case EXCEEDED_PISTON_COUNT_LIMIT -> notifications.add(new PistonLimitNotification(maxPistonBlockCount));
            case EXCEEDED_TUBE_COUNT_LIMIT -> notifications.add(new TubeLimitNotification(maxTubeBlockCount));
        }
    }

    private void startPipeAndHandleNotifications(Block inputPistonBlock, @Nullable List<ItemStack> itemsInPipe, boolean wasRequest) {
        var notifications = new ArrayList<PipeNotification>(1);

        startPipe(inputPistonBlock, itemsInPipe, wasRequest, notifications);

        if (notifications.isEmpty())
            return;

        var hasRegionNotifications = notifications.stream().anyMatch(PipeNotification::broadcastToRegion);
        var coordinates = inputPistonBlock.getX() + " " + inputPistonBlock.getY() + " " + inputPistonBlock.getZ();

        // Avoid locating region-players if no notification targets this range, but otherwise, do use
        // this handler first, as to append region-information even to players within range.
        if (hasRegionNotifications) {
            var world = inputPistonBlock.getWorld();
            var location = inputPistonBlock.getLocation();

            WorldGuardUtil.forEachTargetedRegionPlayer(location, notifyOwnersOfRegion, notifyMembersOfRegion, ignoredRegionsLower, (player, regionDetails) -> {
                // Do not send to players that are outside of this world - this could be rather confusing, seeing
                // how we're not printing world-names with coordinates (unnecessary clutter).
                if (!player.getWorld().equals(world))
                    return;

                var extendedCoordinates = coordinates + " (region " + regionDetails + ")";

                for (var notification : notifications) {
                    if (!notification.broadcastToRegion())
                        continue;

                    notification.sendOnceIfNotDebounced(player, inputPistonBlock, extendedCoordinates, notificationDebouncer);
                }
            });
        }

        if (notificationRadiusSquared > 0) {
            Location inputLocation = inputPistonBlock.getLocation();

            for (Player player : inputPistonBlock.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(inputLocation) > notificationRadiusSquared)
                    continue;

                for (var notification : notifications)
                    notification.sendOnceIfNotDebounced(player, inputPistonBlock, coordinates, notificationDebouncer);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockRedstoneChange(SourcedBlockRedstoneEvent event) {
        if (!EventUtil.passesFilter(event))
            return;

        startPipeAndHandleNotifications(event.getBlock(), null, false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPipeRequest(PipeRequestEvent event) {
        if (!EventUtil.passesFilter(event))
            return;

        startPipeAndHandleNotifications(event.getBlock(), event.getItems(), true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHopperSearch(HopperInventorySearchEvent event) {
        var hopper = event.getBlock();
        var sourceOrDestination = event.getSearchBlock();

        // Destinations are always either below the hopper-block or on any other
        // direct face besides UP, whenever the output does the 90° bend. Blocks above
        // are sources, and we're not trying to suck from a pipe, merely put into it.
        if (sourceOrDestination.getY() > hopper.getY())
            return;

        startPipeAndHandleNotifications(sourceOrDestination, null, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        notificationDebouncer.removePlayer(event.getPlayer());
    }

    private boolean pipesDiagonal;
    private @Nullable Material pipeInsulator;
    private boolean pipeStackPerPull;
    private boolean pipeRequireSign;
    private boolean dropExceededLimits;
    private boolean dropNoSign;
    private int maxTubeBlockCount;
    private int maxPistonBlockCount;
    private int maxCacheLoadCount;
    private int notificationRadiusSquared;
    private boolean notifyOwnersOfRegion;
    private boolean notifyMembersOfRegion;
    private Set<String> ignoredRegionsLower;

    @Override
    public void loadConfiguration(YAMLProcessor config, String path) {

        config.setComment(path + "allow-diagonal", "Allow pipes to work diagonally. Required for insulators to work.");
        pipesDiagonal = config.getBoolean(path + "allow-diagonal", false);

        config.setComment(path + "insulator-block", "When pipes work diagonally, this block allows the pipe to be insulated to not work diagonally.");
        BlockType insulatorType = BlockTypes.get(config.getString(path + "insulator-block", BlockTypes.WHITE_WOOL.id()));
        pipeInsulator = insulatorType == null ? null : BukkitAdapter.adapt(insulatorType);

        config.setComment(path + "stack-per-move", "This option stops the pipes taking the entire chest on power, and makes it just take a single stack.");
        pipeStackPerPull = config.getBoolean(path + "stack-per-move", true);

        config.setComment(path + "require-sign", "Requires pipes to have a [Pipe] sign connected to them. This is the only way to require permissions to make pipes.");
        pipeRequireSign = config.getBoolean(path + "require-sign", false);

        config.setComment(path + "drop-exceeded-limits", "Whether to drop the contents of the pipe if the extent-limits have been exceeded");
        dropExceededLimits = config.getBoolean(path + "drop-exceeded-limits", true);

        config.setComment(path + "drop-no-sign", "Whether to drop the contents of the pipe if a sign was required but none has been encountered");
        dropNoSign = config.getBoolean(path + "drop-no-sign", true);

        config.setComment(path + "max-tube-block-count", "After how many encountered tube-blocks to stop walking the pipe; -1 for no limit.");
        maxTubeBlockCount = config.getInt(path + "max-pipe-block-count", -1);

        config.setComment(path + "max-piston-block-count", "After how many encountered output-pistons to stop walking the pipe; -1 for no limit.");
        maxPistonBlockCount = config.getInt(path + "max-piston-block-count", -1);

        config.setComment(path + "max-cache-load-count", "When initially warming up caches, how many blocks to load in one go at max; -1 for no limit.");
        maxCacheLoadCount = config.getInt(path + "max-cache-load-count", 500);

        config.setComment(path + "initial-chunk-retain-duration", "For how long, in seconds, to retain chunks in memory after having loaded them while traversing pipes; -1 for no retainment at all.");
        cacheRegistry.setInitialChunkTicketDuration(config.getInt(path + "initial-chunk-retain-duration", BlockCacheRegistry.DEFAULT_INITIAL_CHUNK_TICKET_DURATION) * 1000);

        config.setComment(path + "continued-chunk-retain-duration", "For how long, in seconds, to retain chunks in memory that contain regularly accessed blocks; -1 for no continued retainment.");
        cacheRegistry.setContinuedChunkTicketDuration(config.getInt(path + "continued-chunk-retain-duration", BlockCacheRegistry.DEFAULT_CONTINUED_CHUNK_TICKET_DURATION) * 1000);

        config.setComment(path + "notification-radius", "In what radius around an input-block to send notifications to player's action-bars; -1 to hide them");
        notificationRadiusSquared = config.getInt(path + "notification-radius", 5);
        notificationRadiusSquared = notificationRadiusSquared * notificationRadiusSquared;

        config.setComment(path + "notify-owners-of-region", "Whether to display warning-notifications to all owners of the region(s) the input-piston resides in");
        notifyOwnersOfRegion = config.getBoolean(path + "notify-owners-of-region", true);

        config.setComment(path + "notify-members-of-region", "Whether to display warning-notifications to all members of the region(s) the input-piston resides in");
        notifyMembersOfRegion = config.getBoolean(path + "notify-members-of-region", true);

        ignoredRegionsLower = new HashSet<>();

        config.setComment(path + "notify-ignored-regions", "A list of regions to ignore while sending out notifications to members and/or owners");
        for (var ignoredRegion : config.getStringList(path + "notify-ignored-regions", Collections.emptyList()))
            ignoredRegionsLower.add(ignoredRegion.trim().toLowerCase());

        config.setComment(path + "notification-debounce-delay", "Minimum duration, in seconds, between sending out equal notifications; 0 for none");
        notificationDebouncer.setDebounceMillis(config.getInt(path + "notification-debounce-delay", 15) * 1000L);
    }
}