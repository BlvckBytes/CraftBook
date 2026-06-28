package com.sk89q.craftbook.bukkit;

import com.google.common.collect.Sets;
import com.sk89q.craftbook.CraftBookMechanic;
import com.sk89q.craftbook.CraftBookPlayer;
import com.sk89q.craftbook.core.LanguageManager;
import com.sk89q.craftbook.mechanics.Elevator;
import com.sk89q.craftbook.mechanics.Teleporter;
import com.sk89q.craftbook.mechanics.pipe.Pipes;
import com.sk89q.craftbook.util.compat.companion.CompanionPlugins;
import com.sk89q.util.yaml.YAMLFormat;
import com.sk89q.util.yaml.YAMLProcessor;
import com.sk89q.wepif.PermissionsResolverManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;

public class CraftBookPlugin extends JavaPlugin {

    /**
     * Companion Plugins for CraftBook.
     */
    public static CompanionPlugins plugins;

    /**
     * The instance for CraftBook
     */
    private static CraftBookPlugin instance;

    /**
     * The language manager
     */
    private LanguageManager languageManager;

    /**
     * Handles all configuration.
     */
    private BukkitConfiguration config;

    /**
     * The adapter for events to the manager.
     */
    private MechanicListenerAdapter managerAdapter;

    /**
     * List of common mechanics.
     */
    private List<CraftBookMechanic> mechanics;

    public static final Map<String, Class<? extends CraftBookMechanic>> availableMechanics;

    static {
        availableMechanics = new TreeMap<>();

        availableMechanics.put("Elevator", Elevator.class);
        availableMechanics.put("Teleporter", Teleporter.class);
        availableMechanics.put("Pipes", Pipes.class);
    }

    /**
     * Construct objects. Actual loading occurs when the plugin is enabled, so
     * this merely instantiates the objects.
     */
    public CraftBookPlugin() {

        super();
        // Set the instance
        instance = this;
    }

    @Nullable
    public static String getVersion() {
        return null;
    }

    public List<CraftBookMechanic> getMechanics() {

        return mechanics;
    }

