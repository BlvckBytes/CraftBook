package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class WorldGuardUtil {

  public static void forEachTargetedRegionPlayer(
    Location location,
    boolean notifyOwnersOfRegion,
    boolean notifyMembersOfRegion,
    Set<String> ignoredRegionsLower,
    RegionPlayerHandler handler
  ) {
    if (CraftBookPlugin.plugins.getWorldGuard() == null)
      return;

    if (!notifyOwnersOfRegion && !notifyMembersOfRegion)
      return;

    var applicableRegions = WorldGuard.getInstance()
      .getPlatform()
      .getRegionContainer()
      .createQuery()
      .getApplicableRegions(BukkitAdapter.adapt(location));

    for (var region : applicableRegions) {
      if (ignoredRegionsLower.contains(region.getId().toLowerCase()))
        continue;

      var regionOwnerNames = getOwnerNames(region);

      String regionDetails;

      if (!regionOwnerNames.isEmpty())
        regionDetails = region.getId() + " (owned by " + String.join(", ", regionOwnerNames) + ")";
      else
        regionDetails = region.getId();

      if (notifyMembersOfRegion)
        forEachOnlineDomainPlayer(region.getMembers(), player -> handler.handle(player, regionDetails));

      if (notifyOwnersOfRegion)
        forEachOnlineDomainPlayer(region.getOwners(), player -> handler.handle(player, regionDetails));
    }
  }

  private static List<String> getOwnerNames(ProtectedRegion region) {
    var result = new ArrayList<String>();
    var namesLower = new HashSet<String>();

    var domain = region.getOwners();

    for (var playerName : domain.getPlayers()) {
      if (namesLower.add(playerName.toLowerCase()))
        result.add(playerName);
    }

    for (var playerId : domain.getUniqueIds()) {
      var playerName = Bukkit.getOfflinePlayer(playerId).getName();

      if (playerName == null)
        continue;

      if (namesLower.add(playerName.toLowerCase()))
        result.add(playerName);
    }

    return result;
  }

  private static void forEachOnlineDomainPlayer(DefaultDomain domain, Consumer<Player> handler) {
    Player player;

    for (var playerName : domain.getPlayers()) {
      if ((player = Bukkit.getPlayer(playerName)) != null)
        handler.accept(player);
    }

    for (var playerId : domain.getUniqueIds()) {
      if ((player = Bukkit.getPlayer(playerId)) != null)
        handler.accept(player);
    }
  }
}
