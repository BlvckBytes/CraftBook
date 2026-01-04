package com.sk89q.craftbook.mechanics.pipe.notification;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NotificationDebouncer {

  private final Map<UUID, Map<String, Long>> lastNotificationSendByDebounceIdByPlayerId;
  private long debounceMillis;

  public NotificationDebouncer() {
    this.lastNotificationSendByDebounceIdByPlayerId = new HashMap<>();
  }

  public void setDebounceMillis(long debounceMillis) {
    this.debounceMillis = debounceMillis;
  }

  public boolean isSwallowedByDebounce(Player player, PipeNotification notification, Block inputPistonBlock) {
    var debounceId = notification.makeDebounceId(inputPistonBlock);

    if (debounceId == null)
      return false;

    var playerBucket = lastNotificationSendByDebounceIdByPlayerId.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

    var lastSend = playerBucket.get(debounceId);
    var isSwallowed = lastSend != null && System.currentTimeMillis() - lastSend < debounceMillis;

    if (isSwallowed)
      return true;

    playerBucket.put(debounceId, System.currentTimeMillis());
    return false;
  }

  public void removePlayer(Player player) {
    this.lastNotificationSendByDebounceIdByPlayerId.remove(player.getUniqueId());
  }
}
