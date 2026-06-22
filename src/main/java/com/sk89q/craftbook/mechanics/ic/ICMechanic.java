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

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.CraftBookMechanic;
import com.sk89q.craftbook.CraftBookPlayer;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.ic.gates.world.blocks.Planter;
import com.sk89q.craftbook.mechanics.ic.gates.world.items.crafter.AutomaticCrafter;
import com.sk89q.craftbook.mechanics.pipe.CachedBlock;
import com.sk89q.craftbook.mechanics.pipe.PipePutEvent;
import com.sk89q.craftbook.util.EventUtil;
import com.sk89q.craftbook.util.RegexUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.craftbook.util.events.SelfTriggerPingEvent;
import com.sk89q.craftbook.util.events.SelfTriggerThinkEvent;
import com.sk89q.craftbook.util.events.SelfTriggerUnregisterEvent;
import com.sk89q.craftbook.util.events.SelfTriggerUnregisterEvent.UnregisterReason;
import com.sk89q.util.yaml.YAMLProcessor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Mechanic wrapper for ICs. The mechanic manager dispatches events to this mechanic,
 * and then it is processed and passed onto the associated IC.
 *
 * @author sk89q
 */
public class ICMechanic implements CraftBookMechanic {

    private final Map<Location, IC> icByLocation = new HashMap<>();
    private final Map<String, ICFactory> factoryByIcId = new LinkedHashMap<>();

    public static ICMechanic instance;

    public ICMechanic() {
        registerIC(new AutomaticCrafter.Factory());
        registerIC(new Planter.Factory());

        instance = this;
    }

    @Override
    public boolean enable() {
        return true;
    }

    @Override
    public void disable() {
        icByLocation.clear();
    }

    private ICFactory getFactoryById(String id) {
        return factoryByIcId.get(id.toLowerCase());
    }

    private IC getCachedIC(Location pt) {
        return icByLocation.get(pt);
    }

    private void addCachedIC(Location pt, IC ic) {
        if(icByLocation.containsKey(pt)) return;
        icByLocation.put(pt, ic);
    }

    private void removeCachedIC(Location pt) {
        icByLocation.remove(pt);
    }

    public void registerIC(ICFactory factory) {
        var id = factory.getId();
        String bracketedId = "[" + id + "]";

        if (factoryByIcId.containsKey(id.toLowerCase()))
            return;

        Matcher matcher = RegexUtil.IC_PATTERN.matcher(bracketedId);

        if (!matcher.matches())
            return;

        factoryByIcId.put(id.toLowerCase(), factory);
    }

