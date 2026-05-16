package com.sk89q.craftbook.bukkit.commands;

import com.sk89q.craftbook.util.ItemSyntax;
import com.sk89q.minecraft.util.commands.CommandException;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.CraftBookBukkitUtil;
import com.sk89q.craftbook.mechanics.ic.ICCommands;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandPermissions;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.minecraft.util.commands.NestedCommand;
import org.bukkit.entity.Player;

public class TopLevelCommands {

    public TopLevelCommands(CraftBookPlugin plugin) {

    }

    @Command(aliases = {"craftbook", "cb"}, desc = "CraftBook Plugin commands")
    @NestedCommand(Commands.class)
    public void craftBookCmds(CommandContext context, CommandSender sender) {

    }

    @Command(aliases = {"ic", "circuit"}, desc = "Commands to manage Craftbook IC's")
    @NestedCommand(ICCommands.class)
    public void icCmd(CommandContext context, CommandSender sender) {
    }

    public static class Commands {

        public Commands(CraftBookPlugin plugin) {

        }

        @Command(aliases = "reload", desc = "Reloads the CraftBook Common config")
        @CommandPermissions("craftbook.reload")
        public void reload(CommandContext context, CommandSender sender) {

            try {
                CraftBookPlugin.inst().reloadConfiguration();
            } catch (Throwable e) {
                CraftBookBukkitUtil.printStacktrace(e);
                sender.sendMessage("An error occured while reloading the CraftBook config.");
                return;
            }
            sender.sendMessage("The CraftBook config has been reloaded.");
        }

        @Command(aliases = "about", desc = "Gives info about craftbook.")
        public void about(CommandContext context, CommandSender sender) {

            String ver = CraftBookPlugin.inst().getDescription().getVersion();
            if(CraftBookPlugin.getVersion() != null) {
                ver = CraftBookPlugin.getVersion();
            }
            sender.sendMessage(ChatColor.YELLOW + "CraftBook version " + ver);
            sender.sendMessage(ChatColor.YELLOW + "Founded by sk89q, and currently developed by Me4502 & Dark_Arc");
        }

        @Command(aliases = {"iteminfo", "itemsyntax"}, desc = "Provides item syntax for held item.")
        public void itemInfo(CommandContext context, CommandSender sender) throws CommandException {

            if(!(sender instanceof Player)) {
                throw new CommandException("Only players can use this command!");
            }
            if (((Player) sender).getInventory().getItemInMainHand() != null) {
                sender.sendMessage(ChatColor.YELLOW + "Main hand: " + ItemSyntax.getStringFromItem(((Player) sender).getInventory().getItemInMainHand()));
            }
            if (((Player) sender).getInventory().getItemInOffHand() != null) {
                sender.sendMessage(ChatColor.YELLOW + "Off hand: " + ItemSyntax.getStringFromItem(((Player) sender).getInventory().getItemInOffHand()));
            }
        }

        @Command(aliases = {"cbid", "craftbookid"}, desc = "Gets the players CBID.")
        public void cbid(CommandContext context, CommandSender sender) throws CommandException {
            if(!(sender instanceof Player)) {
                throw new CommandException("Only players can use this command!");
            }
            sender.sendMessage("CraftBook ID: " + CraftBookPlugin.inst().wrapPlayer((Player) sender).getCraftBookId());
        }

        @Command(aliases = {"enable"}, desc = "Enable a mechanic")
        @CommandPermissions({"craftbook.enable-mechanic"})
        public void enable(CommandContext args, final CommandSender sender) throws CommandPermissionsException {

            if(args.argsLength() > 0) {
                if(CraftBookPlugin.inst().enableMechanic(args.getString(0)))
                    sender.sendMessage(ChatColor.YELLOW + "Sucessfully enabled " + args.getString(0));
                else
                    sender.sendMessage(ChatColor.RED + "Failed to load " + args.getString(0));
            }
        }

        @Command(aliases = {"disable"}, desc = "Disable a mechanic")
        @CommandPermissions({"craftbook.disable-mechanic"})
        public void disable(CommandContext args, final CommandSender sender) throws CommandPermissionsException {

            if(args.argsLength() > 0) {
                if(CraftBookPlugin.inst().disableMechanic(args.getString(0)))
                    sender.sendMessage(ChatColor.YELLOW + "Sucessfully disabled " + args.getString(0));
                else
                    sender.sendMessage(ChatColor.RED + "Failed to remove " + args.getString(0));
            }
        }
    }
}
