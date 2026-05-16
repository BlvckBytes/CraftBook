package com.sk89q.craftbook.util.compat.companion;

import org.bukkit.plugin.Plugin;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;

public class CompanionPlugins {

    /**
     * Required dependencies
     */
    private WorldEditPlugin worldEditPlugin;

    /**
     * Optional dependencies
     */
    private WorldGuardPlugin worldGuardPlugin;

    public void initiate(CraftBookPlugin plugin) {

        // Check plugin for checking the active states of a plugin
        Plugin checkPlugin;

        // Check for WorldEdit
        checkPlugin = plugin.getServer().getPluginManager().getPlugin("WorldEdit");
        if (checkPlugin != null && checkPlugin instanceof WorldEditPlugin) {
            worldEditPlugin = (WorldEditPlugin) checkPlugin;
        } else {
            try {
                //noinspection UnusedDeclaration
                @SuppressWarnings("unused")
                String s = WorldEditPlugin.CUI_PLUGIN_CHANNEL;
            } catch (Throwable t) {
                plugin.getLogger().severe("WorldEdit detection has failed!");
                plugin.getLogger().severe("WorldEdit is a required dependency, Craftbook disabled!");
                return;
            }
        }

        // Resolve WorldGuard
        checkPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (checkPlugin != null && checkPlugin instanceof WorldGuardPlugin) {
            worldGuardPlugin = (WorldGuardPlugin) checkPlugin;
        } else worldGuardPlugin = null;
    }

    /**
     * Gets a copy of the {@link WorldEditPlugin}.
     *
     * This method cannot return null.
     *
     * @return The {@link WorldEditPlugin} instance
     */
    public WorldEditPlugin getWorldEdit() {

        return worldEditPlugin;
    }

    /**
     * Gets the {@link WorldGuardPlugin} for non-build checks.
     *
     * This method can return null.
     *
     * @return {@link WorldGuardPlugin}
     */
    public WorldGuardPlugin getWorldGuard() {

        return worldGuardPlugin;
    }
}