package com.sk89q.craftbook.circuits.gates.world.entity;

import java.util.Set;

import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemStack;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.bukkit.util.BukkitUtil;
import com.sk89q.craftbook.circuits.ic.AbstractIC;
import com.sk89q.craftbook.circuits.ic.AbstractICFactory;
import com.sk89q.craftbook.circuits.ic.ChipState;
import com.sk89q.craftbook.circuits.ic.IC;
import com.sk89q.craftbook.circuits.ic.ICFactory;
import com.sk89q.craftbook.circuits.ic.ICUtil;
import com.sk89q.craftbook.util.LocationUtil;
import com.sk89q.craftbook.util.RegexUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.craftbook.util.VerifyUtil;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.blocks.ItemID;

public class AnimalHarvester extends AbstractIC {

    public AnimalHarvester (Server server, ChangedSign sign, ICFactory factory) {
        super(server, sign, factory);
    }

    private Block center;
    private Set<Chunk> chunks;
    private int radius;
    private Block chest;

    @Override
    public void load() {

        // if the line contains a = the offset is given
        // the given string should look something like that:
        // radius=x:y:z or radius, e.g. 1=-2:5:11
        radius = ICUtil.parseRadius(getSign());
        if (getSign().getLine(2).contains("=")) {
            getSign().setLine(2, radius + "=" + RegexUtil.EQUALS_PATTERN.split(getSign().getLine(2))[1]);
            center = ICUtil.parseBlockLocation(getSign());
        } else {
            getSign().setLine(2, String.valueOf(radius));
            center = SignUtil.getBackBlock(BukkitUtil.toSign(getSign()).getBlock());
        }

        chest = SignUtil.getBackBlock(BukkitUtil.toSign(getSign()).getBlock()).getRelative(BlockFace.UP);

        radius = VerifyUtil.verifyRadius(radius, 15);

        getSign().update(false);
    }

    @Override
    public String getTitle () {
        return "Animal Harvester";
    }

    @Override
    public String getSignTitle () {
        return "ANIMAL HARVEST";
    }

    @Override
    public void trigger (ChipState chip) {

        if(chip.getInput(0))
            chip.setOutput(0, harvest());
    }

    public boolean harvest() {

        chunks = LocationUtil.getSurroundingChunks(SignUtil.getBackBlock(BukkitUtil.toSign(getSign()).getBlock()), radius); // Update chunks
        for (Chunk chunk : chunks) {
            if (chunk.isLoaded()) {
                for (Entity entity : chunk.getEntities()) {
                    if (entity.isValid() && (entity instanceof Cow || entity instanceof Sheep)) {
                        if(!((Animals) entity).isAdult())
                            continue;
                        // Check Radius
                        if (LocationUtil.isWithinRadius(center.getLocation(), entity.getLocation(), radius)) {
                            return harvestAnimal(entity);
                        }
                    }
                }
            }
        }

        return false;
    }

    public boolean harvestAnimal(Entity entity) {

        if (entity instanceof Cow) {

            if(doesChestContain(ItemID.BUCKET)) {

                removeFromChest(ItemID.BUCKET);
                if(!addToChest(new ItemStack(ItemID.MILK_BUCKET, 1))) {
                    addToChest(new ItemStack(ItemID.BUCKET, 1));
                    return false;
                }

                return true;
            }
        }

        if (entity instanceof Sheep) {

            if(doesChestContain(ItemID.SHEARS)) {

                Sheep sh = (Sheep) entity;
                if(sh.isSheared())
                    return false;
                sh.setSheared(true);
                return addToChest(new ItemStack(BlockID.CLOTH, 3, sh.getColor().getWoolData()));
            }
        }

        return false;
    }

    public boolean doesChestContain(int item) {

        if (chest.getTypeId() == BlockID.CHEST) {

            Chest c = (Chest) chest.getState();
            return c.getInventory().contains(item);
        }

        return false;
    }

    public boolean addToChest(int item) {

        if (chest.getTypeId() == BlockID.CHEST) {

            Chest c = (Chest) chest.getState();
            return c.getInventory().addItem(new ItemStack(item, 1)).isEmpty();
        }

        return false;
    }

    public boolean addToChest(ItemStack item) {

        if (chest.getTypeId() == BlockID.CHEST) {

            Chest c = (Chest) chest.getState();
            return c.getInventory().addItem(item).isEmpty();
        }

        return false;
    }

    public boolean removeFromChest(int item) {

        if (chest.getTypeId() == BlockID.CHEST) {

            Chest c = (Chest) chest.getState();
            c.getInventory().removeItem(new ItemStack(item, 1));
        }

        return false;
    }

    public static class Factory extends AbstractICFactory {

        public Factory(Server server) {

            super(server);
        }

        @Override
        public IC create(ChangedSign sign) {

            return new AnimalHarvester(getServer(), sign, this);
        }

        @Override
        public String getShortDescription() {

            return "Harvests nearby cows and sheep.";
        }

        @Override
        public String[] getLineHelp() {

            String[] lines = new String[] {"radius=x:y:z offset", null};
            return lines;
        }
    }
}
