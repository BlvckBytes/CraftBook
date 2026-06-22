package com.sk89q.craftbook.mechanics.ic.gates.world.blocks;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.ic.*;
import com.sk89q.craftbook.util.ItemSyntax;
import com.sk89q.craftbook.util.ItemUtil;
import com.sk89q.craftbook.util.SearchArea;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.Cocoa;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Planter extends IC implements SelfTriggeredIC {

    private static final int MAX_TRIAL_COUNT = 25;

    private Block cachedContainerBlock;
    private Inventory cachedChestInventory;

    public Planter(ChangedSign block) {
        super(block);
    }

    ItemStack item;

    SearchArea area;

    @Override
    public void load() {
        if(getLine(2).isEmpty())
            item = null;
        else
            item = ItemSyntax.getItem(getLine(2));

        area = SearchArea.createArea(getLocation().getBlock(), getLine(3));
    }

    @Override
    public String getTitle() {

        return "Planter";
    }

    @Override
    public String getSignTitle() {

        return "PLANTER";
    }

    @Override
    public void think() {
        for (int i = 0; i < 4; i++)
            plant();
    }

    private void plant() {
        if (item != null && !plantableItem(item)) return;

        if (cachedContainerBlock == null)
            cachedContainerBlock = getBackBlock().getRelative(0, 1, 0);

        var containerType = cachedContainerBlock.getType();

        // Has a chest attached on top of the block where the sign is mounted - take items from its storage
        if (containerType == Material.CHEST || containerType == Material.TRAPPED_CHEST) {
            // The inventory is live for as long as the chunk is loaded, and once it unloads, this IC is unloaded
            // just as well, invalidating the cache again; no need to access the inventory repeatedly.
            if (cachedChestInventory == null)
                cachedChestInventory = ((Chest) cachedContainerBlock.getState()).getInventory();

            var chestSlot = 0;
            var chestSize = cachedChestInventory.getSize();

            for (int trialIndex = 0; trialIndex < MAX_TRIAL_COUNT; ++trialIndex) {
                if (chestSlot >= chestSize)
                    break;

                var chestItem = cachedChestInventory.getItem(chestSlot);

                // Current slot is unusable for planting - skip over
                if (
                  !ItemUtil.isStackValid(chestItem)
                    || !plantableItem(chestItem)
                    || (item != null && !item.isSimilar(chestItem))
                ) {
                    ++chestSlot;
                    continue;
                }

                Block targetBlock = searchBlocks(chestItem);

                if (targetBlock == null)
                    continue;

                boolean plantSuccess = plantBlockAt(chestItem, targetBlock);

                if (!plantSuccess)
                    continue;

                if (chestItem.getAmount() == 1) {
                    cachedChestInventory.setItem(chestSlot, null);
                    return;
                }

                chestItem.setAmount(chestItem.getAmount() - 1);
                return;
            }
        }

        // Has no container attached - take items from nearby item-entities
        else {
            cachedChestInventory = null;

            List<Entity> areaEntities = area.getEntitiesInArea();

            int entityIndex = 0;

            for (int trialIndex = 0; trialIndex < MAX_TRIAL_COUNT; ++trialIndex) {
                if (entityIndex >= areaEntities.size())
                    break;

                Entity entity = areaEntities.get(entityIndex);

                ItemStack entityStack;

                // Current entity is unusable for planting - skip over
                if (
                  !(entity instanceof Item itemEntity)
                    || entity.isDead()
                    || !entity.isValid()
                    || !ItemUtil.isStackValid(entityStack = itemEntity.getItemStack())
                    || (item != null && !item.isSimilar(entityStack))
                ) {
                    ++entityIndex;
                    continue;
                }

                Block targetBlock = searchBlocks(entityStack);

                if (targetBlock == null)
                    continue;

                // First try to plant, then take the item from the entity - this was a long-standing
                // upstream bug, which made items vanish without being planted on the surrounding field.

                boolean plantSuccess = plantBlockAt(entityStack, targetBlock);

                if (!plantSuccess)
                    continue;

                if (entityStack.getAmount() == 1) {
                    itemEntity.remove();
                    return;
                }

                entityStack.setAmount(entityStack.getAmount() - 1);
                itemEntity.setItemStack(entityStack);
                return;
            }
        }
    }

    @Override
    public void unload() {
        cachedContainerBlock = null;
        cachedChestInventory = null;
    }

    private Block searchBlocks(ItemStack stack) {
        var b = area.getRandomBlockInArea();

        if (b == null || b.getType() != Material.AIR)
            return null;

        if (itemPlantableAtBlock(stack, b))
            return b;

        return null;
    }

    private boolean plantableItem(ItemStack item) {
      return switch (item.getType()) {
        case WHEAT_SEEDS, NETHER_WART, MELON_SEEDS, PUMPKIN_SEEDS, CACTUS, POTATO, CARROT, POPPY, DANDELION,
             RED_MUSHROOM, BROWN_MUSHROOM, LILY_PAD, BEETROOT_SEEDS, COCOA_BEANS, CRIMSON_FUNGUS, WARPED_FUNGUS,
             PITCHER_POD, TORCHFLOWER_SEEDS -> true;
        default -> Tag.SAPLINGS.isTagged(item.getType());
      };
    }

    private boolean itemPlantableAtBlock(ItemStack item, Block block) {
        var belowType = block.getRelative(0, -1, 0).getType();

        switch (item.getType()) {
            case WHEAT_SEEDS:
            case MELON_SEEDS:
            case PUMPKIN_SEEDS:
            case POTATO:
            case CARROT:
            case BEETROOT_SEEDS:
            case PITCHER_POD:
            case TORCHFLOWER_SEEDS:
                return belowType == Material.FARMLAND;
            case NETHER_WART:
                return belowType == Material.SOUL_SAND;
            case CACTUS:
                return belowType == Material.SAND;
            case RED_MUSHROOM:
            case BROWN_MUSHROOM:
                return belowType.isSolid();
            case LILY_PAD:
                return belowType == Material.WATER;
            case COCOA_BEANS:
                BlockFace[] faces = new BlockFace[]{BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH};
                for(BlockFace face : faces) {
                    if(block.getRelative(face).getType() == Material.JUNGLE_LOG)
                        return true;
                }
                return false;
            case CRIMSON_FUNGUS:
            case WARPED_FUNGUS:
                return belowType == Material.CRIMSON_NYLIUM || belowType == Material.WARPED_NYLIUM || Tag.DIRT.isTagged(belowType);
            default:
                if (item.getType() == Material.WITHER_ROSE) {
                    if (belowType == Material.SOUL_SOIL || belowType == Material.SOUL_SAND)
                        return true;
                }

                if (Tag.SAPLINGS.isTagged(item.getType()) || Tag.SMALL_FLOWERS.isTagged(item.getType())) {
                    return switch (belowType) {
                        case MOSS_BLOCK, MUD, MYCELIUM, ROOTED_DIRT, PALE_MOSS_BLOCK,
                             MUDDY_MANGROVE_ROOTS, PODZOL, GRASS_BLOCK, COARSE_DIRT, DIRT -> true;
                        default -> false;
                    };
                }

                return false;
        }
    }

    private boolean plantBlockAt(ItemStack item, Block block) {
        switch (item.getType()) {
            case POPPY:
            case DANDELION:
            case CACTUS:
            case RED_MUSHROOM:
            case BROWN_MUSHROOM:
            case LILY_PAD:
                block.setType(item.getType());
                return true;
            case WHEAT_SEEDS:
                block.setType(Material.WHEAT);
                return true;
            case MELON_SEEDS:
                block.setType(Material.MELON_STEM);
                return true;
            case PUMPKIN_SEEDS:
                block.setType(Material.PUMPKIN_STEM);
                return true;
            case NETHER_WART:
                block.setType(Material.NETHER_WART);
                return true;
            case POTATO:
                block.setType(Material.POTATOES);
                return true;
            case CARROT:
                block.setType(Material.CARROTS);
                return true;
            case BEETROOT_SEEDS:
                block.setType(Material.BEETROOTS);
                return true;
            case COCOA_BEANS:
                List<BlockFace> faces =
                        new ArrayList<>(Arrays.asList(BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH));
                Collections.shuffle(faces, ThreadLocalRandom.current());
                for(BlockFace face : faces) {
                    if(block.getRelative(face).getType() == Material.JUNGLE_LOG) {
                        block.setType(Material.COCOA);
                        ((Cocoa) block.getBlockData()).setFacing(face);
                        return true;
                    }
                }
                return false;
            case CRIMSON_FUNGUS:
                block.setType(Material.CRIMSON_FUNGUS);
                return true;
            case WARPED_FUNGUS:
                block.setType(Material.WARPED_FUNGUS);
                return true;
            case TORCHFLOWER_SEEDS:
                block.setType(Material.TORCHFLOWER_CROP);
                return true;
            case PITCHER_POD:
                block.setType(Material.PITCHER_CROP);
                return true;
            default:
                if (Tag.SAPLINGS.isTagged(item.getType())) {
                    block.setType(item.getType());
                    return true;
                }
                return false;
        }
    }

    public static class Factory extends ICFactory {
        @Override
        public String getId() {
            return "MC1234";
        }

        @Override
        public IC create(ChangedSign sign) {
            return new Planter(sign);
        }

        @Override
        public void verify(ChangedSign sign) throws ICVerificationException {
            if(!SearchArea.isValidArea(CraftBookBukkitUtil.toSign(sign).getBlock(), sign.getLine(3)))
                throw new ICVerificationException("Invalid SearchArea on 4th line!");
        }
    }
}