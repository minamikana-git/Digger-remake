package net.hotamachisubaru.digger

import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

// ToolMoney クラスは、ツールに応じてお金の量を設定するためのクラス
class ToolMoney(private val config: FileConfiguration, private val plugin: JavaPlugin) {

    // プラグインの設定から「ツールでお金を使用する」機能が有効かどうかを取得または設定するプロパティ
    var isToolMoneyEnabled: Boolean
        get() = config.getBoolean(USE_TOOL_MONEY_PATH, false)  // 設定から有効かどうかを取得。デフォルトは false
        set(enabled) {
            config[USE_TOOL_MONEY_PATH] = enabled  // 設定を更新
            plugin.saveConfig()  // 設定ファイルを保存
        }

    // 指定されたツールの素材に対して設定されているお金の量を取得するメソッド
    fun getMoneyForTool(material: Material): Int {
        return config.getInt(TOOL_MONEY_PATH + material.name, DEFAULT_MONEY)  // 設定からお金の量を取得。デフォルトは 50
    }

    companion object {
        // 設定ファイル内のツールのお金に関するパス
        private const val TOOL_MONEY_PATH = "tool-money."
        // 設定ファイル内で「ツールでお金を使用する」機能に関するパス
        private const val USE_TOOL_MONEY_PATH = "use-tool-money"
        // デフォルトのお金の量
        private const val DEFAULT_MONEY = 50
    }
}
