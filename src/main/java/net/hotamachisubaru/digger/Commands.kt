package net.hotamachisubaru.digger

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class Commands(private val plugin: JavaPlugin, private val blockCount: MutableMap<UUID, Int>) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("§cこのコマンドはプレイヤーからのみ実行できます。")
            return true
        }

        return when (command.name.lowercase(Locale.getDefault())) {
            "reload" -> {
                handleReloadCommand(sender)
            }

            "set" -> {
                handleSetCommand(sender, args)
            }

            else -> {
                false
            }
        }
    }

    private fun handleReloadCommand(player: Player): Boolean {
        if (!player.hasPermission("digger.reload")) {
            player.sendMessage("§cあなたにはこのコマンドを実行する権限がありません。")
            return true
        }

        plugin.reloadConfig()
        player.sendMessage("§aconfig.ymlを再読み込みしました。")
        return true
    }

    private fun handleSetCommand(player: Player, args: Array<String>): Boolean {
        if (!player.hasPermission("digger.set")) {
            player.sendMessage("§cあなたにはこのコマンドを実行する権限がありません。")
            return true
        }

        if (args.size != 2) {
            player.sendMessage("§c使用法: /set <プレイヤー名> <スコア>")
            return true
        }

        val playerName = args[0]
        try {
            val newScore = args[1].toInt()
            val targetPlayer = Bukkit.getOfflinePlayer(playerName)
            val targetUUID = targetPlayer.uniqueId

            blockCount[targetUUID] = newScore
            player.sendMessage("§a" + playerName + "のスコアを" + newScore + "に設定しました。")
        } catch (e: NumberFormatException) {
            player.sendMessage("§c無効なスコアです。数字を入力してください。")
        }
        return true
    }
}
