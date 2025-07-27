package net.hotamachisubaru.digger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.*;

public class CoordinatesDisplay {
    private final Digger plugin;
    private final Map<UUID, Integer> diamondCount;

    public CoordinatesDisplay(Digger plugin, Map<UUID, Integer> diamondCount) {
        this.plugin = plugin;
        this.diamondCount = diamondCount;
    }

    public void updateScoreboard(Player player) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;
        Scoreboard board = sm.getNewScoreboard();

        Objective obj = board.registerNewObjective(
                "mine_board",
                Criteria.DUMMY,
                "§bダイヤランキング"
        );
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<Map.Entry<UUID, Integer>> sorted = new ArrayList<>(diamondCount.entrySet());
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int line = 10;
        int rank = 1;
        int myRank = -1;
        UUID myId = player.getUniqueId();

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

        Integer myCount = diamondCount.get(myId);
        if (myCount != null && myRank > 3) {
            obj.getScore("あなたの順位：(" + myRank + "位): " + myCount + " 個").setScore(line--);
        }

        var loc = player.getLocation();
        obj.getScore("§aX: " + loc.getBlockX()).setScore(line--);
        obj.getScore("§aY: " + loc.getBlockY()).setScore(line--);
        obj.getScore("§aZ: " + loc.getBlockZ()).setScore(line--);

        player.setScoreboard(board);
    }
}
