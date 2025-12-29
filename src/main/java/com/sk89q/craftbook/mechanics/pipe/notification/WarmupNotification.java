package com.sk89q.craftbook.mechanics.pipe.notification;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.core.LanguageManager;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class WarmupNotification extends PipeNotification {

  private final int pistonBlockCount;
  private final int tubeBlockCount;

  public WarmupNotification(int pistonBlockCount, int tubeBlockCount) {
    this.pistonBlockCount = pistonBlockCount;
    this.tubeBlockCount = tubeBlockCount;
  }

  @Override
  public boolean broadcastToRegion() {
    // There's no need to broadcast warmup-notifications this far - they're only meant as a status-update
    // for close-by players who are patently waiting on their items to move through the pipe.
    return false;
  }

  @Override
  public ChatMessageType getMessageType() {
    // Send warmup-messages to the action-bar, seeing how they would otherwise completely spam the chat.
    return ChatMessageType.ACTION_BAR;
  }

  @Override
  public String buildMessage(Player receiver, String inputPistonCoordinates) {
    var languageManager = CraftBookPlugin.inst().getLanguageManager();

    return ChatColor.GOLD + languageManager.getString("circuits.pipes.warmup-notification", LanguageManager.getPlayersLanguage(receiver))
      .replace("{coordinates}", inputPistonCoordinates)
      .replace("{tubes}", String.valueOf(tubeBlockCount))
      .replace("{pistons}", String.valueOf(pistonBlockCount));
  }
}
