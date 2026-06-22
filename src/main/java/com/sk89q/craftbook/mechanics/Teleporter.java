package com.sk89q.craftbook.mechanics;

import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.CraftBookMechanic;
import com.sk89q.craftbook.CraftBookPlayer;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.util.EventUtil;
import com.sk89q.craftbook.util.LocationUtil;
import com.sk89q.craftbook.util.ProtectionUtil;
import com.sk89q.craftbook.util.RegexUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.craftbook.util.events.SignClickEvent;
import com.sk89q.util.yaml.YAMLProcessor;
import com.sk89q.worldedit.util.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Teleporter Mechanism. Based off Elevator
 *
 * @author sk89q
 * @author hash
 * @author Me4502
 */
public class Teleporter implements CraftBookMechanic {

    private record BlockOffset(int modX, int modY, int modZ) {
        BlockOffset(BlockFace face) {
            this(face.getModX(), face.getModY(), face.getModZ());
        }

        BlockOffset add(int modX, int modY, int modZ) {
            return new BlockOffset(this.modX + modX, this.modY + modY, this.modZ + modZ);
        }

        Block getRelative(Block origin) {
            return origin.getRelative(modX, modY, modZ);
        }
    }

    private static final BlockOffset[] PRESSURE_PLATE_SIGN_OFFSETS = {
      new BlockOffset(BlockFace.SELF).add(0, -2, 0),
      new BlockOffset(BlockFace.NORTH).add(0, -2, 0),
      new BlockOffset(BlockFace.EAST).add(0, -2, 0),
      new BlockOffset(BlockFace.SOUTH).add(0, -2, 0),
      new BlockOffset(BlockFace.WEST).add(0, -2, 0)
    };

    private final Map<UUID, Long> lastTeleportByPlayerId = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {

        if(!EventUtil.passesFilter(event)) return;

        if (!event.getLine(1).equalsIgnoreCase("[Teleporter]")) return;

        CraftBookPlayer localPlayer = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());

        if(!localPlayer.hasPermission("craftbook.mech.teleporter")) {
            if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                localPlayer.printError("mech.create-permission");
            SignUtil.cancelSign(event);
            return;
        }

        String[] pos = RegexUtil.COLON_PATTERN.split(event.getLine(2));
        if (pos.length <= 2) {
            localPlayer.printError("mech.teleport.invalidcoords");
            SignUtil.cancelSign(event);
            return;
        }

        localPlayer.print("mech.teleport.create");
        event.setLine(1, "[Teleporter]");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if(event.getClickedBlock() != null && SignUtil.isSign(event.getClickedBlock())) return;

