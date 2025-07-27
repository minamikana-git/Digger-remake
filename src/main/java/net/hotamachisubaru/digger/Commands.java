package net.hotamachisubaru.digger;

import net.hotamachisubaru.digger.util.SchedulerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class Commands implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> blockCount;

    public Commands(JavaPlugin plugin) {
        this.plugin = plugin;
        this.blockCount = new HashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            // コンソール等は色付け不要なので普通のメッセージでOK
            sender.sendMessage("このコマンドはプレイヤーからのみ実行できます。");
            return true;
        }

        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                SchedulerUtil.runAtPlayer(player, plugin, () -> handleReloadCommand(player));
                return true;
            }
            case "set" -> {
                SchedulerUtil.runAtPlayer(player, plugin, () -> handleSetCommand(player, args));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void handleSetCommand(Player player, String[] args) {
        if (!player.hasPermission("digger.set")) {
            player.sendMessage(color("§c権限がありません"));
            return;
        }
        if (args.length != 2) {
            player.sendMessage(color("§c正しい使い方: /set <プレイヤー名> <値>"));
            return;
        }
        Player targetPlayer = plugin.getServer().getPlayer(args[0]);
        if (targetPlayer == null) {
            player.sendMessage(color("§c指定されたプレイヤーが見つかりません: " + args[0]));
            return;
        }
        int value;
        try {
            value = Integer.parseInt(args[1]);
            if (value < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(color("§c設定する値は正の整数でなければなりません。"));
            return;
        }
        blockCount.put(targetPlayer.getUniqueId(), value);
        player.sendMessage(color("§aプレイヤー " + targetPlayer.getName() + " のブロック数を " + value + " に設定しました。"));
        targetPlayer.sendMessage(color("§aあなたのブロック数が " + value + " に設定されました。"));
    }

    private void handleReloadCommand(Player player) {
        if (!player.hasPermission("digger.reload")) {
            player.sendMessage(color("§cあなたにはこのコマンドを実行する権限がありません。"));
            return;
        }
        plugin.reloadConfig();
        player.sendMessage(color("§aconfig.ymlを再読み込みしました。"));
    }

    private Component color(String message) {
        // §での色コードをAdventure Componentに変換
        return LegacyComponentSerializer.legacySection().deserialize(message);
    }
}
