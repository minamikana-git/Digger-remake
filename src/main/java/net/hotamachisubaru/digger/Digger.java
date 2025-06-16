package net.hotamachisubaru.digger;

import net.hotamachisubaru.digger.mysql.MySQLDatabase;
import net.hotamachisubaru.digger.sqlite.SQLiteDatabase;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * メインプラグインクラス
 */
public class Digger extends JavaPlugin implements Listener {

    // === フィールド ===
    private static Digger instance;
    private final Logger logger = getLogger();
    private final PluginManager pm = getServer().getPluginManager();
    // データ
    public final Map<UUID, PlayerData> diamondCount = new HashMap<>();
    private final Set<Location> placedBlocks = new HashSet<>();
    private final Map<Location, UUID> placedBlocksWithUUID = new HashMap<>();

    // データベース
    private MySQLDatabase mySQLDatabase;
    private SQLiteDatabase sqLiteDatabase;

    // 更新間隔
    private final long scoreboardUpdateInterval = 20L; // 1秒ごと

    // === プラグイン初期化 ===
    public Digger() {
        instance = this;
    }
    public static Digger getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        logger.info("プラグインを有効化します。");
        saveDefaultConfig();
        setupFiles();
        setupDatabase();
        getCommand("reload").setExecutor(new Commands(this));
        getCommand("set").setExecutor(new Commands(this));
        pm.registerEvents(this, this);

        // サイドバーの定期更新
        new BukkitRunnable() {
            @Override
            public void run() {
                updateAllPlayersScoreboard();
            }
        }.runTaskTimer(this, 20L, scoreboardUpdateInterval);
    }

    private void setupFiles() {
        saveResource("config.properties", false);
        saveResource("Database.db", false);
    }

    // === データベース初期化 ===
    private void setupDatabase() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        if ("mysql".equals(dbType)) {
            initMySQL();
        } else {
            initSQLite();
        }
    }

    private void initMySQL() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(new File(getDataFolder(), "config.properties")));
            mySQLDatabase = new MySQLDatabase(prop);

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

    private void initSQLite() {
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

    // === イベント登録 ===

    // ダイヤ設置ブロック記録
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) {
            placedBlocks.add(event.getBlockPlaced().getLocation());
        }
    }

    // ダイヤ破壊判定（自然生成のみカウント）
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        if (type != Material.DIAMOND_ORE && type != Material.DEEPSLATE_DIAMOND_ORE) return;

        if (placedBlocks.remove(block.getLocation())) return; // 自分設置ならカウントしない

        // カウントアップ
        incrementDiamondCount(player);
    }

    private void incrementDiamondCount(Player player) {
        UUID id = player.getUniqueId();
        PlayerData data = diamondCount.getOrDefault(id, new PlayerData(player.getName(), 0));
        data.setDiamondMined(data.getDiamondMined() + 1);
        diamondCount.put(id, data);
    }

    // === スコアボード更新 ===

    public void updateAllPlayersScoreboard() {
        // Map<UUID, Integer> に変換
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

    // === データ保存/ロード ===

    @Override
    public void onDisable() {
        logger.info("プラグインを無効化します。データを保存中…");
        saveData();
        saveConfig();
    }

    public void loadData() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        if ("mysql".equals(dbType)) {
            if (mySQLDatabase.isConnected()) {
                Map<UUID, PlayerData> data = mySQLDatabase.loadData();
                diamondCount.clear();
                diamondCount.putAll(data);
                logger.info("MySQL から読み込み完了。");
            }
        } else {
            try {
                Map<UUID, PlayerData> data = sqLiteDatabase.getData();
                diamondCount.clear();
                diamondCount.putAll(data);
                logger.info("SQLite から読み込み完了。");
            } catch (SQLException e) {
                logger.severe("SQLite 読み込みエラー: " + e.getMessage());
            }
        }
    }

    public void saveData() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        if ("mysql".equals(dbType)) {
            if (mySQLDatabase.isConnected()) {
                mySQLDatabase.saveData(diamondCount, new ArrayList<>(placedBlocks), placedBlocksWithUUID);
                logger.info("MySQL に保存完了。");
            }
        } else {
            try {
                sqLiteDatabase.saveData(diamondCount, new ArrayList<>(placedBlocks));
                logger.info("SQLite に保存完了。");
            } catch (SQLException e) {
                logger.severe("SQLite 保存エラー: " + e.getMessage());
            }
        }
    }

    // === 内部データ構造 ===
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