        onCommonClick(event);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(SignClickEvent event) {

        if(event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        onCommonClick(event);
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent event) {
        lastTeleportByPlayerId.remove(event.getPlayer().getUniqueId());
    }

    public void onCommonClick(PlayerInteractEvent event) {

        if (!EventUtil.passesFilter(event))
            return;

        if (event.getClickedBlock() == null)
            return;

        CraftBookPlayer localPlayer;

        Block trigger = null;

        boolean noBack = false;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && SignUtil.isSign(event.getClickedBlock())) {
            if (event.getHand() != EquipmentSlot.HAND) return;
            localPlayer = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());
            ChangedSign s = CraftBookBukkitUtil.toChangedSign(event.getClickedBlock());
            if (!s.getLine(1).equals("[Teleporter]")) return;
            String[] pos = RegexUtil.COLON_PATTERN.split(s.getLine(2));
            noBack = s.getLine(3).equalsIgnoreCase("no-back");
            if (pos.length <= 2) {
                localPlayer.printError("mech.teleport.invalidcoords");
                return;
            }
            trigger = event.getClickedBlock();
        } else if (Tag.BUTTONS.isTagged(event.getClickedBlock().getType())) {
            localPlayer = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());
            Directional b = (Directional) event.getClickedBlock().getBlockData();
            Block sign = event.getClickedBlock().getRelative(b.getFacing().getOppositeFace(), 2);
            if (SignUtil.isSign(sign)) {
                ChangedSign s = CraftBookBukkitUtil.toChangedSign(sign);
                if (!s.getLine(1).equals("[Teleporter]")) return;
                String[] pos = RegexUtil.COLON_PATTERN.split(s.getLine(2));
                noBack = s.getLine(3).equalsIgnoreCase("no-back");
                if (pos.length <= 2) {
                    localPlayer.printError("mech.teleport.invalidcoords");
                    return;
                }
                trigger = sign;
            }
        } else if (event.getAction() == Action.PHYSICAL && Tag.PRESSURE_PLATES.isTagged(event.getClickedBlock().getType())) {
            var lastTeleport = lastTeleportByPlayerId.get(event.getPlayer().getUniqueId());

            if (lastTeleport != null && System.currentTimeMillis() - lastTeleport < 1000)
                return;

            localPlayer = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());

            for (var offset : PRESSURE_PLATE_SIGN_OFFSETS) {
                var sign = offset.getRelative(event.getClickedBlock());

                if (SignUtil.isSign(sign)) {
                    ChangedSign s = CraftBookBukkitUtil.toChangedSign(sign);

                    if (!s.getLine(1).equals("[Teleporter]"))
                        continue;

                    String[] pos = RegexUtil.COLON_PATTERN.split(s.getLine(2));
                    noBack = s.getLine(3).equalsIgnoreCase("no-back");

                    if (pos.length <= 2) {
                        localPlayer.printError("mech.teleport.invalidcoords");
                        return;
                    }

                    trigger = sign;
                    break;
                }
            }
        } else
            return;

        if(trigger == null) return;

        if (!localPlayer.hasPermission("craftbook.mech.teleporter.use")) {
            if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                localPlayer.printError("mech.use-permission");
            return;
        }

        if(!ProtectionUtil.canUse(event.getPlayer(), event.getClickedBlock().getLocation(), event.getBlockFace(), event.getAction())) {
            if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                localPlayer.printError("area.use-permissions");
            return;
        }

        makeItSo(localPlayer, trigger, noBack);

        event.setCancelled(true);
    }

    private void makeItSo(CraftBookPlayer player, Block trigger, boolean noBack) {
        // start with the block shifted vertically from the player
        // to the destination sign's height (plus one).
        // check if this looks at all like something we're interested in first

        double toX = 0;
        double toY = 0;
        double toZ = 0;

        if (SignUtil.isSign(trigger)) {
            ChangedSign s = CraftBookBukkitUtil.toChangedSign(trigger);
            String[] pos = RegexUtil.COLON_PATTERN.split(s.getLine(2));
            if (pos.length > 2) {
                try {
                    toX = Double.parseDouble(pos[0]);
                    toY = Double.parseDouble(pos[1]);
                    toZ = Double.parseDouble(pos[2]);
                } catch (Exception e) {
                    player.printError("mech.teleport.arriveonly");
                    return;
                }
            } else {
                player.printError("mech.teleport.arriveonly");
                return;
            }
        }

        if (requireSign) {
            Block location = trigger.getWorld().getBlockAt((int) toX, (int) toY, (int) toZ);
            if (SignUtil.isSign(location)) {
                if (!checkTeleportSign(player, location)) {
                    return;
                }
            } else if (Tag.BUTTONS.isTagged(location.getType())) {
                Directional b = (Directional) location.getBlockData();
                Block sign = location.getRelative(b.getFacing(), 2);
                if (!checkTeleportSign(player, sign)) {
                    return;
                }
            } else if (Tag.PRESSURE_PLATES.isTagged(location.getType())) {
                var hadValidSign = false;

                for (var offset : PRESSURE_PLATE_SIGN_OFFSETS) {
                    Block sign = offset.getRelative(location);

                    if (checkTeleportSign(player, sign)) {
                        hadValidSign = true;
                        break;
                    }
                }

                if (!hadValidSign)
                    return;
            } else {
                player.printError("mech.teleport.sign");
                return;
            }
        }

        Block floor = trigger.getWorld().getBlockAt((int) Math.floor(toX), (int) (Math.floor(toY) + 1),
                (int) Math.floor(toZ));
        // well, unless that's already a ceiling.
        if (floor.getType().isSolid())
            floor = floor.getRelative(BlockFace.DOWN);

        // now iterate down until we find enough open space to stand in
        // or until we're 5 blocks away, which we consider too far.
        int foundFree = 0;
        for (int i = 0; i < 5; i++) {
            var floorType = floor.getType();

            if (!floorType.isSolid() || SignUtil.isSign(floor) || Tag.PRESSURE_PLATES.isTagged(floorType))
                foundFree++;
            else
                break;
            if (floor.getY() == floor.getWorld().getMinHeight()) break;
            floor = floor.getRelative(BlockFace.DOWN);
        }
        if (foundFree < 2) {
            player.printError("mech.teleport.obstruct");
            return;
        }

        // Teleport!
        Location subspaceRift = player.getLocation()
            .setX(floor.getX() + 0.5)
            .setY(floor.getY() + 1.0)
            .setZ(floor.getZ() + 0.5);

        if (maxRange > 0) {
            if (subspaceRift.toVector().distanceSq(player.getLocation().toVector()) > maxRange * maxRange) {
                player.print("mech.teleport.range");
                return;
            }
        }

        lastTeleportByPlayerId.put(player.getUniqueId(), System.currentTimeMillis());

        if (player.isInsideVehicle()) {
            org.bukkit.Location newLocation = CraftBookBukkitUtil.toLocation(subspaceRift);
            Entity teleportedVehicle = LocationUtil.ejectAndTeleportPlayerVehicle(player, newLocation);

            player.temporarilyAttachMetadataFlag("essentials:ignore-teleport", noBack, () -> player.teleport(subspaceRift));

            LocationUtil.addVehiclePassengerDelayed(teleportedVehicle, player);
        } else {
            player.temporarilyAttachMetadataFlag("essentials:ignore-teleport", noBack, () -> player.teleport(subspaceRift));
        }

        player.print("mech.teleport.alert");
    }

    private static boolean checkTeleportSign(CraftBookPlayer player, Block sign) {
        if (!SignUtil.isSign(sign)) {
            player.printError("mech.teleport.sign");
            return false;
        }

        ChangedSign s = CraftBookBukkitUtil.toChangedSign(sign);
        if (!s.getLine(1).equals("[Teleporter]")) {
            player.printError("mech.teleport.sign");
            return false;
        }

        return true;
    }

    private boolean requireSign;
    private int maxRange;

    @Override
    public boolean enable() {
        return true;
    }

    @Override
    public void disable() {}

    @Override
    public void loadConfiguration (YAMLProcessor config, String path) {

        config.setComment(path + "require-sign", "Require a sign to be at the destination of the teleportation.");
        requireSign = config.getBoolean(path + "require-sign", false);

        config.setComment(path + "max-range", "The maximum distance between the start and end of a teleporter. Set to 0 for infinite.");
        maxRange = config.getInt(path + "max-range", 0);
    }
}
