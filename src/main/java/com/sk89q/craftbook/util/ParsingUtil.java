package com.sk89q.craftbook.util;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import org.bukkit.entity.Player;

public final class ParsingUtil {

    /**
     * Parses a line with all tags possible with given arguments.
     * 
     * @param line The base line to start with.
     * @param player The player associated with the line (Can be null)
     * 
     * @return
     */
    public static String parseLine(String line, Player player) {

        if(player != null) {
            line = parsePlayerTags(line, player);
        }

        return line;
    }

    public static String parsePlayerTags(String line, Player player) {

        line = line.replace("@p.l", player.getLocation().getX() + ":" + player.getLocation().getY() + ":" + player.getLocation().getZ());
        line = line.replace("@p.x", String.valueOf(player.getLocation().getX()));
        line = line.replace("@p.y", String.valueOf(player.getLocation().getY()));
        line = line.replace("@p.z", String.valueOf(player.getLocation().getZ()));
        line = line.replace("@p.bx", String.valueOf(player.getLocation().getBlockX()));
        line = line.replace("@p.by", String.valueOf(player.getLocation().getBlockY()));
        line = line.replace("@p.bz", String.valueOf(player.getLocation().getBlockZ()));
        line = line.replace("@p.w", player.getLocation().getWorld().getName());
        line = line.replace("@p.u", player.getUniqueId().toString());
        line = line.replace("@p.i", CraftBookPlugin.inst().getUUIDMappings().getCBID(player.getUniqueId()));
        line = line.replace("@p", player.getName());

        return line;
    }
}