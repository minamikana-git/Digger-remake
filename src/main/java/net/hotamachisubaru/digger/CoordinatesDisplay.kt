package net.hotamachisubaru.digger

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.DisplaySlot
import java.util.*

class CoordinatesDisplay(private val plugin: Digger, private val blockCount: Map<UUID, Int>) {
    private fun updateCoordinatesScoreboard(player: Player) {
        val scoreboard = player.scoreboard
        var objective = scoreboard.getObjective("showCoords")

        if (objective == null) {
            objective =
                scoreboard.registerNewObjective("showCoords", "dummy", ChatColor.GREEN.toString() + "掘削と座標")
            objective.setDisplaySlot(DisplaySlot.SIDEBAR)
        }

        val location = player.location
        objective.getScore(ChatColor.WHITE.toString() + "X: " + ChatColor.RED + location.blockX).score =
            3
        objective.getScore(ChatColor.WHITE.toString() + "Y: " + ChatColor.RED + location.blockY).score =
            2
        objective.getScore(ChatColor.WHITE.toString() + "Z: " + ChatColor.RED + location.blockZ).score =
            1

        player.scoreboard = scoreboard
    }

    private fun updateDiggingRankScoreboard(player: Player) {
        val scoreboard = Bukkit.getScoreboardManager().newScoreboard
        val objective =
            scoreboard.registerNewObjective("diggingRank", "dummy", ChatColor.AQUA.toString() + "整地の順位")
        objective.displaySlot = DisplaySlot.SIDEBAR
        player.scoreboard = scoreboard
    }

    fun startCoordinateUpdates() {
        object : BukkitRunnable() {
            override fun run() {
                for (player in Bukkit.getOnlinePlayers()) {
                    updateCoordinatesScoreboard(player)
                }
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }
}
