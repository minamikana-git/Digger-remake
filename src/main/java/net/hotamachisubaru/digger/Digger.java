package net.hotamachisubaru.digger;

import net.hotamachisubaru.digger.database.*;
import net.hotamachisubaru.digger.util.SchedulerUtil;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

public class Digger extends JavaPlugin implements Listener {
    private static Digger instance;

    public static Digger getInstance() {
        return instance;
    }

    private final PluginManager pm = Bukkit.getPluginManager();
    public final Map<UUID, PlayerData> diamondCount = new HashMap<>();
    private final Set<Location> placedBlocks = new HashSet<>();
    private final Map<Location, UUID> placedBlocksWithUUID = new HashMap<>();
    private MySQLDatabase mySQLDatabase;
    private SQLiteDatabase sqLiteDatabase;
    private Logger logger;
    private Economy economy;
    private final long scoreboardUpdateInterval = 20L;

    @Override
    public void onEnable() {
        instance = this;
        this.logger = getLogger();
        logger.info("プラグインを有効化します。");
        saveDefaultConfig();
        setupEconomy();
        setupFiles();

        // ここで1回だけ
        if (!setupDatabaseSafe()) {
            logger.severe("データベース初期化失敗のため、プラグインを無効化します。");
            pm.disablePlugin(this);
            return;
        }
        loadData();
        Commands commands = new Commands(this);
        getCommand("reload").setExecutor(commands);
        getCommand("set").setExecutor(commands);
        pm.registerEvents(this, this);
        // Folia/Paper対応: 全員分非同期でスコアボード更新
        CompletableFuture.runAsync(() -> {
            while (Bukkit.getPluginManager().isPluginEnabled(this)) {
                Bukkit.getOnlinePlayers().forEach(player -> SchedulerUtil.runAtPlayer(player, this, () -> updateScoreboardSafe(player)));
                try {
                    Thread.sleep(scoreboardUpdateInterval * 50); // Convert ticks to milliseconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }


    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            logger.warning("§4エラー：Vaultプラグインが見つかりませんでした。");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            logger.warning("§4エラー:Economyサービスプロバイダが登録されていません。");
            return false;
        }
        economy = rsp.getProvider();
        if (economy == null) {
            logger.warning("§4エラー：Economyサービスが見つかりません。");
            return false;
        }
        return true;
    }


    @Override
    public void onDisable() {
        logger.info("プラグインを無効化します。データを保存中…");
        saveData();
        saveConfig();
    }

    private boolean setupDatabaseSafe() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        boolean initialized = false;
        try {
            if ("mysql".equals(dbType)) {
                Properties prop = new Properties();
                prop.load(new FileInputStream(new File(getDataFolder(), "config.properties")));
                mySQLDatabase = new MySQLDatabase(prop);
                if (mySQLDatabase.isConnected()) {
                    logger.info("MySQL に正常に接続しました。");
                    initialized = true;
                } else {
                    logger.severe("MySQL への接続に失敗しました。SQLiteに切り替えます。");
                }
            }
        } catch (Exception e) {
            logger.severe("MySQLデータベース初期化エラー: " + e.getMessage() + " SQLiteに切り替えます。");
        }

        // MySQL失敗時はSQLiteを必ず初期化
        if (!initialized) {
            try {
                sqLiteDatabase = SQLiteDatabase.Companion.getInstance();
                sqLiteDatabase.openConnection(getDataFolder().getAbsolutePath());
                logger.info("SQLite に正常に接続しました。");
                initialized = true;
            } catch (Exception e) {
                logger.severe("SQLiteデータベース初期化エラー: " + e.getMessage());
                sqLiteDatabase = null;
                initialized = false;
            }
        }
        return initialized;
    }


    private void setupFiles() {
        saveResource("config.properties", false);
        saveResource("Database.db", false);
    }

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
        if (placedBlocks.remove(block.getLocation())) return;
        // Folia対応: プレイヤースレッドで同期操作
        SchedulerUtil.runAtPlayer(player, this, () -> incrementDiamondCount(player));
    }

    private void incrementDiamondCount(Player player) {
        UUID id = player.getUniqueId();
        PlayerData data = diamondCount.getOrDefault(id, new PlayerData(player.getName(), 0));
        data.setDiamondMined(data.getDiamondMined() + 1);
        diamondCount.put(id, data);
    }

    // プレイヤーごとにスコアボード更新
    public void updateScoreboardSafe(Player player) {
        Map<UUID, Integer> counts = diamondCount.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDiamondMined()));
        CoordinatesDisplay disp = new CoordinatesDisplay(this, counts);
        disp.updateScoreboard(player);
    }
    public void saveData() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        boolean success = false;

        // MySQL優先
        if ("mysql".equals(dbType) && mySQLDatabase != null) {
            try {
                if (mySQLDatabase.isConnected()) {
                    mySQLDatabase.saveData(diamondCount, new ArrayList<>(placedBlocks), placedBlocksWithUUID);
                    logger.info("MySQL に保存完了。");
                    success = true;
                } else {
                    logger.warning("MySQL 接続が確立されていません。SQLiteに切り替えます。");
                }
            } catch (Exception e) {
                logger.severe("MySQL 保存エラー: " + e.getMessage() + "。SQLiteに切り替えます。");
            }
        }

        // SQLite (null安全も兼ねる)
        if (!success && sqLiteDatabase != null) {
            try {
                sqLiteDatabase.saveData(diamondCount, new ArrayList<>(placedBlocks));
                logger.info("SQLite に保存完了。");
            } catch (SQLException e) {
                logger.severe("SQLite 保存エラー: " + e.getMessage());
            }
        } else if (!success) {
            logger.severe("どのデータベースにも保存できませんでした。");
        }
    }

    public void loadData() {
        String dbType = getConfig().getString("database.type", "sqlite").toLowerCase();
        boolean loaded = false;

        if ("mysql".equals(dbType) && mySQLDatabase != null) {
            try {
                if (mySQLDatabase.isConnected()) {
                    Map<UUID, PlayerData> data = mySQLDatabase.loadData();
                    diamondCount.clear();
                    diamondCount.putAll(data);
                    logger.info("MySQL から読み込み完了。");
                    loaded = true;
                } else {
                    logger.warning("MySQL 接続が確立されていません。SQLiteから読み込みを試みます。");
                }
            } catch (Exception e) {
                logger.severe("MySQL 読み込みエラー: " + e.getMessage() + "。SQLiteから読み込みを試みます。");
            }
        }

        if (!loaded && sqLiteDatabase != null) {
            try {
                Map<UUID, PlayerData> data = sqLiteDatabase.getData();
                diamondCount.clear();
                diamondCount.putAll(data);
                logger.info("SQLite から読み込み完了。");
            } catch (SQLException e) {
                logger.severe("SQLite 読み込みエラー: " + e.getMessage());
            }
        } else if (!loaded) {
            logger.severe("どのデータベースからも読み込みできませんでした。");
        }
    }

    public static class PlayerData {
        private final String playerName;
        private int diamondMined;

        public PlayerData(String playerName, int diamondMined) {
            this.playerName = playerName;
            this.diamondMined = diamondMined;
        }

        public String getPlayerName() {
            return playerName;
        }

        public int getDiamondMined() {
            return diamondMined;
        }

        public void setDiamondMined(int v) {
            this.diamondMined = v;
        }
    }
}
