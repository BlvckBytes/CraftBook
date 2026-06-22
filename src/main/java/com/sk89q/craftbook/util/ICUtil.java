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
import com.sk89q.craftbook.CraftBookPlayer;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.ic.ICMechanic;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.SphereRegionSelector;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * IC utility functions.
 *
 * @author sk89q
 */
public final class ICUtil {

    public static void parseSignFlags(CraftBookPlayer player, ChangedSign sign) {

        for(int i = 2; i < 4; i++) {

            if(sign.getLine(i).contains("[off]")) {

                if(CraftBookPlugin.plugins.getWorldEdit() == null) {
                    sign.setLine(i, sign.getLine(i).replace("[off]", ""));
                    player.printError("worldedit.ic.notfound");
                } else {
                    RegionSelector selector = WorldEdit.getInstance().getSessionManager().get(player).getRegionSelector(player.getWorld());

                    try {
                        if(selector instanceof CuboidRegionSelector) {

                            BlockVector3 centre = selector.getRegion().getMaximumPoint().add(selector.getRegion().getMinimumPoint());

                            centre = centre.divide(2);

                            BlockVector3 offset = centre.subtract(BukkitAdapter.adapt(sign.getBlock().getLocation()).toVector().toBlockPoint());

                            String x,y,z;

                            x = Double.toString(offset.x());
                            if (x.endsWith(".0"))
                                x = x.replace(".0", "");

                            y = Double.toString(offset.y());
                            if (y.endsWith(".0"))
                                y = y.replace(".0", "");

                            z = Double.toString(offset.z());
                            if (z.endsWith(".0"))
                                z = z.replace(".0", "");

                            sign.setLine(i, sign.getLine(i).replace("[off]", "&" + x + ":" + y + ":" + z));
                        } else if (selector instanceof SphereRegionSelector) {
                            Vector3 centre = selector.getRegion().getCenter();
                            Vector3 offset = centre.subtract(BukkitAdapter.adapt(sign.getBlock().getLocation()).toVector());

                            String x,y,z;

                            x = Double.toString(offset.x());
                            if (x.endsWith(".0"))
                                x = x.replace(".0", "");

                            y = Double.toString(offset.y());
                            if (y.endsWith(".0"))
                                y = y.replace(".0", "");

                            z = Double.toString(offset.z());
                            if (z.endsWith(".0"))
                                z = z.replace(".0", "");

                            sign.setLine(i, sign.getLine(i).replace("[off]", "&" + x + ":" + y + ":" + z));
                        } else { // Unsupported.
                            sign.setLine(i, sign.getLine(i).replace("[off]", ""));
                            player.printError("worldedit.ic.unsupported");
                        }
                    }
                    catch(IncompleteRegionException e) {
                        player.printError("worldedit.ic.noselection");
                    }
                }
            }

            if(sign.getLine(i).contains("[rad]")) {

                if(CraftBookPlugin.plugins.getWorldEdit() == null) {
                    sign.setLine(i, sign.getLine(i).replace("[rad]", ""));
                    player.printError("worldedit.ic.notfound");
                } else {
                    RegionSelector selector = WorldEdit.getInstance().getSessionManager().get(player).getRegionSelector(player.getWorld());

                    try {
                        if(selector instanceof CuboidRegionSelector) {

                            String x,y,z;

                            x = Double.toString(Math.abs(selector.getRegion().getMaximumPoint().x() - selector.getRegion().getMinimumPoint().x())/2);
                            if (x.endsWith(".0"))
                                x = x.replace(".0", "");

                            y = Double.toString(Math.abs(selector.getRegion().getMaximumPoint().y() - selector.getRegion().getMinimumPoint().y())/2);
                            if (y.endsWith(".0"))
                                y = y.replace(".0", "");

                            z = Double.toString(Math.abs(selector.getRegion().getMaximumPoint().z() - selector.getRegion().getMinimumPoint().z())/2);
                            if (z.endsWith(".0"))
                                z = z.replace(".0", "");

                            sign.setLine(i, sign.getLine(i).replace("[rad]", x + "," + y + "," + z));
                        } else if (selector instanceof SphereRegionSelector) {

                            String x;

                            double amounts = ((EllipsoidRegion) selector.getRegion()).getRadius().x();

                            x = Double.toString(amounts);
                            if (x.endsWith(".0"))
                                x = x.replace(".0", "");

                            sign.setLine(i, sign.getLine(i).replace("[rad]", x));
                        } else { // Unsupported.
                            sign.setLine(i, sign.getLine(i).replace("[rad]", ""));
                            player.printError("worldedit.ic.unsupported");
                        }
                    }
                    catch(IncompleteRegionException e) {
                        player.printError("worldedit.ic.noselection");
                    }
                }
            }
        }

        sign.update(false);
    }

    public static Vector3 parseUnsafeBlockLocation(String line) throws NumberFormatException, ArrayIndexOutOfBoundsException {

        line = line.replace("!", "").replace("^", "").replace("&", "");
        double offsetX = 0, offsetY = 0, offsetZ = 0;

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

    public static Block parseBlockLocation(ChangedSign sign, String line, LocationCheckType relative) {

        Block target = SignUtil.getBackBlock(CraftBookBukkitUtil.toSign(sign).getBlock());

        if (line.contains("!"))
            relative = LocationCheckType.getTypeFromChar('!');
        else if (line.contains("^"))
            relative = LocationCheckType.getTypeFromChar('^');
        else if (line.contains("&"))
            relative = LocationCheckType.getTypeFromChar('&');

        BlockVector3 offsets = BlockVector3.ZERO;

        try {
            offsets = parseUnsafeBlockLocation(line).toBlockPoint();
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException ignored) {
        }

        if(offsets.x() == 0 && offsets.y() == 0 && offsets.z() == 0)
            return target;

        if (relative == LocationCheckType.RELATIVE)
            target = LocationUtil.getRelativeOffset(sign, offsets.x(), offsets.y(), offsets.z());
        else if (relative == LocationCheckType.OFFSET)
            target = LocationUtil.getOffset(target, offsets.x(), offsets.y(), offsets.z());
        else if (relative == LocationCheckType.ABSOLUTE)
            target = new Location(target.getWorld(), offsets.x(), offsets.y(), offsets.z()).getBlock();
        return target;
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

    public enum LocationCheckType {

        RELATIVE('^'),
        OFFSET('&'),
        ABSOLUTE('!');

        char c;

        LocationCheckType(char c) {

            this.c = c;
        }

        public static LocationCheckType getTypeFromChar(char c) {

            for(LocationCheckType t : values())
                if(t.c == c)
                    return t;

            return RELATIVE;
        }

        public static LocationCheckType getTypeFromName(String name) {

            if(name.length() == 1)
                return getTypeFromChar(name.charAt(0));
            for(LocationCheckType t : values())
                if(t.name().equalsIgnoreCase(name))
                    return t;

            return RELATIVE;
        }
    }
}