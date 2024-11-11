package net.hotamachisubaru.digger;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ToolMoney {
    private static final String TOOL_MONEY_PATH = "tool-money.";
    private static final String USE_TOOL_MONEY_PATH = "use-tool-money";
    private static final int DEFAULT_MONEY = 50;

    private final FileConfiguration config;
    private final JavaPlugin plugin;

    public ToolMoney(FileConfiguration config, JavaPlugin plugin) {
        this.config = config;
        this.plugin = plugin;
    }

    public boolean isToolMoneyEnabled() {
        return config.getBoolean(USE_TOOL_MONEY_PATH, false);
    }

    public int getMoneyForTool(Material material) {
        return config.getInt(TOOL_MONEY_PATH + material.name(), DEFAULT_MONEY);
    }

    public void setToolMoneyEnabled(boolean enabled) {
        config.set(USE_TOOL_MONEY_PATH, enabled);
        plugin.saveConfig();
    }
}
