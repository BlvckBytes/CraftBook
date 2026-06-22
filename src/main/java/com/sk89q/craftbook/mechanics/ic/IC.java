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

package com.sk89q.craftbook.mechanics.ic;

import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.util.SignUtil;

/**
 * A base abstract IC that all ICs can inherit from.
 *
 * @author sk89q
 */
public abstract class IC {

    private final ChangedSign sign;

    public IC(ChangedSign sign) {
        this.sign = sign;
    }

    public abstract String getTitle();

    public abstract String getSignTitle();

    public abstract void unload();

    public abstract void load();

    public ChangedSign getSign() {
        return sign;
    }

    public Location getLocation() {
        return getSign().getBlock().getLocation();
    }

    public Block getBackBlock() {
        return SignUtil.getBackBlock(CraftBookBukkitUtil.toSign(sign).getBlock());
    }

    public String getLine(int line) {
        return sign.getLine(line);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IC && getSignTitle().equalsIgnoreCase(((IC) o).getSignTitle()) && getTitle().equalsIgnoreCase(((IC) o).getTitle()) && sign.equals(((IC) o).sign);
    }
}