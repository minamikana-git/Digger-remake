package net.hotamachisubaru.digger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.Map;
import java.util.UUID;

public class CoordinatesDisplay {
    private final Digger plugin;
    private final Map<UUID, Integer> blockCount;

    public CoordinatesDisplay(Digger plugin, Map<UUID, Integer> blockCount) {
        this.plugin = plugin;
        this.blockCount = blockCount;
    }

    private void updateCoordinatesScoreboard(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective("showCoords");

        if (objective == null) {
            objective = scoreboard.registerNewObjective("showCoords", "dummy", ChatColor.GREEN + "掘削と座標");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        Location location = player.getLocation();
        objective.getScore(ChatColor.WHITE + "X: " + ChatColor.RED + location.getBlockX()).setScore(3);
        objective.getScore(ChatColor.WHITE + "Y: " + ChatColor.RED + location.getBlockY()).setScore(2);
        objective.getScore(ChatColor.WHITE + "Z: " + ChatColor.RED + location.getBlockZ()).setScore(1);

        player.setScoreboard(scoreboard);
    }

    private void updateDiggingRankScoreboard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("diggingRank", "dummy", ChatColor.AQUA + "整地の順位");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(scoreboard);
    }

    public void startCoordinateUpdates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateCoordinatesScoreboard(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }
}
