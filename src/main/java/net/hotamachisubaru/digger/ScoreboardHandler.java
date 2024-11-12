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

    // プラグインのインスタンスとスコアボードの管理
    private final JavaPlugin plugin;
    private final Scoreboard scoreboard;
    private final Objective objective;

    // コンストラクタでスコアボードとObjectiveを初期化
    public ScoreboardHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = setupObjective();
    }

    // スコアボードのObjectiveを設定するメソッド
    private Objective setupObjective() {
        // 新しいObjectiveを登録し、表示スロットをSIDEBARに設定
        Objective objective = scoreboard.registerNewObjective("stats", "dummy", ChatColor.GREEN + "整地の順位");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        return objective;
    }

    // スコアボードを更新するためのメソッド（静的メソッド）
    public static void updateScoreboard(Player viewingPlayer) {
        // プラグインのインスタンスを取得
        Digger pluginInstance = Digger.getInstance();
        // プレイヤーごとのスコアボード表示のオンオフを取得（デフォルトは表示する状態）
        Boolean showScoreboard = pluginInstance.scoreboardToggles.getOrDefault(viewingPlayer.getUniqueId(), true);

        // スコアボードの表示が無効の場合は処理を終了
        if (!showScoreboard) {
            return;
        }

        // 新しいスコアボードとObjectiveを作成
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("stats", "dummy", ChatColor.GREEN + "整地の順位");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // 掘ったブロック数に基づいてプレイヤーデータをソートし、トップ10を取得
        List<Map.Entry<UUID, Digger.PlayerData>> sortedList = pluginInstance.blockCount.entrySet().stream()
                .sorted((entry1, entry2) -> Integer.compare(entry2.getValue().getBlocksMined(), entry1.getValue().getBlocksMined()))
                .limit(10)
                .collect(Collectors.toList());

        // トップ10プレイヤーをスコアボードに表示
        for (Map.Entry<UUID, Digger.PlayerData> entry : sortedList) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
            // プレイヤー名が取得できない場合は "Unknown" と表示
            String listedPlayerName = offlinePlayer.getName() == null ? "Unknown" : offlinePlayer.getName();
            objective.getScore(listedPlayerName).setScore(entry.getValue().getBlocksMined());
        }

        // プレイヤーのランクを決定
        Digger.PlayerData playerData = pluginInstance.blockCount.getOrDefault(viewingPlayer.getUniqueId(), new Digger.PlayerData("", 0));
        int viewerScore = playerData.getBlocksMined();
        int viewerRankIndex = sortedList.stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList())
                .indexOf(viewingPlayer.getUniqueId());
        // プレイヤーのランクがトップ10以内かをチェックし、適切な表示を設定
        String rankDisplayText = viewerRankIndex < 0 || viewerRankIndex >= 10 ? "ランキング外" : (viewerRankIndex + 1) + "位";

        // 空行とプレイヤー情報を表示
        objective.getScore(" ").setScore(2);  // 空行を挿入して視覚的に見やすくする
        objective.getScore(ChatColor.GOLD + "あなたの順位: " + rankDisplayText).setScore(1);
        objective.getScore(ChatColor.GREEN + "掘ったブロック数: " + ChatColor.WHITE + viewerScore + "ブロック").setScore(0);

        // プレイヤーの現在の座標を表示
        Location location = viewingPlayer.getLocation();
        String locationDisplay = ChatColor.WHITE + "座標: " + ChatColor.RED +
                " X:" + location.getBlockX() +
                " Y:" + location.getBlockY() +
                " Z:" + location.getBlockZ();
        objective.getScore(locationDisplay).setScore(-1);

        // スコアボードをプレイヤーに適用
        viewingPlayer.setScoreboard(scoreboard);
    }
}
