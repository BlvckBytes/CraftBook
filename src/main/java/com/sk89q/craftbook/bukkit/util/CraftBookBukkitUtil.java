package com.sk89q.craftbook.bukkit.util;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.util.Location;
import org.bukkit.World;

// $Id$
/*
 * WorldEdit Copyright (C) 2010 sk89q <http://www.sk89q.com> and contributors
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not,
 * see <http://www.gnu.org/licenses/>.
 */

public final class CraftBookBukkitUtil {

    public static void printStacktrace(Throwable e) {
        CraftBookPlugin.inst().getLogger().severe(CraftBookPlugin.getStackTrace(e));
    }

    public static org.bukkit.Location toLocation(Location teleportLocation) {
        return new org.bukkit.Location(
                toWorld((com.sk89q.worldedit.world.World) teleportLocation.getExtent()),
                teleportLocation.getX(),
                teleportLocation.getY(),
                teleportLocation.getZ(),
                teleportLocation.getYaw(),
                teleportLocation.getPitch()
        );
    }

    public static World toWorld(final com.sk89q.worldedit.world.World world) {
        return ((BukkitWorld) world).getWorld();
    }
}
