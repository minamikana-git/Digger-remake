package net.hotamachisubaru.digger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Criteria;

import java.util.Map;
import java.util.UUID;

public class CoordinatesDisplay {

    private final Digger plugin;
    private final Map<UUID, Integer> diamondCount;
    private final NamespacedKey coordsKey;
    private final NamespacedKey rankKey;

    public CoordinatesDisplay(Digger plugin, Map<UUID, Integer> diamondCount) {
        this.plugin = plugin;
        this.diamondCount = diamondCount;
        this.coordsKey = new NamespacedKey(plugin, "showCoords");
        this.rankKey = new NamespacedKey(plugin, "diggingRank");
    }

    /**
     * 座標表示用スコアボードを更新
     */
    private void updateCoordinatesScoreboard(Player player) {
        Scoreboard board = player.getScoreboard();

        Objective obj = board.getObjective(String.valueOf(coordsKey));
        if (obj == null) {
            obj = board.registerNewObjective(
                    String.valueOf(coordsKey),
                    Criteria.DUMMY,
                    Component.text("個数と座標", NamedTextColor.GREEN)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        var loc = player.getLocation();
        obj.getScore("X: " + loc.getBlockX()).setScore(3);
        obj.getScore("Y: " + loc.getBlockY()).setScore(2);
        obj.getScore("Z: " + loc.getBlockZ()).setScore(1);

        player.setScoreboard(board);
    }

    /**
     * ダイヤ掘削ランキングを更新
     */
    public void updateScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();

        Objective obj = board.registerNewObjective(
                String.valueOf(rankKey),
                Criteria.DUMMY,
                Component.text("ダイヤの順位", NamedTextColor.AQUA)
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        Integer count = diamondCount.get(player.getUniqueId());
        if (count != null) {
            obj.getScore(player.getName()).setScore(count);
        }

        player.setScoreboard(board);
    }

    /**
     * 2秒ごとに全員のスコアを更新
     */
    public void startCoordinateUpdates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateCoordinatesScoreboard(p);
                    updateScoreboard(p);
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }
}
