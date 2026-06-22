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

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.mechanics.ic.gates.world.blocks.Planter;
import com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter.AutomaticCrafter;
import com.sk89q.craftbook.util.RegexUtil;
import org.bukkit.Location;
import org.bukkit.Server;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;

/**
 * Manages known registered ICs. For an IC to be detected in-world through CraftBook,
 * the IC's factory has to be registered with this manager.
 *
 * @author sk89q
 */
public class ICManager {

    private static ICManager INSTANCE;

    public ICManager() {
        INSTANCE = this;
    }

    public static ICManager inst() {
        return INSTANCE;
    }

    public void enable() {
        CraftBookPlugin.inst().createDefaultConfiguration(new File(CraftBookPlugin.inst().getDataFolder(), "ic-config.yml"), "ic-config.yml");

        registerICs(CraftBookPlugin.inst().getServer());
    }

    public void disable() {
        emptyCache();
        INSTANCE = null;
    }

    /**
     * Holds a map of registered IC factories with their ID.
     *
     * @see RegisteredICFactory
     */
    public final Map<String, RegisteredICFactory> registered = new LinkedHashMap<>();

    /**
     * Holds a map of long IDs to short IDs
     *
     * @see RegisteredICFactory
     */
    public final Map<String, String> longRegistered = new HashMap<>();

    private static final Map<Location, IC> cachedICs = new HashMap<>();

    public void registerIC(String name, String longName, ICFactory factory) {

        for(String ic : ICMechanic.instance.disabledICs)
            if(ic.equalsIgnoreCase(name))
                return;

        register(name, longName, factory);
    }

    public void register(String id, String longId, ICFactory factory) {

        // this is needed so we dont have two patterns
        String id2 = "[" + id + "]";
        // lets check if the IC ID has already been registered
        if (registered.containsKey(id.toLowerCase(Locale.ENGLISH))) return;
        // check if the ic matches the requirements
        Matcher matcher = RegexUtil.IC_PATTERN.matcher(id2);
        if (!matcher.matches()) return;

        RegisteredICFactory registration = new RegisteredICFactory(id, longId, factory);
        // Lowercase the ID so that we can do case in-sensitive lookups
        registered.put(id.toLowerCase(Locale.ENGLISH), registration);

        if (longId != null) {
            String toRegister = longId.toLowerCase(Locale.ENGLISH);
            if (toRegister.length() > 15) {
                toRegister = toRegister.substring(0, 15);
            }
            longRegistered.put(toRegister, id);
        }
    }

    /**
     * Get an IC registration by a provided ID.
     *
     * @param id case insensitive ID
     *
     * @return registration
     *
     * @see RegisteredICFactory
     */
    public RegisteredICFactory get(String id) {

        return registered.get(id.toLowerCase(Locale.ENGLISH));
    }

    /**
     * Checks if the IC Mechanic at the given point is cached. If not it will return false.
     *
     * @param pt of the ic
     *
     * @return true if ic is cached
     */
    public static boolean isCachedIC(Location pt) {

        return cachedICs.containsKey(pt);
    }

    /**
     * Gets the cached IC based on its location in the world. isCached should be checked before calling this method.
     *
     * @param pt of the ic
     *
     * @return cached ic.
     */
    public static IC getCachedIC(Location pt) {

        return cachedICs.get(pt);
    }

    /**
     * Adds the given IC to the cached IC list.
     *
     * @param pt of the ic
     * @param ic to add
     */
    public static void addCachedIC(Location pt, IC ic) {

        if (!ICMechanic.instance.cache) return;
        if(cachedICs.containsKey(pt)) return;
        cachedICs.put(pt, ic);
    }

    /**
     * Removes the given IC from the cache list based on its location.
     *
     * @param pt of the ic
     */
    public static void removeCachedIC(Location pt) {
      cachedICs.remove(pt);
    }

    /**
     * Clears the IC cache.
     *
     */
    public static void emptyCache() {

        cachedICs.clear();
    }

    public void registerICs(Server server) {

        // SISOs
        registerIC("MC1219", "auto craft", new AutomaticCrafter.Factory(server));
        registerIC("MC1234", "planter", new Planter.Factory(server));
    }
}
