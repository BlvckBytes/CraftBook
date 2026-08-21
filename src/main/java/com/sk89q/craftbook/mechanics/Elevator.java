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
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.util.LocationUtil;
import com.sk89q.craftbook.util.ProtectionUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.craftbook.util.events.SignClickEvent;
import com.sk89q.util.yaml.YAMLProcessor;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
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

    private final Map<UUID, Long> lastTeleportByPlayerId = new HashMap<>();

    @Override
    public boolean enable() {
        return true;
    }

    @Override
    public void disable() {}

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        lastTeleportByPlayerId.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {
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
        Block destination = findDestination(shift, event.getClickedBlock());

        if(destination == null) {
            localPlayer.printError("mech.lift.no-destination");
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

    private Block findDestination(BlockFace shift, Block clickedBlock) {
        var destination = clickedBlock;

        while (true) {
            destination = destination.getRelative(shift);

            LiftData liftData = getLiftData(destination);

            var direction = liftData.direction;

            if (direction != Direction.NONE)
                break;

            if (destination.getY() == clickedBlock.getY())
                return null;

            if (destination.getY() >= clickedBlock.getWorld().getMaxHeight())
                return null;

            if (destination.getY() <= clickedBlock.getWorld().getMinHeight())
                return null;
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

    private boolean elevatorButtonEnabled;
    private boolean elevatorPressurePlateEnabled;

    @Override
    public void loadConfiguration (YAMLProcessor config, String path) {
        config.setComment(path + "enable-buttons", "Allow elevators to be used by a button on the other side of the block.");
        elevatorButtonEnabled = config.getBoolean(path + "enable-buttons", true);

        config.setComment(path + "enable-pressure-plates", "Allow elevators to be used by a pressure-plate above the sign or on the block of the sign itself.");
        elevatorPressurePlateEnabled = config.getBoolean(path + "enable-pressure-plates", true);
    }
}
