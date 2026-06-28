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

package com.sk89q.craftbook.mechanics;

import com.sk89q.craftbook.CraftBookMechanic;
import com.sk89q.craftbook.CraftBookPlayer;
import com.sk89q.craftbook.bukkit.BukkitCraftBookPlayer;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.util.EventUtil;
import com.sk89q.craftbook.util.LocationUtil;
import com.sk89q.craftbook.util.ProtectionUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.craftbook.util.events.SignClickEvent;
import com.sk89q.craftbook.util.events.SourcedBlockRedstoneEvent;
import com.sk89q.util.yaml.YAMLProcessor;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The default elevator mechanism -- wall signs in a vertical column that teleport the player vertically when triggered.
 *
 * @author sk89q
 * @author hash
 */
public class Elevator implements CraftBookMechanic {

    private static final BlockFace[] SIGN_MOUNT_FACES = new BlockFace[] {
      BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private record LiftData(Elevator.Direction direction, boolean noBack, @Nullable String[] signLines) {}

    private HashSet<UUID> flyingPlayers;
    private HashMap<UUID, Entity> playerVehicles;

    private final Map<UUID, Long> lastTeleportByPlayerId = new HashMap<>();

    @Override
    public boolean enable() {
        if(elevatorSlowMove) {
            flyingPlayers = new HashSet<>();
            playerVehicles = new HashMap<>();
        }

        return true;
    }

    @Override
    public void disable() {

        if(flyingPlayers != null) {
            Iterator<UUID> it = flyingPlayers.iterator();
            while(it.hasNext()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(it.next());
                if(!op.isOnline()) {
                    it.remove();
                    continue;
                }
                op.getPlayer().setFlying(false);
                op.getPlayer().setAllowFlight(op.getPlayer().getGameMode() == GameMode.CREATIVE);
                it.remove();
            }

            flyingPlayers = null;
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {

        if(!elevatorSlowMove) return;
        if(!(event.getEntity() instanceof Player)) return;
        if(!flyingPlayers.contains(event.getEntity().getUniqueId())) return;
        if(event instanceof EntityDamageByEntityEvent) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        lastTeleportByPlayerId.remove(event.getPlayer().getUniqueId());

        if(!elevatorSlowMove) return;
        //Clean up mechanics that store players that we don't want anymore.
        Iterator<UUID> it = flyingPlayers.iterator();
        while(it.hasNext()) {
            UUID p = it.next();
            if(event.getPlayer().getUniqueId().equals(p)) {
                event.getPlayer().setFlying(false);
                event.getPlayer().setAllowFlight(event.getPlayer().getGameMode() == GameMode.CREATIVE);
                it.remove();
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {

        if(!EventUtil.passesFilter(event)) return;

        Direction dir = Direction.NONE;
        if(event.getLine(1).equalsIgnoreCase("[lift down]")) dir = Direction.DOWN;
        if(event.getLine(1).equalsIgnoreCase("[lift up]")) dir = Direction.UP;
        if(event.getLine(1).equalsIgnoreCase("[lift]")) dir = Direction.RECV;

        if(dir == Direction.NONE) return;
        CraftBookPlayer player = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());

        if(!player.hasPermission("craftbook.mech.elevator")) {
            if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                player.printError("mech.create-permission");
            SignUtil.cancelSign(event);
            return;
        }

        switch (dir) {
            case UP:
                player.print("mech.lift.up-sign-created");
                event.setLine(1, "[Lift Up]");
                break;
            case DOWN:
                player.print("mech.lift.down-sign-created");
                event.setLine(1, "[Lift Down]");
                break;
            case RECV:
                player.print("mech.lift.target-sign-created");
                event.setLine(1, "[Lift]");
                break;
            default:
                SignUtil.cancelSign(event);
        }
    }

    private enum Direction {
        NONE, UP, DOWN, RECV
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockRedstoneChange(SourcedBlockRedstoneEvent event) {

        if(!elevatorAllowRedstone || event.isMinor() || !event.isOn())
            return;

        if (!EventUtil.passesFilter(event))
            return;

        LiftData liftData = getLiftData(event.getBlock());

        Direction dir = liftData.direction;
        switch (dir) {
            case UP:
            case DOWN:
                break;
            case RECV:
                return;
            default:
                return;
        }

        BlockFace shift = dir == Direction.UP ? BlockFace.UP : BlockFace.DOWN;
        Block destination = findDestination(dir, shift, event.getBlock());

        if(destination == null) return;

        for(Player player : LocationUtil.getNearbyPlayers(event.getBlock().getLocation(), elevatorRedstoneRadius)) {

            CraftBookPlayer localPlayer = CraftBookPlugin.inst().wrapPlayer(player);
            if(flyingPlayers != null && flyingPlayers.contains(localPlayer.getUniqueId())) {
                localPlayer.printError("mech.lift.busy");
                continue;
            }

            if (!localPlayer.hasPermission("craftbook.mech.elevator.use")) {
                if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                    localPlayer.printError("mech.use-permission");
                continue;
            }

            makeItSo(localPlayer, destination, shift, liftData.noBack);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        var block = event.getClickedBlock();

        if (block == null)
            return;

        var blockType = block.getType();

        if(event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if(event.getHand() != EquipmentSlot.HAND) return;
            if (!(Tag.BUTTONS.isTagged(blockType))) return;
            onCommonClick(event, false);
        }

        if (event.getAction() == Action.PHYSICAL) {
            if (!(Tag.PRESSURE_PLATES.isTagged(blockType))) return;
            onCommonClick(event, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(SignClickEvent event) {

        if(event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if(event.getHand() != EquipmentSlot.HAND) return;
        onCommonClick(event, false);
    }

    private void onCommonClick(PlayerInteractEvent event, boolean isPressurePlate) {
        if (isPressurePlate) {
            var lastTeleport = lastTeleportByPlayerId.get(event.getPlayer().getUniqueId());

            if (lastTeleport != null && System.currentTimeMillis() - lastTeleport < 1000)
                return;
        }

        if (!EventUtil.passesFilter(event))
            return;

        CraftBookPlayer localPlayer = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());

        LiftData liftData = getLiftData(event.getClickedBlock());

        // check if this looks at all like something we're interested in first
        Direction dir = liftData.direction;
        switch (dir) {
            case UP:
            case DOWN:
                break;
            case RECV:
                localPlayer.printError("mech.lift.no-depart");
                return;
            default:
                return;
        }

        BlockFace shift = dir == Direction.UP ? BlockFace.UP : BlockFace.DOWN;
        Block destination = findDestination(dir, shift, event.getClickedBlock());

        if(destination == null) {
            localPlayer.printError("mech.lift.no-destination");
            return;
        }

        if(flyingPlayers != null && flyingPlayers.contains(localPlayer.getUniqueId())) {
            localPlayer.printError("mech.lift.busy");
            return;
        }

        if (!localPlayer.hasPermission("craftbook.mech.elevator.use")) {
            event.setCancelled(true);
            if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                localPlayer.printError("mech.use-permission");
            return;
        }

        if(!ProtectionUtil.canUse(event.getPlayer(), event.getClickedBlock().getLocation(), event.getBlockFace(), event.getAction())) {
            if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                localPlayer.printError("area.use-permissions");
            return;
        }

        makeItSo(localPlayer, destination, shift, liftData.noBack);

        event.setCancelled(true);
    }

    private Block findDestination(Direction dir, BlockFace shift, Block clickedBlock) {

        // find destination sign
        int f = dir == Direction.UP ? clickedBlock.getWorld().getMaxHeight() : clickedBlock.getWorld().getMinHeight();
        Block destination = clickedBlock;
        // heading up from top or down from bottom
        if (destination.getY() == f) {
            return null;
        }
        boolean loopd = false;
        while (true) {
            destination = destination.getRelative(shift);

            LiftData liftData = getLiftData(destination);

            Direction derp = liftData.direction;
            if (derp != Direction.NONE)
                break; // found it!

            if (destination.getY() == clickedBlock.getY()) {
                return null;
            }
            if (elevatorLoop && !loopd) {
                if (destination.getY() == clickedBlock.getWorld().getMaxHeight()) { // hit the top of the world
                    org.bukkit.Location low = destination.getLocation();
                    low.setY(clickedBlock.getWorld().getMinHeight());
                    destination = destination.getWorld().getBlockAt(low);
                    loopd = true;
                } else if (destination.getY() == clickedBlock.getWorld().getMinHeight()) { // hit the bottom of the world
                    org.bukkit.Location low = destination.getLocation();
                    low.setY(clickedBlock.getWorld().getMaxHeight());
                    destination = destination.getWorld().getBlockAt(low);
                    loopd = true;
                }
            } else {
                if (destination.getY() == clickedBlock.getWorld().getMaxHeight()) {
                    return null;
                }
                else if (destination.getY() == clickedBlock.getWorld().getMinHeight()) {
                    return null;
                }
            }
        }

        return destination;
    }

    private void makeItSo(CraftBookPlayer player, Block destination, BlockFace shift, boolean noBack) {
        // start with the block shifted vertically from the player
        // to the destination sign's height (plus one).
        Block floor = destination.getWorld().getBlockAt((int) Math.floor(player.getLocation().getX()), destination.getY() + 1,
                (int) Math.floor(player.getLocation().getZ()));
        // well, unless that's already a ceiling.
        if (floor.getType().isSolid()) {
            floor = floor.getRelative(BlockFace.DOWN);
        }

        // now iterate down until we find enough open space to stand in
        // or until we're 5 blocks away, which we consider too far.
        int foundFree = 0;
        boolean foundGround = false;
        for (int i = 0; i < 5; i++) {
            var floorType = floor.getType();
            if (!floorType.isSolid() || SignUtil.isSign(floor) || Tag.PRESSURE_PLATES.isTagged(floorType)) {
                foundFree++;
            } else {
                foundGround = true;
                break;
            }
            if (floor.getY() == destination.getWorld().getMinHeight()) {
                break;
            }
            floor = floor.getRelative(BlockFace.DOWN);
        }
        if (!foundGround) {
            player.printError("mech.lift.no-floor");
            return;
        }
        if (foundFree < 2) {
            player.printError("mech.lift.obstruct");
            return;
        }

        teleportPlayer(player, floor, destination, shift, noBack);
    }

    private void teleportPlayer(final CraftBookPlayer player, final Block floor, final Block destination, final BlockFace shift, boolean noBack) {

        final Location newLocation = CraftBookBukkitUtil.toLocation(player.getLocation());
        newLocation.setY(floor.getY() + 1);

        if(elevatorSlowMove) {

            final com.sk89q.worldedit.util.Location lastLocation = player.getLocation();

            if (player.isInsideVehicle()) {
                Player bukkitPlayer = ((BukkitCraftBookPlayer)player).getPlayer();
                playerVehicles.put(player.getUniqueId(), bukkitPlayer.getVehicle());

                LocationUtil.ejectAndTeleportPlayerVehicle(player, newLocation);

                // Ejecting the player out of the vehicle will move
                // the player to the side, so we have to correct this.
                player.temporarilyAttachMetadataFlag("essentials:ignore-teleport", noBack, () -> player.teleport(lastLocation));
            }

            new BukkitRunnable(){
                @Override
                public void run () {

                    OfflinePlayer offlinePlayer = ((BukkitCraftBookPlayer)player).getPlayer();
                    if(!offlinePlayer.isOnline()) {
                        cancel();
                        return;
                    }
                    Player p = offlinePlayer.getPlayer();
                    if(!flyingPlayers.contains(p.getUniqueId()) && !p.getAllowFlight())
                        flyingPlayers.add(p.getUniqueId());

                    enableFlightMode(p);

                    newLocation.setPitch(p.getLocation().getPitch());
                    newLocation.setYaw(p.getLocation().getYaw());

                    boolean isPlayerAlmostAtDestination = Math.abs(floor.getY() - p.getLocation().getY()) < 0.7;

                    if(isPlayerAlmostAtDestination) {
                        finishElevatingPlayer(p);
                        return;
                    }

                    boolean didPlayerLeaveElevator =
                            lastLocation.getBlockX() != p.getLocation().getBlockX() ||
                            lastLocation.getBlockZ() != p.getLocation().getBlockZ();

                    if(didPlayerLeaveElevator) {
                        player.print("mech.lift.leave");
                        disableFlightMode(p);
                        playerVehicles.remove(p.getUniqueId());
                        cancel();
                        return;
                    }

                    Direction playerVerticalMovement = getVerticalDirection(p.getLocation(), newLocation);

                    switch (playerVerticalMovement) {
                        case UP:
                            // Teleporting the player up inside solid blocks will not execute
                            // the teleport but rather cause the player to "swim" in mid air.
                            // See https://dev.enginehub.org/youtrack/issue/CRAFTBOOK-3464
                            // Thus we'll simply teleport the player to the ceiling in that case.
                            if (isSolidBlockOccludingMovement(p, playerVerticalMovement)) {
                                finishElevatingPlayer(p);
                                return;
                            } else {
                                p.setVelocity(new Vector(0, elevatorMoveSpeed, 0));
                            }
                            break;
                        case DOWN:
                            // Contrary to moving the player up,
                            // moving down into solid blocks works just fine.
                            p.setVelocity(new Vector(0, -elevatorMoveSpeed, 0));
                            if (isSolidBlockOccludingMovement(p, playerVerticalMovement))
                                player.temporarilyAttachMetadataFlag("essentials:ignore-teleport", noBack, () -> p.teleport(p.getLocation().add(0, -elevatorMoveSpeed, 0)));
                            break;
                        default:
                            // Player is not moving
                            finishElevatingPlayer(p);
                            return;
                    }

                    lastLocation.setY(p.getLocation().getY());
                }

                private void finishElevatingPlayer(Player p) {
                    player.temporarilyAttachMetadataFlag("essentials:ignore-teleport", noBack, () -> p.teleport(newLocation));
                    teleportFinish(player, destination, shift);
                    disableFlightMode(p);
                    setPassengerIfPlayerWasInVehicle(player);
                    cancel();
                }

            }.runTaskTimer(CraftBookPlugin.inst(), 1, 1);
        } else {
            // Teleport!
            if (player.isInsideVehicle()) {
                Entity teleportedVehicle = LocationUtil.ejectAndTeleportPlayerVehicle(player, newLocation);

                player.temporarilyAttachMetadataFlag("essentials:ignore-teleport", noBack, () -> player.teleport(BukkitAdapter.adapt(newLocation)));

                LocationUtil.addVehiclePassengerDelayed(teleportedVehicle, player);
            } else {
                player.temporarilyAttachMetadataFlag("essentials:ignore-teleport", noBack, () -> player.teleport(BukkitAdapter.adapt(newLocation)));
            }

            teleportFinish(player, destination, shift);
        }
    }

    private void enableFlightMode(Player p) {
        p.setAllowFlight(true);
        p.setFlying(true);
        p.setFallDistance(0f);
        p.setNoDamageTicks(2);
    }

    private void disableFlightMode(Player p) {
        if (!flyingPlayers.contains(p.getUniqueId())) {
            return;
        }
        p.setFlying(false);
        p.setAllowFlight(p.getGameMode() == GameMode.CREATIVE);
        flyingPlayers.remove(p.getUniqueId());
    }

    private Direction getVerticalDirection(Location from, Location to)
    {
        if(from.getY() < to.getY())
            return Direction.UP;
        else if (from.getY() > to.getY())
            return Direction.DOWN;
        else
            return Direction.NONE;
    }

    private boolean isSolidBlockOccludingMovement(Player p, Direction direction) {
        int verticalDistance = direction == Direction.UP ? 2 : -1;
        return p.getLocation().clone().add(0, verticalDistance, 0).getBlock().getType().isSolid();
    }

    private void setPassengerIfPlayerWasInVehicle(CraftBookPlayer player) {

        boolean wasPlayerInVehicle = playerVehicles.containsKey(player.getUniqueId());

        if(wasPlayerInVehicle) {
            Entity vehicle = playerVehicles.get(player.getUniqueId());
            LocationUtil.addVehiclePassengerDelayed(vehicle, player);
            playerVehicles.remove(player.getUniqueId());
        }
    }

    private void teleportFinish(CraftBookPlayer player, Block destination, BlockFace shift) {
        lastTeleportByPlayerId.put(player.getUniqueId(), System.currentTimeMillis());

        // Now, we want to read the sign so we can tell the player
        // his or her floor, but as that may not be avilable, we can
        // just print a generic message
        var signLines = getLiftData(destination).signLines;

        if (signLines == null)
            return;

        var title = signLines[0];

        if (!title.isEmpty()) {
            player.print(player.translate("mech.lift.floor") + ": " + title);
        } else {
            player.print(shift.getModY() > 0 ? "mech.lift.up" : "mech.lift.down");
        }
    }

    private LiftData getLiftData(Block block) {
        if (!SignUtil.isSign(block)) {
            var blockType = block.getType();

            if (elevatorButtonEnabled && Tag.BUTTONS.isTagged(blockType)) {
                Switch b = (Switch) block.getBlockData();
                Block sign = block.getRelative(b.getFacing().getOppositeFace(), 2);
                if (SignUtil.isSign(sign))
                    return getLiftData(SignUtil.getFrontLinesOrEmpty(sign));
            }

            if (elevatorPressurePlateEnabled && Tag.PRESSURE_PLATES.isTagged(blockType)) {
                for (var mountFace : SIGN_MOUNT_FACES) {
                    var attachedSign = block.getRelative(mountFace.getModX() * 2, 0, mountFace.getModZ() * 2);

                    if (!SignUtil.isSign(attachedSign))
                        continue;

                    var data = getLiftData(SignUtil.getFrontLinesOrEmpty(attachedSign));

                    if (data.direction != Direction.NONE)
                        return data;
                }
            }

            return new LiftData(Direction.NONE, false, null);
        }

        return getLiftData(SignUtil.getFrontLinesOrEmpty(block));
    }

    private static LiftData getLiftData(String[] signLines) {
        var noBack = signLines[3].equalsIgnoreCase("no-back");

        if (signLines[1].equalsIgnoreCase("[Lift Up]"))
            return new LiftData(Direction.UP, noBack, signLines);

        if (signLines[1].equalsIgnoreCase("[Lift Down]"))
            return new LiftData(Direction.DOWN, noBack, signLines);

        if (signLines[1].equalsIgnoreCase("[Lift]"))
            return new LiftData(Direction.RECV, noBack, signLines);

        return new LiftData(Direction.NONE, false, signLines);
    }

    private boolean elevatorAllowRedstone;
    private int elevatorRedstoneRadius;
    private boolean elevatorButtonEnabled;
    private boolean elevatorPressurePlateEnabled;
    private boolean elevatorLoop;
    private boolean elevatorSlowMove;
    private double elevatorMoveSpeed;


    @Override
    public void loadConfiguration (YAMLProcessor config, String path) {

        config.setComment(path + "allow-redstone", "Allows elevators to be triggered by redstone, which will move all players in a radius.");
        elevatorAllowRedstone = config.getBoolean(path + "allow-redstone", false);

        config.setComment(path + "redstone-player-search-radius", "The radius that elevators will look for players in when triggered by redstone.");
        elevatorRedstoneRadius = config.getInt(path + "redstone-player-search-radius", 3);

        config.setComment(path + "enable-buttons", "Allow elevators to be used by a button on the other side of the block.");
        elevatorButtonEnabled = config.getBoolean(path + "enable-buttons", true);

        config.setComment(path + "enable-pressure-plates", "Allow elevators to be used by a pressure-plate above the sign or on the block of the sign itself.");
        elevatorPressurePlateEnabled = config.getBoolean(path + "enable-pressure-plates", true);

        config.setComment(path + "allow-looping", "Allows elevators to loop the world height. The heighest lift up will go to the next lift on the bottom of the world and vice versa.");
        elevatorLoop = config.getBoolean(path + "allow-looping", false);

        config.setComment(path + "smooth-movement", "Causes the elevator to slowly move the player between floors instead of instantly.");
        elevatorSlowMove = config.getBoolean(path + "smooth-movement", false);

        config.setComment(path + "smooth-movement-speed", "The speed at which players move from floor to floor when smooth movement is enabled.");
        elevatorMoveSpeed = config.getDouble(path + "smooth-movement-speed", 0.5);
    }
}
