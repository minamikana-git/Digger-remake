package net.hotamachi.digger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import net.hotamachisubaru.digger.Digger;

import java.util.Map;
import java.util.UUID;

public class CoordinatesDisplay {
    private Digger plugin;
    private Map<UUID, Integer> blockCount;

    public CoordinatesDisplay(Digger plugin, Map<UUID, Integer> blockCount) {
        this.plugin = plugin;
        this.blockCount = blockCount;
    }

    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective("showCoords");

        if (objective == null) {
            objective = scoreboard.registerNewObjective("showCoords", "dummy", ChatColor.GREEN + "掘削と座標");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // 座標を表示
        Location location = player.getLocation();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        objective.getScore(ChatColor.WHITE + "X: " + ChatColor.RED + x).setScore(3);
        objective.getScore(ChatColor.WHITE + "Y: " + ChatColor.RED + y).setScore(2);
        objective.getScore(ChatColor.WHITE + "Z: " + ChatColor.RED + z).setScore(1);

        player.setScoreboard(scoreboard);
    }

    public void updateDiggingRankScoreboard(UUID viewingPlayerUUID, Player viewingPlayer) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("diggingRank", "dummy", ChatColor.AQUA + "整地の順位");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        viewingPlayer.setScoreboard(scoreboard);
    }

    public void showCoordinates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 20ティックごとに更新
    }
}