package net.hotamachi.digger;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class Digger extends JavaPlugin implements Listener {

    private final Map<UUID, Integer> blockCount = new HashMap<>();
    private Scoreboard scoreboard;
    private Economy economy;


    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("&4Vaultプラグインが見つかりませんでした。プラグインを無効化します。");

            getServer().getPluginManager().disablePlugin(this);

            return;

        }

        this.getServer().getPluginManager().registerEvents(this, this);

        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("トップ10", "トップ10", "あなたの順位");

        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    @EventHandler
    public void depositMoney(BlockBreakEvent event, UUID playerUUID) {

        UUID playerID = event.getPlayer().getUniqueId();
        blockCount.put(playerID, blockCount.getOrDefault(playerID, 0) + 1);

        if (Math.random() < 0.02) {

            economy.depositPlayer(event.getPlayer(), 50);

            event.getPlayer().sendMessage("§3 50円を手に入れました。");

            updateScoreboard(playerID, playerUUID);
        }
    }

    private void updateScoreboard(UUID playerID, UUID playerUUID) {

        Objective objective = scoreboard.getObjective("トップ10");

        for (String entry : scoreboard.getEntries()) {

            scoreboard.resetScores(entry);
        }

        List<Map.Entry<UUID, Integer>> sortedList = blockCount.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).collect(Collectors.toList());

        int playerRank = 1;
        for (Map.Entry<UUID, Integer> entry : sortedList) {
            if (entry.getKey().equals(playerUUID)) {
                break;
            }
            playerRank++;
        }

        for (int i = 0; i < Math.min(10, sortedList.size()); i++) {
            Map.Entry<UUID, Integer> entry = sortedList.get(i);

            String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            int score = entry.getValue();

            objective.getScore(playerName).setScore(score);

        }

        objective.getScore("あなたの順位：　" + playerRank + "位").setScore(0);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}