    private IC setupOrAccessIC(Block block, boolean create) {

        // if we're not looking at a wall sign, it can't be an IC.
        if (!SignUtil.isWallSign(block)) return null;
        ChangedSign sign = CraftBookBukkitUtil.toChangedSign(block);

        // detect the text on the sign to see if it's any kind of IC at all.
        Matcher matcher = RegexUtil.IC_PATTERN.matcher(sign.getLine(1));
        if (!matcher.matches()) return null;

        String id = matcher.group(1);

        // now actually try to pull up an IC of that id number.
        ICFactory factory = getFactoryById(id);

        if (factory == null) {
            CraftBookPlugin.logger().warning("\"" + sign.getLine(1) + "\" should be an IC ID, but no IC registered under that ID could be found.");
            block.breakNaturally();
            return null;
        }

        var ic = getCachedIC(block.getLocation());

        // check if the ic is cached and get that single instance instead of creating a new one
        if (ic != null) {
            if(ic.getSign().updateSign(sign)) {
                removeCachedIC(block.getLocation());
                ic = factory.create(sign);
                ic.load();
                addCachedIC(block.getLocation(), ic);
            }
        } else if (create) {
            ic = factory.create(sign);
            ic.load();
            addCachedIC(block.getLocation(), ic);
        } else
            return null;

        if (ic instanceof SelfTriggeredIC)
            CraftBookPlugin.inst().getSelfTriggerManager().registerSelfTrigger(block.getLocation());

        return ic;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onThinkPing(SelfTriggerPingEvent event) {

        if(!EventUtil.passesFilter(event)) return;

        setupOrAccessIC(event.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onThinkUnregister(SelfTriggerUnregisterEvent event) {

        if(!EventUtil.passesFilter(event)) return;

        var ic = setupOrAccessIC(event.getBlock(), false);

        if(ic != null)
            ic.unload();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onThink(SelfTriggerThinkEvent event) {
        if(!EventUtil.passesFilter(event)) return;

        var ic = setupOrAccessIC(event.getBlock(), true);

        if(ic instanceof SelfTriggeredIC selfTriggeredIC) {
            event.setHandled(true);
            selfTriggeredIC.think();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if(!EventUtil.passesFilter(event)) return;

        var ic = setupOrAccessIC(event.getBlock(), false);

        if(ic == null) return;

        // remove the ic from cache
        CraftBookPlugin.inst().getSelfTriggerManager().unregisterSelfTrigger(event.getBlock().getLocation(), UnregisterReason.BREAK);
        removeCachedIC(event.getBlock().getLocation());
        if(!event.isCancelled())
            ic.unload();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPipePut(PipePutEvent event) {

        if(!EventUtil.passesFilter(event)) return;

        if (!CachedBlock.isSign(event.getCachedPuttingBlock())) return;

        var ic = setupOrAccessIC(event.getPuttingBlock(), true);

        if(ic == null) return;

        if(ic instanceof PipeInputIC pipeIC)
            pipeIC.onPipeTransfer(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {

        if(!EventUtil.passesFilter(event)) return;

        initializeIC(event.getBlock(), CraftBookPlugin.inst().wrapPlayer(event.getPlayer()), event);
    }

    public void initializeIC(final Block block, final CraftBookPlayer player, final SignChangeEvent event) {
        Matcher matcher = RegexUtil.IC_PATTERN.matcher(event.getLine(1));

        if (!matcher.matches()) {
            return;
        }

        String id = matcher.group(1);

        if (!SignUtil.isWallSign(block)) {
            player.printError("Only wall signs are used for ICs.");
            SignUtil.cancelSign(event);
            return;
        }

        var cachedIC = getCachedIC(block.getLocation());

        if (cachedIC != null) {
            cachedIC.unload();
            removeCachedIC(block.getLocation());
        }

        var factory = getFactoryById(id);

        if (factory == null) {
            player.printError("Unknown IC detected: " + id);
            SignUtil.cancelSign(event);
            return;
        }

        try {
            checkPermissions(player, factory, factory.getId().toLowerCase(Locale.ENGLISH));
        } catch (ICVerificationException e) {
            player.printError(e.getMessage());
            SignUtil.cancelSign(event);
            return;
        }

        Bukkit.getServer().getScheduler().runTask(CraftBookPlugin.inst(), () -> {
            ChangedSign sign = new ChangedSign(event.getBlock(), event.getLines());

            try {
                factory.verify(sign);
            } catch (ICVerificationException e) {
                player.printError(e.getMessage());
                event.getBlock().breakNaturally();
                return;
            }

            IC ic = factory.create(sign);
            ic.load();

            sign.setLine(1, "[" + factory.getId() + "]");
            sign.setLine(0, ic.getSignTitle());

            sign.update(false);

            if (ic instanceof SelfTriggeredIC)
                CraftBookPlugin.inst().getSelfTriggerManager().registerSelfTrigger(block.getLocation());

            player.print(player.translate("mech.ic.create") + " " + factory.getId() + ": " + ic.getTitle() + ".");
        });
    }

    public static void checkPermissions(CraftBookPlayer player, ICFactory factory, String id) throws ICVerificationException {

        if (player.hasPermission("craftbook.ic." + id.toLowerCase(Locale.ENGLISH))) {
            return;
        }

        if (player.hasPermission("craftbook.ic." + factory.getClass().getPackage().getName() + '.' + id.toLowerCase(Locale.ENGLISH))) {
            return;
        }

        throw new ICVerificationException("You don't have permission to use " + id.toLowerCase(Locale.ENGLISH) + ".");
    }

    public double maxRange;

    @Override
    public void loadConfiguration (YAMLProcessor config, String path) {
        config.setComment(path + "max-radius", "The max radius IC's with a radius setting can use. (WILL cause lag at higher values)");
        maxRange = config.getDouble(path + "max-radius", 10);
    }
}