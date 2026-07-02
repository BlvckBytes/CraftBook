package com.sk89q.craftbook.util;

import com.sk89q.craftbook.CraftBookPlayer;
import com.sk89q.craftbook.bukkit.BukkitCraftBookPlayer;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * @author Silthus, Me4502
 */
public final class LocationUtil {

    /**
     * Teleports the vehicle the player is in to the given destination.
     * Player is ejected out of the vehicle prior to teleportation,
     * otherwise it doesn't work.
     * @param player        The player that will be ejected and whose vehicle will be teleported.
     * @param newLocation   The location the vehicle will be teleported to.
     * @return The {@link Entity} the player was in or null if player was not in vehicle.
     */
    public static Entity ejectAndTeleportPlayerVehicle(CraftBookPlayer player, Location newLocation) {

        Player bukkitPlayer = ((BukkitCraftBookPlayer)player).getPlayer();

        if(bukkitPlayer == null || !bukkitPlayer.isInsideVehicle())
            return null;

        Entity vehicle = bukkitPlayer.getVehicle();

        if(vehicle == null)
            return null;

        newLocation.setYaw(vehicle.getLocation().getYaw());
        newLocation.setPitch(vehicle.getLocation().getPitch());

        // Vehicle must eject the passenger first,
        // otherwise vehicle.teleport() will not have any effect.
        vehicle.eject();
        vehicle.teleport(newLocation);
        return vehicle;
    }


    /**
     * Adds the player to the vehicle. Execution is delayed
     * by six ticks through a {@link BukkitRunnable} because
     * it doesn't work otherwise.
     *
     * @param vehicle   The vehicle that will set the player as a passenger.
     * @param player    The player that will be put inside the provided vehicle.
     */
    public static void addVehiclePassengerDelayed(Entity vehicle, CraftBookPlayer player) {

        Player bukkitPlayer = ((BukkitCraftBookPlayer)player).getPlayer();

        if(bukkitPlayer == null || vehicle == null)
            return;

        // The runnableDelayInTicks = 6 was the lowest number that
        // worked reliably across several tests.
        long runnableDelayInTicks = 6;

        // vehicle.teleport() seems to have a delay. Calling vehicle.setPassenger()
        // without the delayed runnable will not set the passenger.
        new BukkitRunnable(){
            @Override
            public void run () {
                vehicle.addPassenger(bukkitPlayer);
            }
        }.runTaskLater(CraftBookPlugin.inst(), runnableDelayInTicks);
    }
}
