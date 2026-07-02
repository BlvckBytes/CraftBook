// $Id$
/*
 * CraftBook Copyright (C) 2010, 2011 sk89q <http://www.sk89q.com>
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

package com.sk89q.craftbook.bukkit;

import com.sk89q.craftbook.util.EventUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.craftbook.util.events.SignClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashSet;
import java.util.Set;

/**
 * This adapter hooks a mechanic manager up to Bukkit.
 *
 * @author sk89q
 */
final class MechanicListenerAdapter implements Listener {

    private final Set<String> signClickTimer = new HashSet<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(final PlayerInteractEvent event) {

        if (!EventUtil.passesFilter(event))
            return;

        Block block = null;
        Action action = null;
        if(event.getAction() == Action.RIGHT_CLICK_AIR) {
            try {
                block = event.getPlayer().getTargetBlock(null, 5);
                if(block != null && block.getType() != Material.AIR)
                    action = Action.RIGHT_CLICK_BLOCK;
                else
                    action = Action.RIGHT_CLICK_AIR;
            } catch(Exception e) {
                //Bukkit randomly errors. Catch the error.
            }
        } else {
            block = event.getClickedBlock();
            action = event.getAction();
        }

        if(block != null && SignUtil.isSign(block) && event.getHand() == EquipmentSlot.HAND) {
            if(CraftBookPlugin.inst().getConfiguration().signClickTimeout > 0) {
                if(signClickTimer.contains(event.getPlayer().getName())) {
                    return;
                } else {
                    signClickTimer.add(event.getPlayer().getName());
                    Bukkit.getScheduler().runTaskLater(CraftBookPlugin.inst(), () -> signClickTimer.remove(event.getPlayer().getName()), CraftBookPlugin.inst().getConfiguration().signClickTimeout);
                }
            }
            SignClickEvent ev = new SignClickEvent(event.getPlayer(), action, event.getItem(), block, event.getBlockFace());
            CraftBookPlugin.inst().getServer().getPluginManager().callEvent(ev);
            if(ev.isCancelled())
                event.setCancelled(true);
        }
    }
}