package com.sk89q.craftbook.mechanics.pipe.notification;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public abstract class PipeNotification {

  private final Set<UUID> receiverIds = new HashSet<>();

  public abstract boolean broadcastToRegion();

  public abstract ChatMessageType getMessageType();

  public abstract String buildMessage(Player receiver, String inputPistonCoordinates);

  public void sendOnce(Player receiver, String inputPistonCoordinates) {
    if (!receiverIds.add(receiver.getUniqueId()))
      return;

    var message = buildMessage(receiver, inputPistonCoordinates);
    var messageType = getMessageType();

    if (messageType == ChatMessageType.CHAT) {
      receiver.sendMessage(message);
      return;
    }

    receiver.spigot().sendMessage(messageType, new TextComponent(message));
  }
}