    /**
     * Called on plugin enable.
     */
    @Override
    public void onEnable() {
        plugins = new CompanionPlugins();
        plugins.initiate(this);

        // Need to create the plugins/CraftBook folder
        getDataFolder().mkdirs();

        // Setup Config
        createDefaultConfiguration(new File(getDataFolder(), "config.yml"), "config.yml");
        config = new BukkitConfiguration(new YAMLProcessor(new File(getDataFolder(), "config.yml"), true, YAMLFormat.EXTENDED), logger());
        // Load the configuration
        try {
            config.load();
        } catch (Throwable e) {
            getLogger().severe("Failed to load CraftBook Configuration File! Is it corrupt?");
            getLogger().severe(getStackTrace(e));
            getLogger().severe("Disabling CraftBook due to invalid Configuration File!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        managerAdapter = new MechanicListenerAdapter();

        PermissionsResolverManager.initialize(this);

        // Let's start the show
        setupCraftBook();
        registerGlobalEvents();

        getServer().getPluginManager().registerEvents(new Listener() {

            /* Bukkit Bug Fixes */

            @EventHandler(priority = EventPriority.LOWEST)
            public void signChange(SignChangeEvent event) {
                for(int i = 0; i < event.getLines().length; i++) {
                    StringBuilder builder = new StringBuilder();
                    for (char c : event.getLine(i).toCharArray()) {
                        if (c < 0xF700 || c > 0xF747) {
                            builder.append(c);
                        }
                    }
                    String fixed = builder.toString();
                    if(!fixed.equals(event.getLine(i)))
                        event.setLine(i, fixed);
                }
            }

            /* Alerts */

            @EventHandler(priority = EventPriority.HIGH)
            public void playerJoin(PlayerJoinEvent event) {

                if(!event.getPlayer().isOp()) return;

                if(getMechanics().isEmpty()) {
                    event.getPlayer().sendMessage(ChatColor.RED + "[CraftBook] Warning! You have no mechanics enabled, the plugin will appear to do nothing until a feature is enabled!");
                }
            }
        }, this);

        if(getMechanics().isEmpty()) {
            Bukkit.getScheduler().runTaskTimer(this,
                    () -> getLogger().warning(ChatColor.RED + "Warning! You have no mechanics enabled, the plugin will appear to do nothing until a feature is enabled!"), 20L, 20*60*5);
        }
    }

  /**
     * Register basic things to the plugin. For example, languages.
     */
    public void setupCraftBook() {
        // Initialize the language manager.
        languageManager = new LanguageManager();
        languageManager.init();

        mechanics = new ArrayList<>();

        createDefaultConfiguration(new File(getDataFolder(), "mechanisms.yml"), "mechanisms.yml");
        var mechanismsConfig = new YAMLProcessor(new File(getDataFolder(), "mechanisms.yml"), true, YAMLFormat.EXTENDED);

        try {
            mechanismsConfig.load();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mechanismsConfig.setWriteDefaults(true);

        mechanismsConfig.setHeader(
                "# CraftBook Mechanism Configuration. Generated for version: " + (CraftBookPlugin.inst() == null ? CraftBookPlugin.getVersion() : CraftBookPlugin.inst().getDescription().getVersion()),
                "# This configuration will automatically add new configuration options for you,",
                "# So there is no need to regenerate this configuration unless you need to.",
                "# More information about these features are available at...",
                "# " + CraftBookPlugin.getWikiDomain() + "/mechanics/",
                "#",
                "# NOTE! MAKE SURE TO ENABLE FEATURES IN THE config.yml FILE!",
                "");

        for(String enabled : Sets.newHashSet(config.enabledMechanics)) {

            Class<? extends CraftBookMechanic> mechClass = availableMechanics.get(enabled);
            try {
                if(mechClass != null) {

                    CraftBookMechanic mech = mechClass.newInstance();
                    mech.loadConfiguration(mechanismsConfig, "mechanics." + enabled + '.');
                    mechanics.add(mech);
                }
            } catch (Throwable t) {
                getLogger().log(Level.WARNING, "Failed to load mechanic: " + enabled, t);
            }
        }

        mechanismsConfig.save();

        Iterator<CraftBookMechanic> iter = mechanics.iterator();
        while(iter.hasNext()) {
            CraftBookMechanic mech = iter.next();
            try {
                if(!mech.enable()) {
                    getLogger().warning("Failed to enable mechanic: " + mech.getClass().getSimpleName());
                    mech.disable();
                    iter.remove();
                    continue;
                }
                getServer().getPluginManager().registerEvents(mech, this);
            } catch(Throwable t) {
                getLogger().log(Level.WARNING, "Failed to enable mechanic: " + mech.getClass().getSimpleName(), t);
            }
        }
    }

    public void registerGlobalEvents() {
        getServer().getPluginManager().registerEvents(managerAdapter, inst());
    }

    /**
     * Called on plugin disable.
     */
    @Override
    public void onDisable() {
        if(mechanics != null) {
            for(CraftBookMechanic mech : mechanics)
                mech.disable();
            mechanics = null;
        }
    }

    /**
     * This retrieves the CraftBookPlugin instance for static access.
     *
     * @return Returns a CraftBookPlugin
     */
    public static CraftBookPlugin inst() {

        return instance;
    }

    public CraftBookMechanic getMechanic(Class<? extends CraftBookMechanic> clazz) {

        for(CraftBookMechanic mech : mechanics) {
            if(mech.getClass().equals(clazz))
                return mech;
        }

        return null;
    }

    /**
     * This retrieves the CraftBookPlugin logger.
     *
     * @return Returns the CraftBookPlugin {@link Logger}
     */
    public static Logger logger() {

        return inst().getLogger();
    }

    /**
     * Get the global ConfigurationManager.
     * Use this to access global configuration values and per-world configuration values.
     *
     * @return The global ConfigurationManager
     */
    public BukkitConfiguration getConfiguration() {

        return config;
    }

    /**
     * This method is used to get the CraftBook {@link LanguageManager}.
     *
     * @return The CraftBook {@link LanguageManager}
     */
    public LanguageManager getLanguageManager() {

        return languageManager;
    }

    public boolean hasPermission(Permissible permissible, String perm) {

        if (permissible.isOp()) {
            if (permissible instanceof Player) {

                if (!config.noOpPermissions) return true;
            } else {
                return true;
            }
        }

        // Invoke the permissions resolver
        if (permissible instanceof Player player) {
          return PermissionsResolverManager.getInstance().hasPermission(player.getWorld().getName(), player, perm);
        }

        return false;
    }

    /**
     * Wrap a player as a CraftBookPlayer.
     *
     * @param player The player to wrap
     *
     * @return The wrapped player
     */
    public CraftBookPlayer wrapPlayer(Player player) {

        return new BukkitCraftBookPlayer(this, player);
    }

    /**
     * Create a default configuration file from the .jar.
     *
     * @param actual      The destination file
     * @param defaultName The name of the file inside the jar's defaults folder
     */
    public void createDefaultConfiguration(File actual, String defaultName) {

        // Make parent directories
        File parent = actual.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        if (actual.exists()) {
            return;
        }

        InputStream input = null;
        JarFile file = null;
        try {
            file = new JarFile(getFile());
            ZipEntry copy = file.getEntry("defaults/" + defaultName);
            if (copy == null) {
                file.close();
                throw new FileNotFoundException();
            }
            input = file.getInputStream(copy);
        } catch (IOException e) {
            getLogger().severe("Unable to read default configuration: " + defaultName);
        }

        if (input != null) {
            FileOutputStream output = null;

            try {
                output = new FileOutputStream(actual);
                byte[] buf = new byte[8192];
                int length = 0;
                while ((length = input.read(buf)) > 0) {
                    output.write(buf, 0, length);
                }

                getLogger().info("Default configuration file written: " + actual.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            } finally {

                try {
                    file.close();
                } catch (IOException ignored) {
                }

                try {
                    input.close();
                } catch (IOException ignore) {
                }

                try {
                    if (output != null) {
                        output.close();
                    }
                } catch (IOException ignore) {
                }
            }
        } else if (file != null)
            try {
                file.close();
            } catch (IOException ignored) {
            }
    }

    @Override
    public File getFile() {

        return super.getFile();
    }

    public static String getStackTrace(Throwable ex) {

        Writer out = new StringWriter();
        PrintWriter pw = new PrintWriter(out);
        ex.printStackTrace(pw);
        return out.toString();
    }

    public static String getWikiDomain() {
        return "https://craftbook.enginehub.org/en/3.x";
    }
}