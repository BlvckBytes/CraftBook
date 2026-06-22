// $Id$
/*
 * Copyright (C) 2010, 2011 sk89q <http://www.sk89q.com>
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

package com.sk89q.craftbook.util;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.mechanics.ic.ICMechanic;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import org.bukkit.block.Block;

/**
 * IC utility functions.
 *
 * @author sk89q
 */
public final class ICUtil {

    public static Vector3 parseUnsafeBlockLocation(String line) throws NumberFormatException, ArrayIndexOutOfBoundsException {

        line = line.replace("!", "").replace("^", "").replace("&", "");
        double offsetX = 0, offsetY, offsetZ = 0;

        if (line.contains("="))
            line = RegexUtil.EQUALS_PATTERN.split(line)[1];
        String[] split = RegexUtil.COLON_PATTERN.split(line);
        if (split.length > 1) {
            offsetX = Double.parseDouble(split[0]);
            offsetY = Double.parseDouble(split[1]);
            offsetZ = Double.parseDouble(split[2]);
        } else
            offsetY = Double.parseDouble(line);

        return Vector3.at(offsetX, offsetY, offsetZ);
    }

    public static Block parseBlockLocation(ChangedSign sign, String line) {
        BlockVector3 offsets = BlockVector3.ZERO;

        try {
            offsets = parseUnsafeBlockLocation(line).toBlockPoint();
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {}

        if(offsets.x() == 0 && offsets.y() == 0 && offsets.z() == 0)
            return sign.getBlock();

        return LocationUtil.getRelativeOffset(sign, offsets.x(), offsets.y(), offsets.z());
    }

    public static Vector3 parseRadius(String line) {

        Vector3 radius = Vector3.at(10,10,10);
        try {
            radius = parseUnsafeRadius(line);
        } catch (NumberFormatException ignored) {
        }
        return radius;
    }

    public static Vector3 parseUnsafeRadius(String line) throws NumberFormatException {
        String[] radians = RegexUtil.COMMA_PATTERN.split(RegexUtil.EQUALS_PATTERN.split(line, 2)[0]);
        if(radians.length > 1) {
            double x = verifyRadius(Double.parseDouble(radians[0]), ICMechanic.instance.maxRange);
            double y = verifyRadius(Double.parseDouble(radians[1]), ICMechanic.instance.maxRange);
            double z = verifyRadius(Double.parseDouble(radians[2]), ICMechanic.instance.maxRange);
            return Vector3.at(x,y,z);
        }
        else {
            double r = Double.parseDouble(radians[0]);
            r = verifyRadius(r, ICMechanic.instance.maxRange);
            return Vector3.at(r,r,r);
        }
    }

    private static double verifyRadius(double radius, double maxradius) {
        return Math.max(0, Math.min(maxradius, radius));
    }
}