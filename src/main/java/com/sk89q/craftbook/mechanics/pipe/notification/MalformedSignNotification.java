package com.sk89q.craftbook.mechanics.pipe.notification;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.core.LanguageManager;
import net.md_5.bungee.api.ChatMessageType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class MalformedSignNotification extends PipeNotification {

  private final Location signLocation;
  private final String malformedToken;
  private final int lineNumber;

  public MalformedSignNotification(Location signLocation, String malformedToken, int lineNumber) {
    this.signLocation = signLocation;
    this.malformedToken = malformedToken;
    this.lineNumber = lineNumber;
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
  public String buildMessage(Player receiver, String inputPistonCoordinates) {
    var languageManager = CraftBookPlugin.inst().getLanguageManager();

    return ChatColor.RED + languageManager.getString("circuits.pipes.malformed-sign-token", LanguageManager.getPlayersLanguage(receiver))
      .replace("{coordinates}", inputPistonCoordinates)
      .replace("{sign_coordinates}", signLocation.getBlockX() + " " + signLocation.getBlockY() + " " + signLocation.getBlockZ())
      .replace("{line}", String.valueOf(lineNumber))
      .replace("{token}", malformedToken);
  }
}
