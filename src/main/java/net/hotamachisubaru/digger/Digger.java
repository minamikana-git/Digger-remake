package net.hotamachisubaru.digger;

import net.hotamachisubaru.digger.mysql.MySQLDatabase;
import net.hotamachisubaru.digger.sqlite.SQLiteDatabase;
import org.bukkit.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Digger extends JavaPlugin implements Listener {

    private final PluginManager pm = getServer().getPluginManager();
    private final Map<Location, UUID> placedBlocksWithUUID = new HashMap<>();
    private MySQLDatabase mySQLDatabase;
    private SQLiteDatabase sqLiteDatabase;
    private static Digger instance;
    public final Map<UUID, PlayerData> diamondCount = new HashMap<>();
    private final List<Location> placedBlocks = new ArrayList<>();
    private final List<String> worldBlacklist = new ArrayList<>();
    private final long scoreboardUpdateInterval = 20L;
    private Scoreboard scoreboard;
    private Objective objective;
    public final Logger logger = getLogger();

    public Digger() {
        instance = this;
    }

    public static Digger getInstance() {
        return instance;


    }

    @Override
    public void onEnable() {
        logger.info("プラグインを有効化します。");
        setupResource();
        setupDatabase();
        setupCommands();
        setupScoreboard();
        registerEvents();
        startScoreboardUpdater();
    }

    private void startScoreboardUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateAllPlayersScoreboard();
            }
        }.runTaskTimer(this, 20L, scoreboardUpdateInterval);
    }

    private void setupResource() {
        saveDefaultConfig();
        saveResource("config.properties", false);
        saveResource("Database.db", false);
    }

    private void setupDatabase() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        if ("mysql".equals(dbType)) {
            initializeMySQLDatabase();
        } else {
            initializeSQLiteDatabase();
        }
    }

    private void setupCommands() {
        getCommand("reload").setExecutor(new Commands(this));
    }

    private void setupScoreboard() {
        new BukkitRunnable() {
            @Override
            public void run() {
                ScoreboardManager sm = Bukkit.getScoreboardManager();
                if (sm == null) {
                    logger.severe("ScoreboardManager が取得できません。");
                    return;
                }
                scoreboard = sm.getNewScoreboard();

                // ObjectiveCriteria の代わりに "dummy" を文字列で渡す
                objective = scoreboard.registerNewObjective(
                        "stats",
                        "dummy",
                        ChatColor.GREEN + "あなたの順位"
                );
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);

            }
        }.runTaskLater(this, 20L);
    }

    private void registerEvents() {
        pm.registerEvents(this, this);
    }

    private void initializeMySQLDatabase() {
        try {
            if (mySQLDatabase == null) {
                Properties prop = new Properties();
                prop.load(new FileInputStream(new File(getDataFolder(), "config.properties")));
                mySQLDatabase = new MySQLDatabase(prop);
            }
            if (!mySQLDatabase.isConnected()) {
                logger.severe("MySQL への接続に失敗しました。");
            } else {
                logger.info("MySQL に正常に接続しました。");
            }
        } catch (IOException e) {
            logger.severe("config.properties の読み込みに失敗: " + e.getMessage());
            pm.disablePlugin(this);
        }
    }

    private void initializeSQLiteDatabase() {
        // companion object の getInstance() を呼ぶ
        sqLiteDatabase = SQLiteDatabase.Companion.getInstance();
        try {
            if (!sqLiteDatabase.checkConnection()) {
                sqLiteDatabase.openConnection(getDataFolder().getAbsolutePath());
            }
            logger.info("SQLite に正常に接続しました。");
        } catch (SQLException e) {
            logger.severe("SQLite の初期化に失敗: " + e.getMessage());
            pm.disablePlugin(this);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        placedBlocksWithUUID.put(event.getBlock().getLocation(), event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (worldBlacklist.contains(p.getWorld().getName())
                || placedBlocks.remove(event.getBlock().getLocation())
                || isBlockBlacklisted(event.getBlock().getType())) {
            return;
        }
        updateDiamondCount(p);
    }

    private boolean isBlockBlacklisted(Material m) {
        return getConfig().getStringList("block-blacklist").contains(m.name());
    }

    private void updateDiamondCount(Player p) {
        UUID id = p.getUniqueId();
        PlayerData data = diamondCount.getOrDefault(id, new PlayerData(p.getName(), 0));
        data.setDiamondMined(data.getDiamondMined() + 1);
        diamondCount.put(id, data);
    }

    public void updateAllPlayersScoreboard() {
        // Map<UUID, Integer> に変換して渡す
        Map<UUID, Integer> counts = diamondCount.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getDiamondMined()
                ));
        CoordinatesDisplay disp = new CoordinatesDisplay(this, counts);
        for (Player p : Bukkit.getOnlinePlayers()) {
            disp.updateScoreboard(p);
        }
    }

    @Override
    public void onDisable() {
        logger.info("プラグインを無効化します。データを保存中…");
        saveData();
        saveConfig();
    }

    public void loadData() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        if ("mysql".equals(dbType)) {
            loadFromMySQL();
        } else {
            loadFromSQLite();
        }
    }

    private void loadFromSQLite() {
        try {
            Map<UUID, PlayerData> data = sqLiteDatabase.getData();
            diamondCount.clear();
            diamondCount.putAll(data);
            logger.info("SQLite から読み込み完了。");
        } catch (SQLException e) {
            logger.severe("SQLite 読み込みエラー: " + e.getMessage());
        }
    }

    private void loadFromMySQL() {
        if (mySQLDatabase.isConnected()) {
            Map<UUID, PlayerData> data = mySQLDatabase.loadData();
            diamondCount.clear();
            diamondCount.putAll(data);
            logger.info("MySQL から読み込み完了。");
        }
    }

    public void saveData() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        if ("mysql".equals(dbType)) {
            saveToMySQL();
        } else {
            saveToSQLite();
        }
    }

    private void saveToMySQL() {
        if (mySQLDatabase.isConnected()) {
            mySQLDatabase.saveData(
                    diamondCount,
                    placedBlocks,
                    placedBlocksWithUUID
            );
            logger.info("MySQL に保存完了。");
        }
    }

    private void saveToSQLite() {
        try {
            sqLiteDatabase.saveData(diamondCount, placedBlocks);
            logger.info("SQLite に保存完了。");
        } catch (SQLException e) {
            logger.severe("SQLite 保存エラー: " + e.getMessage());
        }
    }

    public static class PlayerData {
        private final String playerName;
        private int diamondMined;

        public PlayerData(String playerName, int diamondMined) {
            this.playerName = playerName;
            this.diamondMined = diamondMined;
        }

        public String getPlayerName() { return playerName; }
        public int getDiamondMined() { return diamondMined; }
        public void setDiamondMined(int v) { this.diamondMined = v; }
    }
}
