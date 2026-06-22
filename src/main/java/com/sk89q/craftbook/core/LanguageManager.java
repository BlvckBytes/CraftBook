package com.sk89q.craftbook.core;

import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.util.yaml.YAMLFormat;
import com.sk89q.util.yaml.YAMLProcessor;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Me4502
 */
public class LanguageManager {

    private final Map<String, YAMLProcessor> languageMap = new HashMap<>();

    public void init() {
        checkForLanguages();
    }

    private void checkForLanguages() {

        for (String language : CraftBookPlugin.inst().getConfiguration().languages) {
            language = language.trim();
            File f = new File(CraftBookPlugin.inst().getDataFolder(), language + ".yml");
            if(!f.exists())
                try {
                    f.createNewFile();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            YAMLProcessor lang = new YAMLProcessor(f, true, YAMLFormat.EXTENDED);

            try {
                lang.load();
            } catch (Throwable e) {
                CraftBookPlugin.inst().getLogger().severe("An error occured loading the languages file for: " + language + "! This language WILL NOT WORK UNTIL FIXED!");
                e.printStackTrace();
                continue;
            }

            lang.setWriteDefaults(true);

            for(Entry<String, String> s : defaultMessages.entrySet())
                lang.getString(s.getKey(), s.getValue());

            lang.save();

            languageMap.put(language.toLowerCase(), lang);
        }
    }

    public String getString(String message, String language) {

        //message = ChatColor.stripColor(message);
        if(language == null || !languageMap.containsKey(language.toLowerCase()))
            language = CraftBookPlugin.inst().getConfiguration().language;
        YAMLProcessor languageData = languageMap.get(language.toLowerCase());
        String def = defaultMessages.get(message);
        if(languageData == null) {
          return def == null ? message : def;
        } else {
            String translated;
            if(def == null || languageData.getString(message) != null)
                translated = languageData.getString(message);
            else {
                translated = languageData.getString(message, def);
            }

          if (translated != null)
            return translated;
          else
            return def == null ? message : def;
        }
    }

    public static String getPlayersLanguage(Player p) {
        return p.getLocale();
    }

    public static final HashMap<String, String> defaultMessages = new HashMap<>(32, 1.0f) {{
        put("area.permissions", "You don't have permissions to do that in this area!");
        put("area.use-permissions", "You don't have permissions to use that in this area!");
        put("area.break-permissions", "You don't have permissions to break that in this area!");

        put("mech.create-permission", "You don't have permission to create this mechanic.");
        put("mech.use-permission", "You don't have permission to use this mechanic.");
        put("mech.restock-permission", "You don't have permission to restock this mechanic.");
        put("mech.not-enough-blocks","Not enough blocks to trigger mechanic!");
        put("mech.group","You are not in the required group!");
        put("mech.restock","Mechanism Restocked!");

        put("mech.anchor.create","Chunk Anchor Created!");
        put("mech.anchor.already-anchored","This chunk is already anchored!");

        put("mech.cook.create","Cooking Pot Created!");
        put("mech.cook.ouch","Ouch! That was hot!");
        put("mech.cook.add-fuel","You put fuel into the cooking pot, and watch as the fire roars!");

        put("mech.ic.create","You've created");

        put("mech.lift.target-sign-created","Elevator target sign created.");
        put("mech.lift.down-sign-created","Elevator down sign created.");
        put("mech.lift.up-sign-created","Elevator up sign created.");
        put("mech.lift.obstruct","Your destination is obstructed!");
        put("mech.lift.no-floor","There is no floor at your destination!");
        put("mech.lift.floor","Floor");
        put("mech.lift.up","You went up a floor!");
        put("mech.lift.down","You went down a floor!");
        put("mech.lift.leave", "You have left the elevator!");
        put("mech.lift.no-destination", "This lift has no destination.");
        put("mech.lift.no-depart", "Cannot depart from this lift (can only arrive).");
        put("mech.lift.busy", "Elevator Busy!");

        put("mech.map.create","Map Changer Created!");
        put("mech.map.invalid","Invalid Map ID!");

        put("mech.pistons.crush.created","Piston Crush Mechanic Created!");
        put("mech.pistons.supersticky.created","Piston Super-Sticky Mechanic Created!");
        put("mech.pistons.bounce.created","Piston Bounce Mechanic Created!");
        put("mech.pistons.superpush.created","Piston Super-Push Mechanic Created!");

        put("mech.teleport.create","Teleporter Created!");
        put("mech.teleport.alert","You Teleported!");
        put("mech.teleport.range","Out of Range!");
        put("mech.teleport.sign","There is no Sign at your Destination!");
        put("mech.teleport.arriveonly","You can only arrive at this teleporter!");
        put("mech.teleport.invalidcoords", "The entered coordinates are invalid!");
        put("mech.teleport.obstruct","Your destination is obstructed!");

        put("mech.xp-storer.create", "XP Storer Created!");
        put("mech.xp-storer.bottle", "You need a bottle to perform this mechanic!");
        put("mech.xp-storer.success", "You package your experience into a bottle!");
        put("mech.xp-storer.not-enough-xp", "You do not have enough experience to fill a bottle!");

        put("circuits.pipes.create","Pipe created!");
        put("circuits.pipes.pipe-not-found", "Failed to find pipe!");
        put("circuits.pipes.warmup-notification", "[Pipe] Warming up... {tubes}T {pistons}P");
        put("circuits.pipes.exceeded-tube-count-notification", "[Pipe] Exceeded the tube-block limit of {limit} at {coordinates}; dropping item at input!");
        put("circuits.pipes.exceeded-piston-count-notification", "[Pipe] Exceeded the piston-block limit of {limit} at {coordinates}; dropping item at input!");
        put("circuits.pipes.no-sign-encountered", "[Pipe] Could not locate a valid sign anywhere on the pipe at {coordinates}; dropping item at input!");
        put("circuits.pipes.malformed-sign-token", "[Pipe] The token \"{token}\" on line {line} on the sign at {sign_coordinates} on the pipe at {coordinates} is invalid!");

        put("vehicles.create-permission","You don't have permissions to create this vehicle mechanic!");
    }};
}