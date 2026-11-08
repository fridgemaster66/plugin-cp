package dev.cp;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CPPlugin extends JavaPlugin {

    private ResourceManager resources;
    private PackGenerator generator;
    private Lang lang;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lang = new Lang(getConfig().getString("language", "pl"));
        resources = new ResourceManager(this);
        generator = new PackGenerator(this, resources);
        BlockStates.init();
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new FoodListener(this), this);

        if (getConfig().getBoolean("settings.auto-create-folders", true)) {
            resources.ensureFolders();
        }

        PluginCommand cmd = getCommand("cp");
        if (cmd == null) {
            getLogger().severe("Brak komendy /cp w plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        CPCommand handler = new CPCommand(this);
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
        getLogger().info("CP wlaczony.");
    }

    public ResourceManager resources() {
        return resources;
    }

    public PackGenerator generator() {
        return generator;
    }

    public Lang lang() {
        return lang;
    }

    public void setLanguage(String code) {
        String c = code.toLowerCase();
        getConfig().set("language", c);
        saveConfig();
        lang = new Lang(c);
    }
}
