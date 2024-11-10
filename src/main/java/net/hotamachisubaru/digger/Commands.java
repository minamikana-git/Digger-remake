package net.hotamachi.digger;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public class Commands implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> blockCount;

    // コンストラクタ
    public Commands(JavaPlugin plugin, Map<UUID, Integer> blockCount) {
        this.plugin = plugin;
        this.blockCount = blockCount;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーからのみ実行できます。");
            return true;
        }

        Player player = (Player) sender;
        String cmdName = command.getName().toLowerCase();

        switch (cmdName) {
            case "reload":
                return handleReloadCommand(player);

            case "set":
                return handleSetCommand(player, args);

            default:
                return false;
        }
    }

    // リロードコマンド処理
    private boolean handleReloadCommand(Player player) {
        if (!player.hasPermission("digger.reload")) {
            player.sendMessage("§cあなたにはこのコマンドを実行する権限がありません。");
            return true;
        }

        plugin.reloadConfig();
        player.sendMessage("§aconfig.ymlを再読み込みしました。");
        return true;
    }

    // スコア設定コマンド処理
    private boolean handleSetCommand(Player player, String[] args) {
        if (!player.hasPermission("digger.set")) {
            player.sendMessage("§cあなたにはこのコマンドを実行する権限がありません。");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage("§c使用法: /set <プレイヤー名> <スコア>");
            return true;
        }

        String playerName = args[0];
        try {
            int newScore = Integer.parseInt(args[1]);
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);
            UUID targetUUID = targetPlayer.getUniqueId();

            // スコアを設定
            blockCount.put(targetUUID, newScore);
            player.sendMessage("§a" + playerName + "のスコアを" + newScore + "に設定しました。");
        } catch (NumberFormatException e) {
            player.sendMessage("§c無効なスコアです。数字を入力してください。");
        }
        return true;
    }
}
