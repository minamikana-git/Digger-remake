package net.hotamachisubaru.digger;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ToolMoney {
    private final FileConfiguration config;
    private final JavaPlugin plugin;

    public ToolMoney(FileConfiguration config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    public boolean isToolMoneyEnabled() {
        return config.getBoolean("use-tool-money", false);
    }

    public int getMoneyForTool(Material material) {
        return config.getInt("tool-money." + material.name(), 50);
    }

    public void setToolMoneyEnabled(boolean enabled) {
        config.set("use-tool-money", enabled);
        plugin.saveConfig();  // 変更を保存する
    }
}