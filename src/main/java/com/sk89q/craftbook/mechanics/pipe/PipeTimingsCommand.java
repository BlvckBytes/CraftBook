package com.sk89q.craftbook.mechanics.pipe;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.IntStream;

public class PipeTimingsCommand implements CommandExecutor, TabCompleter {

  // NOTE: This just represents a little internal analysis-tool for now, so I won't put too much effort into polishing it up.

  private record TimingEntry(World world, int x, int y, int z, double totalTime) {}

  private static final long RECORDING_DURATION_S = 30;
  private static final int PAGE_SIZE = 10;

  private final Map<UUID, Long2ObjectMap<MutableDouble>> totalTimeByCompactIdByWorldId;
  private final List<TimingEntry> sortedTimingEntries;

  private long recordingEndStamp = -1;

  public PipeTimingsCommand() {
    this.totalTimeByCompactIdByWorldId = new HashMap<>();
    this.sortedTimingEntries = new ArrayList<>();
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
    if (!(sender.hasPermission("craftbook.pipetimings"))) {
      sender.sendMessage("§c[PipeTimings] Dir fehlt die nötige Berechtigung, um diesen Befehl zu benutzen!");
      return true;
    }

    if (args.length == 0) {
      sender.sendMessage("§c[PipeTimings] Benutzung: /" + label + " <record, result>");
      return true;
    }

    switch (args[0].toLowerCase()) {
      case "record" -> {
        if (args.length != 1) {
          sender.sendMessage("§c[PipeTimings] Benutzung: /" + label + " record");
          return true;
        }

        if (recordingEndStamp >= 0) {
          var remainingSeconds = (recordingEndStamp - System.currentTimeMillis()) / 1000.0;
          sender.sendMessage("§c[PipeTimings] Es ist bereits eine Aufzeichnung im Gange! Verbleibende Zeit: §4" + String.format("%.2f", remainingSeconds) + "s§c.");
          return true;
        }

        totalTimeByCompactIdByWorldId.clear();
        sortedTimingEntries.clear();

        recordingEndStamp = System.currentTimeMillis() + RECORDING_DURATION_S * 1000;

        Bukkit.getScheduler().runTaskTimer(CraftBookPlugin.inst(), task -> {
          if (recordingEndStamp > System.currentTimeMillis())
            return;

          task.cancel();
          recordingEndStamp = -1;

          for (var worldBucketEntry : totalTimeByCompactIdByWorldId.entrySet()) {
            var world = Bukkit.getWorld(worldBucketEntry.getKey());

            if (world == null)
              throw new IllegalStateException("Could not locate world with ID " + worldBucketEntry.getKey());

            for (var chunkBucketEntry : worldBucketEntry.getValue().long2ObjectEntrySet()) {
              var compactId = chunkBucketEntry.getLongKey();
              var totalTime = chunkBucketEntry.getValue().value;

              sortedTimingEntries.add(new TimingEntry(
                world,
                CompactId.getXFromBlockXYZChunkId(compactId),
                CompactId.getYFromBlockXYZChunkId(compactId),
                CompactId.getZFromBlockXYZChunkId(compactId),
                totalTime
              ));
            }
          }

          totalTimeByCompactIdByWorldId.clear();
          sortedTimingEntries.sort((a, b) -> -Double.compare(a.totalTime, b.totalTime));

          broadcastToAuthorized("§a[PipeTimings] Aufzeichnung beendet! Nutze §2/" + label + " result§a, um Ergebnisse anzusehen.");
        }, 0, 0);

        broadcastToAuthorized("§a[PipeTimings] Aufzeichnung für §2" + RECORDING_DURATION_S + "s §agestartet!");
        return true;
      }

      case "result" -> {
        var page = 1;

        if (args.length == 2) {
          try {
            page = Integer.parseInt(args[1]);

            if (page <= 0)
              throw new IllegalStateException();
          } catch (Throwable e) {
            sender.sendMessage("§c[PipeTimings] Ungültige Seitenzahl: §4" + args[1] + "§c.");
          }
        }

        else if (args.length > 2) {
          sender.sendMessage("§c[PipeTimings] Benutzung: /" + label + " result [page]");
          return true;
        }

        var pageCount = (sortedTimingEntries.size() + PAGE_SIZE - 1) / PAGE_SIZE;

        if (pageCount == 0) {
          sender.sendMessage("§c[PipeTimings] Keine Ergebnisse vorhanden!");
          return true;
        }

        if (page > pageCount) {
          sender.sendMessage("§c[PipeTimings] Ungültige Seitenzahl §4" + page + "§c, da nur §4" + pageCount + " §cSeiten zur Verfügung stehen.");
          return true;
        }

        sender.sendMessage("§a[PipeTimings] Seite §2" + page + "§a:");

        var firstIndex = (page - 1) * PAGE_SIZE;

        for (var index = firstIndex; index < firstIndex + PAGE_SIZE; ++index) {
          if (sortedTimingEntries.size() <= index)
            break;

          sendEntryLine(sender, sortedTimingEntries.get(index));
        }
      }
    }

    return true;
  }

  @Override
  public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
    if (!(sender.hasPermission("craftbook.pipetimings")))
      return List.of();

    if (args.length == 1)
      return List.of("record", "result");

    if (args.length == 2 && args[1].equalsIgnoreCase("result") && !sortedTimingEntries.isEmpty()) {
      var pageCount = (sortedTimingEntries.size() + PAGE_SIZE - 1) / PAGE_SIZE;
      return IntStream.range(1, Math.max(pageCount, 15)).mapToObj(String::valueOf).toList();
    }

    return List.of();
  }

  public void timeExecutionOf(Block inputPistonBlock, Runnable runnable) {
    if (recordingEndStamp < 0) {
      runnable.run();
      return;
    }

    var nanosBefore = System.nanoTime();
    runnable.run();
    var nanosAfter = System.nanoTime();

    var compactId = CompactId.computeWorldlessBlockXYZChunkId(inputPistonBlock.getX(), inputPistonBlock.getY(), inputPistonBlock.getZ());
    var worldId = inputPistonBlock.getWorld().getUID();

    totalTimeByCompactIdByWorldId
      .computeIfAbsent(worldId, k -> new Long2ObjectOpenHashMap<>())
      .computeIfAbsent(compactId, k -> new MutableDouble())
      .value += (nanosAfter - nanosBefore) / 1000D / 1000D;
  }

  private void broadcastToAuthorized(String message) {
    for (var player : Bukkit.getOnlinePlayers()) {
      if (player.hasPermission("craftbook.pipetimings"))
        player.sendMessage(message);
    }
  }

  @SuppressWarnings("deprecation")
  private void sendEntryLine(CommandSender sender, TimingEntry entry) {
    var pos1String = entry.x + " " + entry.y + " " + entry.z;
    var pos2String = (entry.x + 16) + " " + (entry.y + 16) + " " + (entry.z + 16);

    var component = new TextComponent(TextComponent.fromLegacyText(
      "§7(" + entry.world.getName() + ") §a" + pos1String + " bis " + pos2String + " Gesamtzeit " + String.format("%.2f", entry.totalTime) + "ms"
    ));

    component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tppos " + pos1String + " " + entry.world.getName()));

    sender.spigot().sendMessage(component);
  }
}
