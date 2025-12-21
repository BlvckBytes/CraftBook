package com.sk89q.craftbook.mechanics.ic.gates.world.blocks;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.ic.AbstractICFactory;
import com.sk89q.craftbook.mechanics.ic.AbstractSelfTriggeredIC;
import com.sk89q.craftbook.mechanics.ic.ChipState;
import com.sk89q.craftbook.mechanics.ic.IC;
import com.sk89q.craftbook.mechanics.ic.ICFactory;
import com.sk89q.craftbook.mechanics.ic.ICVerificationException;
import com.sk89q.craftbook.util.ItemSyntax;
import com.sk89q.craftbook.util.ItemUtil;
import com.sk89q.craftbook.util.SearchArea;
import org.bukkit.Material;
import org.bukkit.Server;
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

/**
 * Sapling planter Hybrid variant of MCX206 and MCX203 chest collector When there is a sapling or seed item drop in
 * range it will auto plant it above
 * the IC.
 *
 * @authors Drathus, Me4502
 */
public class Planter extends AbstractSelfTriggeredIC {

    private Block cachedContainerBlock;
    private Inventory cachedChestInventory;

    public Planter(Server server, ChangedSign block, ICFactory factory) {

        super(server, block, factory);
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
    public void trigger(ChipState chip) {

        if (chip.getInput(0)) chip.setOutput(0, plant());
    }

    @Override
    public void think(ChipState state) {

        if(state.getInput(0)) return;

        for(int i = 0; i < 10; i++)
            plant();
    }

    public boolean plant() {

        if (item != null && !plantableItem(item)) return false;

        if (cachedContainerBlock == null)
            cachedContainerBlock = getBackBlock().getRelative(0, 1, 0);

        var containerType = cachedContainerBlock.getType();

        // Has a chest attached on top of the block where the sign is mounted - take items from its storage
        if (containerType == Material.CHEST || containerType == Material.TRAPPED_CHEST) {
            // The inventory is live for as long as the chunk is loaded, and once it unloads, this IC is unloaded
            // just as well, invalidating the cache again; no need to access the inventory repeatedly.
            if (cachedChestInventory == null)
                cachedChestInventory = ((Chest) cachedContainerBlock.getState()).getInventory();

            // By incrementing chest-slots separately and trying up to as many times as there are slots, we stay
            // within sane limits but drastically increase the speed of planting, seeing how the chance of planting
            // is no longer the result of finding a receptive block multiplied by finding a plantable item in the chest,
            // but rather that we lock into the first matching item and try with that until it has been used up completely.
            int chestSlot = 0;

            for (int trialIndex = 0; trialIndex < cachedChestInventory.getSize(); ++trialIndex) {
                var chestItem = cachedChestInventory.getItem(chestSlot);

                // Current slot is unusable for planting - skip over
                if (
                  chestItem == null
                    || !ItemUtil.isStackValid(chestItem)
                    || !plantableItem(chestItem)
                    || (item != null && !ItemUtil.areItemsIdentical(chestItem, item))
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
                    return true;
                }

                chestItem.setAmount(chestItem.getAmount() - 1);
                return true;
            }
        }

        // Has no container attached - take items from nearby item-entities
        else {
            cachedChestInventory = null;

            List<Entity> areaEntities = area.getEntitiesInArea();

            // Same reasoning holds true here as does for the above.
            int entityIndex = 0;

            for (int trialIndex = 0; trialIndex < areaEntities.size(); ++trialIndex) {
                Entity entity = areaEntities.get(entityIndex);

                ItemStack entityStack;

                // Current entity is unusable for planting - skip over
                if (
                  !(entity instanceof Item itemEntity)
                    || !ItemUtil.isStackValid(entityStack = itemEntity.getItemStack())
                    || (item != null && !ItemUtil.areItemsIdentical(item, entityStack))
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

                ItemUtil.takeFromItemEntity(itemEntity, 1);
                return true;
            }
        }

        return false;
    }

    @Override
    public void unload() {
        super.unload();

        cachedContainerBlock = null;
        cachedChestInventory = null;
    }

    public Block searchBlocks(ItemStack stack) {

        Block b = area.getRandomBlockInArea();

        if (b == null || b.getType() != Material.AIR)
            return null;

        if (itemPlantableAtBlock(stack, b))
            return b;

        return null;
    }

    protected boolean plantableItem(ItemStack item) {
        switch (item.getType()) {
            case WHEAT_SEEDS:
            case NETHER_WART:
            case MELON_SEEDS:
            case PUMPKIN_SEEDS:
            case CACTUS:
            case POTATO:
            case CARROT:
            case POPPY:
            case DANDELION:
            case RED_MUSHROOM:
            case BROWN_MUSHROOM:
            case LILY_PAD:
            case BEETROOT_SEEDS:
            case COCOA_BEANS:
            case CRIMSON_FUNGUS:
            case WARPED_FUNGUS:
                return true;
            default:
                return Tag.SAPLINGS.isTagged(item.getType());
        }
    }

    protected boolean itemPlantableAtBlock(ItemStack item, Block block) {
        Material belowType = block.getRelative(0, -1, 0).getType();

        switch (item.getType()) {
            case WHEAT_SEEDS:
            case MELON_SEEDS:
            case PUMPKIN_SEEDS:
            case POTATO:
            case CARROT:
            case BEETROOT_SEEDS:
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
                if (Tag.SAPLINGS.isTagged(item.getType()) || Tag.SMALL_FLOWERS.isTagged(item.getType())) {
                    return Tag.DIRT.isTagged(belowType);
                }
                return false;
        }
    }

    protected boolean plantBlockAt(ItemStack item, Block block) {

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
                Collections.shuffle(faces, CraftBookPlugin.inst().getRandom());
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
            default:
                if (Tag.SAPLINGS.isTagged(item.getType())) {
                    block.setType(item.getType());
                    return true;
                }
                return false;
        }
    }

    public static class Factory extends AbstractICFactory {

        public Factory(Server server) {

            super(server);
        }

        @Override
        public IC create(ChangedSign sign) {

            return new Planter(getServer(), sign, this);
        }

        @Override
        public String getShortDescription() {

            return "Plants plantable things at set offset.";
        }

        @Override
        public String[] getLineHelp() {

            return new String[] {"+oItem to plant id{:data}", "SearchArea"};
        }

        @Override
        public void verify(ChangedSign sign) throws ICVerificationException {
            if(!SearchArea.isValidArea(CraftBookBukkitUtil.toSign(sign).getBlock(), sign.getLine(3)))
                throw new ICVerificationException("Invalid SearchArea on 4th line!");
        }
    }
}