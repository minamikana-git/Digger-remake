package net.hotamachisubaru.digger

import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class ToolMoney(private val config: FileConfiguration, private val plugin: JavaPlugin) {
    var isToolMoneyEnabled: Boolean
        get() = config.getBoolean(USE_TOOL_MONEY_PATH, false)
        set(enabled) {
            config[USE_TOOL_MONEY_PATH] = enabled
            plugin.saveConfig()
        }

    fun getMoneyForTool(material: Material): Int {
        return config.getInt(TOOL_MONEY_PATH + material.name, DEFAULT_MONEY)
    }

    companion object {
        private const val TOOL_MONEY_PATH = "tool-money."
        private const val USE_TOOL_MONEY_PATH = "use-tool-money"
        private const val DEFAULT_MONEY = 50
    }
}
