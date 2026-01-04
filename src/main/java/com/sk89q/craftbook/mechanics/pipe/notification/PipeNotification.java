package com.sk89q.craftbook.mechanics.pipe.notification;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

public abstract class PipeNotification {

  private final Set<UUID> receiverIds = new HashSet<>();

  public abstract boolean broadcastToRegion();

  public abstract ChatMessageType getMessageType();

  public abstract String buildMessage(Player receiver, String extendedCoordinates);

  /**
   * @return The tokens which identify the information conveyed to the user; this is used
   *         to debounce notifications which would needlessly spam the chat. Return null
   *         to entirely deactivate debouncing this notification; return an empty array if
   *         there's no additional information, but the type of notification should still
   *         be debounced.
   */
  public abstract @Nullable Object[] getDataTokens();

  public @Nullable String makeDebounceId(Block inputPistonBlock) {
    var dataTokens = getDataTokens();

    if (dataTokens == null)
      return null;

    var result = new StringJoiner("_");

    var inputPistonTokens = new Object[] {
      inputPistonBlock.getWorld().getName(),
      inputPistonBlock.getX(), inputPistonBlock.getY(), inputPistonBlock.getZ()
    };

    for (var inputPistonToken : inputPistonTokens)
      result.add(String.valueOf(inputPistonToken));

    result.add(getClass().getSimpleName());

    for (var dataToken : getDataTokens())
      result.add(String.valueOf(dataToken));

    return result.toString();
  }

  public void sendOnceIfNotDebounced(Player receiver, Block inputPistonBlock, String extendedCoordinates, NotificationDebouncer debouncer) {
    if (!receiverIds.add(receiver.getUniqueId()))
      return;

    if (debouncer.isSwallowedByDebounce(receiver, this, inputPistonBlock))
      return;

    var message = buildMessage(receiver, extendedCoordinates);
    var messageType = getMessageType();

    if (messageType == ChatMessageType.CHAT) {
      receiver.sendMessage(message);
      return;
    }

    receiver.spigot().sendMessage(messageType, new TextComponent(message));
  }
}
