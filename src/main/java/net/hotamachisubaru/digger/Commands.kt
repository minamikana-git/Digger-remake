package net.hotamachisubaru.digger

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

// Commandsクラスは、BukkitのCommandExecutorを実装しており、カスタムコマンドの処理を行います。
// このクラスはプラグインのインスタンスと、プレイヤーごとのブロックカウントを保持するためのマップを受け取ります。
class Commands(private val plugin: JavaPlugin, private val toolMoney: ToolMoney) : CommandExecutor {
    private val blockCount: MutableMap<UUID, Int> = mutableMapOf()

    // onCommandメソッドは、Bukkitがコマンドを受け取ったときに呼び出されます。
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        // コマンドの送信者がプレイヤーでない場合は、メッセージを送り終了します。
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーからのみ実行できます。")
            return true
        }

        // コマンド名に応じて処理を分岐します。
        return when (command.name.lowercase(Locale.getDefault())) {
            "reload" -> {
                // "reload" コマンドの処理を行います。
                handleReloadCommand(sender)
            }
            "set" -> {
                // "set" コマンドの処理を行います。
                handleSetCommand(sender, args)
            }
            else -> {
                // その他のコマンドは未対応とします。
                false
            }
        }
    }

    // handleReloadCommandメソッドは、設定ファイルを再読み込みするためのコマンドを処理します。
    private fun handleReloadCommand(player: Player): Boolean {
        // プレイヤーが権限を持っているか確認します。
        if (!player.hasPermission("digger.reload")) {
            player.sendMessage("§cあなたにはこのコマンドを実行する権限がありません。")
            return true
        }

        // プラグインの設定ファイルを再読み込みします。
        plugin.reloadConfig()
        player.sendMessage("§aconfig.ymlを再読み込みしました。")
        return true
    }

    // handleSetCommandメソッドは、特定のプレイヤーのスコアを設定するコマンドを処理します。
    private fun handleSetCommand(player: Player, args: Array<String>): Boolean {
        // プレイヤーが権限を持っているか確認します。
        if (!player.hasPermission("digger.set")) {
            player.sendMessage("§cあなたにはこのコマンドを実行する権限がありません。")
            return true
        }

        // 引数が2つであることを確認します。
        if (args.size != 2) {
            player.sendMessage("§c使用法: /set <プレイヤー名> <スコア>")
            return true
        }

        // プレイヤー名を取得し、スコアを整数に変換します。
        val playerName = args[0]
        try {
            val newScore = args[1].toInt()
            // Bukkitを使って対象プレイヤーのUUIDを取得します。
            val targetPlayer = Bukkit.getOfflinePlayer(playerName)
            val targetUUID = targetPlayer.uniqueId

            // スコアをブロックカウントマップに設定します。
            blockCount[targetUUID] = newScore
            player.sendMessage("§a${playerName}のスコアを${newScore}に設定しました。")
        } catch (e: NumberFormatException) {
            // スコアが無効な場合のエラーメッセージを送信します。
            player.sendMessage("§c無効なスコアです。数字を入力してください。")
        }
        return true
    }
}
