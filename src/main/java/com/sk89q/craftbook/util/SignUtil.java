// $Id$
/*
 * CraftBook Copyright (C) 2010 sk89q <http://www.sk89q.com>
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.block.SignChangeEvent;

import java.util.function.Consumer;

/**
 * <p>
 * Convenience methods for dealing with some sign block data.
 * </p>
 * <p>
 * If you intend to care about the eight further directions (as opposed to the four cardinal directions and the four
 * ordinal directions), this isn't
 * for you -- since BlockFace has no such directions, those will be rounded to the nearest ordinal direction. (If the
 * term "further direction"
 * confuses you, see https://secure.wikimedia.org/wikipedia/en/wiki/Cardinal_directions).
 * </p>
 * <p>
 * This is direly close to being a replicate of things you can access via org.bukkit.material.Sign (which extends
 * MaterialData). However, that thing:
 * <ul>
 * <li>doesn't provide the relative direction methods.
 * <li>rounds the further divisions to cardinal/ordinal differently (and wrong, in my book).
 * <li>has the same class name for that MaterialData thing as the BlockState, which is annoying as hell import-wise.
 * <li>requires allocating an object and copying two bytes in a fashion that I consider kinda unnecessary.
 * </ul>
 * Ideally, I think I'd like to see if I can get something like these methods pushed to bukkit.
 * </p>
 *
 * @author hash
 */
public final class SignUtil {

    public static boolean isSign(Block block) {
        return Tag.ALL_SIGNS.isTagged(block.getType());
    }

    public static boolean isStandingSign(Block block) {
        return Tag.STANDING_SIGNS.isTagged(block.getType());
    }

    public static boolean isWallSign(Block block) {
        return isWallSign(block.getType());
    }

    public static boolean isWallSign(Material type) {
        return Tag.WALL_SIGNS.isTagged(type);
    }

    /**
     * @param sign treated as sign post if it is such, or else assumed to be a wall sign (i.e.,
     *             if you ask about a stone block, it's considered a wall
     *             sign).
     *
     * @return the side of the sign containing the text (in other words, when a player places a new sign,
     *         while facing north, this will return south).
     */
    public static BlockFace getFront(Block sign) {
        BlockData blockData = sign.getBlockData();
        if (blockData instanceof Sign) {
            return ((Sign) blockData).getRotation();
        } else if (blockData instanceof WallSign) {
            return ((WallSign) blockData).getFacing();
        } else {
            return BlockFace.SELF;
        }
    }

    public static BlockFace getBack(Block sign) {
        return getFront(sign).getOppositeFace();
    }

    public static Block getBackBlock(Block sign) {

        return sign.getRelative(getBack(sign));
    }

    /**
     * Cancels a sign change event, and destroys the sign in the process.
     * 
     * @param event The event that is to be cancelled.
     */
    public static void cancelSign(SignChangeEvent event) {
        event.setCancelled(true);
        event.getBlock().breakNaturally();
    }

    public static String[] getFrontLinesOrEmpty(Block maybeSignBlock) {
        if (maybeSignBlock.getState(false) instanceof org.bukkit.block.Sign sign)
            return getFrontLines(sign);

        return new String[] { "", "", "", "" };
    }

    public static String[] getFrontLines(org.bukkit.block.Sign sign) {
        var lineComponents = sign.getSide(Side.FRONT).lines();
        var lineStrings = new String[lineComponents.size()];

        for (var index = 0; index < lineComponents.size(); ++index) {
            var lineBuilder = new StringBuilder();
            var lineComponent = lineComponents.get(index);

            if (lineComponent != null)
                forEachTextOfComponent(lineComponent, lineBuilder::append);

            lineStrings[index] = lineBuilder.toString();
        }

        return lineStrings;
    }

    private static void forEachTextOfComponent(Component component, Consumer<String> handler) {
        if (component instanceof TextComponent textComponent)
            handler.accept(textComponent.content());

        for (var child : component.children())
            forEachTextOfComponent(child, handler);
    }
}