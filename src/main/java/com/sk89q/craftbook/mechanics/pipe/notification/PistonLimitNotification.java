package com.sk89q.craftbook.mechanics.pipe.notification;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.core.LanguageManager;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class PistonLimitNotification extends PipeNotification {

  private final int pistonLimit;

  public PistonLimitNotification(int pistonLimit) {
    this.pistonLimit = pistonLimit;
  }

  @Override
  public boolean broadcastToRegion() {
    return true;
  }

  @Override
  public ChatMessageType getMessageType() {
    return ChatMessageType.CHAT;
  }

  @Override
  public String buildMessage(Player receiver, String extendedCoordinates) {
    var languageManager = CraftBookPlugin.inst().getLanguageManager();

    return ChatColor.RED + languageManager.getString("circuits.pipes.exceeded-piston-count-notification", LanguageManager.getPlayersLanguage(receiver))
      .replace("{coordinates}", extendedCoordinates)
      .replace("{limit}", String.valueOf(pistonLimit));
  }

  @Override
  public @Nullable Object[] getDataTokens() {
    return new Object[0];
  }
}
