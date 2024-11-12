package net.hotamachisubaru.digger

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.DisplaySlot
import java.util.*

// CoordinatesDisplay クラスは、プレイヤーに対して座標と掘削の順位を表示する機能を提供します。
// このクラスには、スコアボードの更新を管理する機能が含まれています。
class CoordinatesDisplay(private val plugin: Digger, private val blockCount: Map<UUID, Int>) {

    // プレイヤーの座標を表示するスコアボードを更新するメソッドです。
    private fun updateCoordinatesScoreboard(player: Player) {
        // プレイヤーのスコアボードを取得
        val scoreboard = player.scoreboard

        // 既存の "showCoords" という目的 (objective) を取得します。
        var objective = scoreboard.getObjective("showCoords")

        // もし目的が存在しなければ、新しく登録します。
        if (objective == null) {
            objective = scoreboard.registerNewObjective("showCoords", "dummy", ChatColor.GREEN.toString() + "掘削と座標")
            objective.setDisplaySlot(DisplaySlot.SIDEBAR) // サイドバーに表示する設定を行います。
        }

        // プレイヤーの現在の座標を取得します。
        val location = player.location

        // それぞれの座標 (X, Y, Z) の情報をスコアとして設定します。
        // 各スコアには順位を表す値を設定し、スコアボードに表示される順序を制御しています。
        objective.getScore(ChatColor.WHITE.toString() + "X: " + ChatColor.RED + location.blockX).score = 3
        objective.getScore(ChatColor.WHITE.toString() + "Y: " + ChatColor.RED + location.blockY).score = 2
        objective.getScore(ChatColor.WHITE.toString() + "Z: " + ChatColor.RED + location.blockZ).score = 1

        // プレイヤーのスコアボードを更新します。
        player.scoreboard = scoreboard
    }

    // プレイヤーの掘削ランクを表示するスコアボードを更新するメソッドです。
    private fun updateDiggingRankScoreboard(player: Player) {
        // 新しいスコアボードを作成します（以前のものではなく新規作成します）。
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard

        // "diggingRank" という目的を作成し、表示名に色付きテキストを設定します。
        val objective = scoreboard.registerNewObjective("diggingRank", "dummy", ChatColor.AQUA.toString() + "整地の順位")

        // サイドバーに表示する設定を行います。
        objective.displaySlot = DisplaySlot.SIDEBAR

        // プレイヤーに新しいスコアボードを設定します。
        player.scoreboard = scoreboard
    }

    // 全てのオンラインプレイヤーの座標表示を定期的に更新するためのメソッドです。
    fun startCoordinateUpdates() {
        object : BukkitRunnable() {
            override fun run() {
                // 全てのオンラインプレイヤーの座標スコアボードを更新します。
                for (player in Bukkit.getOnlinePlayers()) {
                    updateCoordinatesScoreboard(player)
                }
            }
        }.runTaskTimer(plugin, 0L, 20L) // このタスクはサーバーが起動した後、毎秒 (20ティック) ごとに実行されます。
    }
}
