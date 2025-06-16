package net.hotamachisubaru.digger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.util.*;

public class CoordinatesDisplay {

    private final Digger plugin;
    private final Map<UUID, Integer> diamondCount;

    public CoordinatesDisplay(Digger plugin, Map<UUID, Integer> diamondCount) {
        this.plugin = plugin;
        this.diamondCount = diamondCount;
    }

    // サイドバーにランキングと座標を同時表示
    public void updateScoreboard(Player player) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;
        Scoreboard board = sm.getNewScoreboard();

        Objective obj = board.registerNewObjective(
                "digger_board",
                "dummy",
                "§bダイヤランキング"
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // ランキング表示準備
        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(diamondCount.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int line = 10;
        int rank = 1;
        int myRank = -1;
        UUID myId = player.getUniqueId();

        // 上位3人の表示
        for (Map.Entry<UUID, Integer> entry : sorted) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Unknown";
            int count = entry.getValue();
            String label = rank + "位: " + name + " [" + count + "] 個";
            obj.getScore(label).setScore(line--);

            if (entry.getKey().equals(myId)) {
                myRank = rank;
            }
            if (rank >= 3) break;
            rank++;
        }

        // 圏外の自分も表示
        Integer myCount = diamondCount.get(myId);
        if (myCount != null && myRank > 3) {
            obj.getScore("あなた(" + myRank + "位): " + myCount + " 個").setScore(line--);
        }

        // 座標表示
        var loc = player.getLocation();
        obj.getScore("§aX: " + loc.getBlockX()).setScore(line--);
        obj.getScore("§aY: " + loc.getBlockY()).setScore(line--);
        obj.getScore("§aZ: " + loc.getBlockZ()).setScore(line--);

        player.setScoreboard(board);
    }

    /**
     * 全員分のスコアボードを定期的に更新
     */
    public void startCoordinateUpdates() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(p);
                }
            }
        }.runTaskTimer(plugin, 0L, 40L); // 2秒ごと
    }
}
