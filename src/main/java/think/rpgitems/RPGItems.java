package think.rpgitems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.librazy.nclangchecker.LangKey;
import think.rpgitems.data.Font;
import think.rpgitems.item.ItemManager;
import think.rpgitems.power.*;
import think.rpgitems.power.impl.BasePower;
import think.rpgitems.support.WGSupport;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RPGItems extends JavaPlugin {

    private static int version;
    private static int serial;
    private static String pluginMCVersion;
    private static String serverMCVersion;

    public static Logger logger;
    public static RPGItems plugin;
    List<Plugin> managedPlugins = new ArrayList<>();
    public I18n i18n;
    public Configuration cfg;

    @Override
    public void onLoad() {
        plugin = this;
        logger = this.getLogger();

        String versionDesc = getDescription().getVersion();
        Pattern serialPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)-mc([\\d.]+)");
        Matcher serialMatcher = serialPattern.matcher(versionDesc);

        if (serialMatcher.matches()) {
            version = Integer.parseInt(serialMatcher.group(1)) * 100 + Integer.parseInt(serialMatcher.group(2));
            serial = Integer.parseInt(serialMatcher.group(3));
            pluginMCVersion = serialMatcher.group(4);
        }

        String serverVersion = Bukkit.getVersion();
        Pattern mcVersionPattern = Pattern.compile("\\(MC:\\s+([\\d.]+)\\)");
        Matcher mcVersionMatcher = mcVersionPattern.matcher(serverVersion);

        if (mcVersionMatcher.find()) {
            serverMCVersion = mcVersionMatcher.group(1);
        }

        logger.log(Level.INFO, "Plugin serial: '" + serial + "', native version: '" + pluginMCVersion + "', server version: '" + serverMCVersion + "'.");

        cfg = new Configuration(this);
        cfg.load();
        i18n = new I18n(this, cfg.language);

        PowerManager.registerAdapter(PowerPlain.class, PowerOffhandClick.class, p -> getWrapper(p, PowerOffhandClick.class, "offhandClick"));
        PowerManager.registerAdapter(PowerPlain.class, PowerSprint.class, p -> getWrapper(p, PowerSprint.class, "sprint"));
        PowerManager.registerAdapter(PowerPlain.class, PowerSneak.class, p -> getWrapper(p, PowerSneak.class, "sneak"));
        PowerManager.registerAdapter(PowerPlain.class, PowerAttachment.class, p -> getWrapper(p, PowerAttachment.class, "attachment"));

        PowerManager.addDescriptionResolver(RPGItems.plugin, (power, property) -> {
            if (property == null) {
                @LangKey(skipCheck = true) String powerKey = "power.properties." + power.getKey() + ".main_description";
                return I18n.format(powerKey);
            }
            @LangKey(skipCheck = true) String key = "power.properties." + power.getKey() + "." + property;
            if (I18n.getInstance().hasKey(key)) {
                return I18n.format(key);
            }
            @LangKey(skipCheck = true) String baseKey = "power.properties.base." + property;
            if (I18n.getInstance().hasKey(baseKey)) {
                return I18n.format(baseKey);
            }
            return null;
        });
        PowerManager.registerPowers(RPGItems.plugin, BasePower.class.getPackage().getName());
        saveDefaultConfig();
        Font.load();
        WGSupport.load();
        loadExtensions();
    }

    public void loadExtensions() {
        File extDir = new File(plugin.getDataFolder(), "ext");
        if (extDir.isDirectory() || extDir.mkdirs()) {
            File[] files = extDir.listFiles((d, n) -> n.endsWith(".jar"));
            if (files == null) return;
            for (File file : files) {
                try {
                    Plugin plugin = Bukkit.getPluginManager().loadPlugin(file);
                    String message = String.format("Loading %s", plugin.getDescription().getFullName());
                    plugin.getLogger().info(message);
                    plugin.onLoad();
                    managedPlugins.add(plugin);
                    logger.info("Loaded extension: " + plugin.getName());
                } catch (InvalidPluginException | InvalidDescriptionException e) {
                    logger.log(Level.SEVERE, "Error loading extension: " + file.getName(), e);
                }
            }
        } else {
            logger.severe("Error creating extension directory ./ext");
        }
    }

    @Override
    public void onEnable() {
        Trigger.stopAcceptingRegistrations();
        plugin = this;
        if (plugin.cfg.version.startsWith("0.") && Double.parseDouble(plugin.cfg.version) < 0.5) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "======================================");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "You current version of RPGItems config is not supported.");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "Please run your server with latest version of RPGItems 3.5 before update.");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "======================================");
            throw new IllegalStateException();
        } else if (plugin.cfg.version.equals("0.5")) {
            cfg.pidCompat = true;
        }

        if (Bukkit.class.getPackage().getImplementationVersion().startsWith("git-Bukkit-")) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "======================================");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "RPGItems plugin requires Spigot API, Please make sure you are using Spigot.");
            Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "======================================");
        }
        try {
            Bukkit.spigot();
        } catch (NoSuchMethodError e) {
            getCommand("rpgitem").setExecutor((sender, command, label, args) -> {
                sender.sendMessage(ChatColor.RED + "======================================");
                sender.sendMessage(ChatColor.RED + "RPGItems plugin requires Spigot API, Please make sure you are using Spigot.");
                sender.sendMessage(ChatColor.RED + "======================================");
                return true;
            });
        }
        AdminHandler adminCommandHandler = new AdminHandler(this, i18n);
        UserHandler userCommandHandler = new UserHandler(this, i18n);
        getCommand("rpgitem").setExecutor(adminCommandHandler);
        getCommand("rpgitem").setTabCompleter(adminCommandHandler);
        getCommand("rpgitems").setExecutor(userCommandHandler);
        getCommand("rpgitems").setTabCompleter(userCommandHandler);
        getServer().getPluginManager().registerEvents(new ServerLoadListener(), this);
        managedPlugins.forEach(Bukkit.getPluginManager()::enablePlugin);
    }

    public static int getVersion() {
        return version;
    }

    public static int getSerial() {
        return serial;
    }

    public static String getPluginMCVersion() {
        return pluginMCVersion;
    }

    public static String getServerMCVersion() {
        return serverMCVersion;
    }

    private class ServerLoadListener implements Listener {
        @EventHandler
        public void onServerLoad(ServerLoadEvent event) {
            HandlerList.unregisterAll(this);
            getServer().getPluginManager().registerEvents(new Events(), RPGItems.this);
            WGSupport.init(RPGItems.this);
            logger.info("Loading RPGItems...");
            ItemManager.load(RPGItems.this);
            logger.info("Done");
            new Ticker().runTaskTimer(RPGItems.this, 0, 0);
        }
    }

    @Override
    public void onDisable() {
        WGSupport.unload();
        HandlerList.unregisterAll(plugin);
        getCommand("rpgitem").setExecutor(null);
        getCommand("rpgitem").setTabCompleter(null);
        this.getServer().getScheduler().cancelTasks(plugin);
        ItemManager.unload();
        managedPlugins.forEach(Bukkit.getPluginManager()::disablePlugin);
    }

    @SuppressWarnings({"unchecked", "JavaReflectionInvocation"})
    private static <T> T getWrapper(final PowerPlain obj, final Class<T> implInterface, final String delegateMethod) {
        InvocationHandler invocationHandler = (proxy, method, args) -> {
            if (!method.getName().equals(delegateMethod)) {
                return obj.getClass().getMethod(method.getName(), method.getParameterTypes()).invoke(obj, args);
            } else {
                return obj.getClass().getDeclaredMethod("fire", Player.class, ItemStack.class).invoke(obj, args[0], args[1]);
            }
        };
        return (T) Proxy.newProxyInstance(obj.getClass().getClassLoader(), new Class[]{implInterface}, invocationHandler);
    }
}
