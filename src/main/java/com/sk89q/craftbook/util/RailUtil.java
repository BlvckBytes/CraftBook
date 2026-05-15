package com.sk89q.craftbook.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;

import java.util.ArrayList;
import java.util.List;

public final class RailUtil {

    public static List<Chest> getNearbyChests(Block body) {

        int x = body.getX();
        int y = body.getY();
        int z = body.getZ();
        List<Chest> containers = new ArrayList<>();
        if (body.getWorld().getBlockAt(x, y, z).getType() == Material.CHEST || body.getWorld().getBlockAt(x, y, z).getType() == Material.TRAPPED_CHEST) {
            containers.add((Chest) body.getWorld().getBlockAt(x, y, z).getState());
        }
        if (body.getWorld().getBlockAt(x - 1, y, z).getType() == Material.CHEST || body.getWorld().getBlockAt(x - 1, y, z).getType() == Material.TRAPPED_CHEST) {
            containers.add((Chest) body.getWorld().getBlockAt(x - 1, y, z).getState());
            if (body.getWorld().getBlockAt(x - 2, y, z).getType() == Material.CHEST || body.getWorld().getBlockAt(x - 2, y, z).getType() == Material.TRAPPED_CHEST) {
                containers.add((Chest) body.getWorld().getBlockAt(x - 2, y, z).getState());
            }
        }
        if (body.getWorld().getBlockAt(x + 1, y, z).getType() == Material.CHEST || body.getWorld().getBlockAt(x + 1, y, z).getType() == Material.TRAPPED_CHEST) {
            containers.add((Chest) body.getWorld().getBlockAt(x + 1, y, z).getState());
            if (body.getWorld().getBlockAt(x + 2, y, z).getType() == Material.CHEST || body.getWorld().getBlockAt(x + 2, y, z).getType() == Material.TRAPPED_CHEST) {
                containers.add((Chest) body.getWorld().getBlockAt(x + 2, y, z).getState());
            }
        }
        if (body.getWorld().getBlockAt(x, y, z - 1).getType() == Material.CHEST || body.getWorld().getBlockAt(x, y, z - 1).getType() == Material.TRAPPED_CHEST) {
            containers.add((Chest) body.getWorld().getBlockAt(x, y, z - 1).getState());
            if (body.getWorld().getBlockAt(x, y, z - 2).getType() == Material.CHEST || body.getWorld().getBlockAt(x, y, z - 2).getType() == Material.TRAPPED_CHEST) {
                containers.add((Chest) body.getWorld().getBlockAt(x, y, z - 2).getState());
            }
        }
        if (body.getWorld().getBlockAt(x, y, z + 1).getType() == Material.CHEST  || body.getWorld().getBlockAt(x, y, z + 1).getType() == Material.TRAPPED_CHEST) {
            containers.add((Chest) body.getWorld().getBlockAt(x, y, z + 1).getState());
            if (body.getWorld().getBlockAt(x, y, z + 2).getType() == Material.CHEST || body.getWorld().getBlockAt(x, y, z + 2).getType() == Material.TRAPPED_CHEST) {
                containers.add((Chest) body.getWorld().getBlockAt(x, y, z + 2).getState());
            }
        }

        return containers;
    }

    public static boolean isTrack(Material id) {
        return Tag.RAILS.isTagged(id);
    }
}