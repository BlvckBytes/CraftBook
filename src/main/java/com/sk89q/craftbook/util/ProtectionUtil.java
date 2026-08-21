package com.sk89q.craftbook.util;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.logging.Level;

public final class ProtectionUtil {

    /**
     * Checks to see if a player can use at a location. This will return
     * true if region protection is disabled or WorldGuard is not found.
     *
     * @param player The player to check.
     * @param loc    The location to check at.
     *
     * @return whether {@code player} can build at {@code loc}
     */
    public static boolean canUse(Player player, Location loc, BlockFace face, Action action) {

        if (!shouldUseProtection()) return true;
        if (CraftBookPlugin.inst().getConfiguration().advancedBlockChecks) {
            PlayerInteractEvent event = new PlayerInteractEvent(player, action == null ? Action.RIGHT_CLICK_BLOCK : action, player.getItemInHand(), loc.getBlock(), face == null ? BlockFace.SELF : face);
            callFakeEvent(event);
            if (!event.isCancelled() && CraftBookPlugin.inst().getConfiguration().obeyWorldguard && CraftBookPlugin.plugins.getWorldGuard() != null) {
                return CraftBookPlugin.plugins.getWorldGuard().createProtectionQuery().testBlockInteract(player, loc.getBlock());
            }
            return !event.isCancelled();
        }
        return !CraftBookPlugin.inst().getConfiguration().obeyWorldguard || CraftBookPlugin.plugins.getWorldGuard() == null || CraftBookPlugin.plugins.getWorldGuard().createProtectionQuery().testBlockInteract(player, loc.getBlock());
    }

    /**
     * Checks whether or not protection related code should even be tested.
     * 
     * @return should check or not.
     */
    public static boolean shouldUseProtection() {

        return CraftBookPlugin.inst().getConfiguration().advancedBlockChecks || CraftBookPlugin.inst().getConfiguration().obeyWorldguard;
    }

    private static void callFakeEvent(Event event) {
        for (var listener : event.getHandlers().getRegisteredListeners()) {
            var plugin = listener.getPlugin();

            if (!plugin.isEnabled())
                continue;

            var pluginName = plugin.getName();

            if (pluginName.equals("CraftBook") || pluginName.equals("CraftBook5"))
                continue;

            try {
                listener.callEvent(event);
            } catch (Exception e) {
                CraftBookPlugin.logger().log(Level.SEVERE, "Could not pass event " + event.getEventName() + " to " + listener.getPlugin().getPluginMeta().getName(), e);
            }
        }
    }
}