package net.hotamachisubaru.digger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScoreboardHandler {

    private final JavaPlugin plugin;
    private Scoreboard scoreboard;

    public ScoreboardHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        setupObjective();
    }

    private void setupObjective() {
        Objective objective = scoreboard.registerNewObjective("stats", "dummy", ChatColor.GREEN + "整地の順位");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    public static void updateScoreboard(Player viewingPlayer) {

        Boolean showScoreboard = scoreboardToggles.getOrDefault(viewingPlayer.getUniqueId(), true);
        if (showScoreboard) {

            // スコアボードのセットアップ
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective("stats", "dummy", ChatColor.GREEN + "整地の順位");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);


// ソートされたリストを取得
            List<Map.Entry<UUID, Digger.PlayerData>> sortedList = blockCount.entrySet().stream()
                    .sorted((entry1, entry2) -> Integer.compare(entry2.getValue().getBlocksMined(), entry1.getValue().getBlocksMined()))
                    .limit(10)
                    .collect(Collectors.toList());


            // 空行
            objective.getScore(" ").setScore(1);


            // プレイヤーのランクを決定
            Digger.PlayerData defaultData = new Digger.PlayerData("", 0);
            Digger.PlayerData playerData = blockCount.getOrDefault(viewingPlayer.getUniqueId(), defaultData);
            int viewerScore = playerData.getBlocksMined();
            int viewerRankIndex = sortedList.stream()
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList())
                    .indexOf(viewingPlayer.getUniqueId());
            String rankDisplayText = viewerRankIndex < 0 || viewerRankIndex >= 10 ? "ランキング外" : (viewerRankIndex + 1) + "位";

            // トップ10プレイヤーをスコアボードに表示
            for (int i = 0; i < sortedList.size(); i++) {
                Map.Entry<UUID, Digger.PlayerData> entry = sortedList.get(i);
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
                String listedPlayerName = offlinePlayer.getName() == null ? "Unknown" : offlinePlayer.getName();
                objective.getScore(listedPlayerName).setScore(entry.getValue().getBlocksMined());
            }

            // 空行
            objective.getScore(" ").setScore(1);

            objective.getScore(ChatColor.GOLD + "あなたの順位: " + rankDisplayText).setScore(0);
            objective.getScore(ChatColor.GREEN + "掘ったブロック数: " + ChatColor.WHITE + viewerScore + "ブロック").setScore(-1);
            // プレイヤーの座標を表示
            Location location = viewingPlayer.getLocation();
            String locationDisplay = ChatColor.WHITE + "座標: " + ChatColor.RED +
                    " X:" + location.getBlockX() +
                    " Y:" + location.getBlockY() +
                    " Z:" + location.getBlockZ();
            objective.getScore(locationDisplay).setScore(-2);
            // スコアボードをプレイヤーに適用
            viewingPlayer.setScoreboard(scoreboard);
        }
    }
}

