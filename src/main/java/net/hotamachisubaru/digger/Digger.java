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
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * メインプラグインクラス
 */
public class Digger extends JavaPlugin implements Listener {

    // シングルトン
    private static Digger instance;
    public static Digger getInstance() { return instance; }
    private final PluginManager pm = Bukkit.getPluginManager();

    // ロガー
    private Logger logger;

    // データ構造
    public final Map<UUID, PlayerData> diamondCount = new HashMap<>();
    private final Set<Location> placedBlocks = new HashSet<>();
    private final Map<Location, UUID> placedBlocksWithUUID = new HashMap<>();

    // データベース
    private MySQLDatabase mySQLDatabase;
    private SQLiteDatabase sqLiteDatabase;

    // 更新間隔
    private final long scoreboardUpdateInterval = 20L;

    @Override
    public void onEnable() {
        instance = this;
        this.logger = getLogger();

        logger.info("プラグインを有効化します。");
        saveDefaultConfig();
        setupFiles();

        if (!setupDatabaseSafe()) {
            logger.severe("データベース初期化失敗のため、プラグインを無効化します。");
            pm.disablePlugin(this);
            return;
        }

        loadData();

        getCommand("reload").setExecutor(new Commands(this));
        getCommand("set").setExecutor(new Commands(this));
        pm.registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                updateAllPlayersScoreboard();
            }
        }.runTaskTimer(this, 20L, scoreboardUpdateInterval);
    }

    @Override
    public void onDisable() {
        logger.info("プラグインを無効化します。データを保存中…");
        saveData();
        saveConfig();
    }

    /** データベース初期化: 成功でtrue */
    private boolean setupDatabaseSafe() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        try {
            if ("mysql".equals(dbType)) {
                Properties prop = new Properties();
                prop.load(new FileInputStream(new File(getDataFolder(), "config.properties")));
                mySQLDatabase = new MySQLDatabase(prop);

                if (!mySQLDatabase.isConnected()) {
                    logger.severe("MySQL への接続に失敗しました。");
                    return false;
                }
                logger.info("MySQL に正常に接続しました。");
            } else {
                sqLiteDatabase = SQLiteDatabase.Companion.getInstance();
                sqLiteDatabase.openConnection(getDataFolder().getAbsolutePath());
                logger.info("SQLite に正常に接続しました。");
            }
            return true;
        } catch (Exception e) {
            logger.severe("データベース初期化エラー: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /** リソース配置 */
    private void setupFiles() {
        saveResource("config.properties", false);
        saveResource("Database.db", false);
    }

    // === イベント登録 ===
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (type == Material.DIAMOND_ORE || type == Material.DEEPSLATE_DIAMOND_ORE) {
            placedBlocks.add(event.getBlockPlaced().getLocation());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material type = block.getType();

        if (type != Material.DIAMOND_ORE && type != Material.DEEPSLATE_DIAMOND_ORE) return;
        if (placedBlocks.remove(block.getLocation())) return; // 設置ダイヤは無視

        incrementDiamondCount(player);
    }

    private void incrementDiamondCount(Player player) {
        UUID id = player.getUniqueId();
        PlayerData data = diamondCount.getOrDefault(id, new PlayerData(player.getName(), 0));
        data.setDiamondMined(data.getDiamondMined() + 1);
        diamondCount.put(id, data);
    }

    /** スコアボードの一括更新 */
    public void updateAllPlayersScoreboard() {
        Map<UUID, Integer> counts = diamondCount.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDiamondMined()));
        CoordinatesDisplay disp = new CoordinatesDisplay(this, counts);
        for (Player p : Bukkit.getOnlinePlayers()) {
            disp.updateScoreboard(p);
        }
    }

    /** データ読込 */
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

    /** データ保存 */
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

    // === 内部データクラス ===
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
