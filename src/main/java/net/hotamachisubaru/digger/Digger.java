package net.hotamachisubaru.digger;

import net.hotamachisubaru.digger.enchant.EnchantManager;
import net.hotamachisubaru.digger.mysql.MySQLDatabase;
import net.hotamachisubaru.digger.sqlite.SQLiteDatabase;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Logger;

public class Digger extends JavaPlugin implements Listener {

    private final PluginManager pm = getServer().getPluginManager();
    private final Map<Location, UUID> placedBlocksWithUUID = new HashMap<>();
    private MySQLDatabase mySQLDatabase;
    private final SQLiteDatabase sqLiteDatabase = new SQLiteDatabase();
    private FileConfiguration dataConfig;
    private File dataFile;
    private final EnchantManager enchantManager = new EnchantManager();
    private boolean useToolMoney;
    public final Map<UUID, Boolean> scoreboardToggles = new HashMap<>();
    private static Digger instance;
    private Boolean currentSetting = null;
    public static double rewardProbability = 0.02;
    public ToolMoney toolMoney;
    private Scoreboard scoreboard;
    private Economy economy;
    private final long scoreboardUpdateInterval = 20L;
    private Objective objective;
    private final Map<Material, Integer> rewardMap = new HashMap<>();
    public final Map<UUID, PlayerData> blockCount = new HashMap<>();
    private final List<Location> placedBlocks = new ArrayList<>();
    private final List<String> worldBlacklist = new ArrayList<>();
    private Material toolType;
    private Connection connection;
    private String url;
    private String user;
    private String password;
    public final Logger logger = getLogger();


    public Digger() {
        instance = this;
    }

    public static Digger getInstance() {
        return instance;
    }

    public boolean getCurrentSetting() {
        if (currentSetting == null) {
            currentSetting = true;
        }
        return currentSetting;
    }

    @Override
    public void onEnable() {
        logger.info("整地プラグインを起動しています。データのロード中です。");
        toolMoney = new ToolMoney(getConfig(), this);// ToolMoneyインスタンスの初期化
        setupResource();
        setupDatabase();
        setupCommands();
        setupScoreboard();
        setupEconomy();
        registerEvents();
        startScoreboardUpdater();
    }

    private void setupResource() {
        saveDefaultConfig();
        saveResource("config.properties", false); // config.propertiesの保存（既に存在する場合は上書きしない）
        saveResource("player-data.yml", false);// player-data.ymlの保存（既に存在する場合は上書きしない）
        saveResource("Database.db",false);
    }

    private void setupDatabase() {
        String dbType = getConfig().getString("database.type", "yaml").toLowerCase();
        switch (dbType) {
            case "mysql":
                initializeMySQLDatabase();
                break;
            case "sqlite":
                initializeSQLiteDatabase();
                break;
            case "yaml":
            default:
                // YAMLの場合、特別な初期化は不要
                logger.info("YAMLファイルを使用してデータを保存します。");
                break;
        }
    }
    private void setupCommands() {
        Commands commandExecutor = new Commands(this, toolMoney);
        getCommand("reload").setExecutor(commandExecutor);
        getCommand("set").setExecutor(commandExecutor);
    }


    private void setupScoreboard() {
        new BukkitRunnable() {
            @Override
            public void run() {
                ScoreboardManager scoreboardManager = Bukkit.getScoreboardManager();
                if (scoreboardManager == null) {
                   logger.severe("スコアボードマネージャーを取得できませんでした。サーバーがまだ初期化されていない可能性があります。");
                    return;
                }
                scoreboard = scoreboardManager.getNewScoreboard();
                objective = scoreboard.registerNewObjective("整地の順位", "dummy", ChatColor.GREEN + "あなたの順位");
                objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
        }.runTaskLater(this, 40L); // 2秒後にスコアボードの初期化を試みる
    }


    private void registerEvents() {
        pm.registerEvents(this, this);
    }

    private void initializeMySQLDatabase() {
        try {
            if (mySQLDatabase == null) {
                Properties prop = new Properties();
                File configFile = new File(getDataFolder(), "config.properties");
                prop.load(new FileInputStream(configFile));
                mySQLDatabase = new MySQLDatabase(prop);
            }

            if (!mySQLDatabase.connect()) {
                logger.severe("MySQLデータベースへの接続に失敗しました。");
            } else {
                logger.info("MySQLデータベースに正常に接続しました。");
            }
        } catch (IOException e) {
            logger.severe("config.properties ファイルの読み込みに失敗しました: " + e.getMessage());
            pm.disablePlugin(this);
        }
    }

    private void initializeSQLiteDatabase() {
        try {
            sqLiteDatabase.openConnection(getDataFolder().getAbsolutePath());
            logger.info("SQLiteデータベースに正常に接続しました。");
        } catch (SQLException e) {
            logger.severe("SQLiteデータベースの初期化に失敗しました: " + e.getMessage());
            pm.disablePlugin(this);
        }
    }

    private void loadPropertiesConfig() {
        File configFile = new File(getDataFolder(), "config.properties");
        Properties prop = new Properties();

        if (!configFile.exists()) {
            createDefaultConfigFile(configFile, prop);
        }

        try (FileInputStream inputStream = new FileInputStream(configFile)) {
            prop.load(inputStream);
        } catch (IOException e) {
            logger.severe("config.properties ファイルの読み込みに失敗しました: " + e.getMessage());
            pm.disablePlugin(this);
        }
    }

    private void createDefaultConfigFile(File configFile, Properties prop) {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();
            prop.setProperty("db.url", "jdbc:mysql://localhost:3306/yourdatabase");
            prop.setProperty("db.user", "username");
            prop.setProperty("db.password", "password");
            prop.store(new FileWriter(configFile), "Database Configurations");
        } catch (IOException e) {
            logger.severe("config.properties ファイルの生成に失敗しました: " + e.getMessage());
            pm.disablePlugin(this);
        }
    }

    private boolean setupEconomy() {
        if (pm.getPlugin("Vault") == null) {
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

    private void startScoreboardUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateAllPlayersScoreboard();
            }
        }.runTaskTimer(this, 20L, scoreboardUpdateInterval);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーからのみ実行できます。");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("reload")) {
            return handleReloadCommand(player);
        }
        return false;
    }

    private boolean handleReloadCommand(Player player) {
        if (!player.hasPermission("digger.reload")) {
            player.sendMessage("§cあなたにはこのコマンドを実行する権限がありません。");
            return true;
        }

        this.reloadConfig();
        Digger.rewardProbability = this.getConfig().getDouble("rewardProbability", 0.02);
        player.sendMessage("§a config.ymlを再読み込みしました。");
        return true;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location loc = event.getBlock().getLocation();
        placedBlocksWithUUID.put(loc, playerId);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (worldBlacklist.contains(world.getName()) ||
                placedBlocks.remove(event.getBlock().getLocation()) ||
                isBlockBlacklisted(event.getBlock().getType())) {
            return;
        }

        updateBlockCount(player);
        giveReward(player);
        enchantManager.applyEfficiencyEnchant(player, getBlocksMined(player));
    }

    private int getBlocksMined(Player player) {
        UUID playerID = player.getUniqueId();
        PlayerData data = blockCount.get(playerID);
        return data != null ? data.getBlocksMined() : 0;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        saveData();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        saveData();
        loadData();
    }

    public void updateBlockCount(Player player) {
        UUID playerID = player.getUniqueId();
        PlayerData data = blockCount.getOrDefault(playerID, new PlayerData(player.getName(), 0));
        data.setBlocksMined(data.getBlocksMined() + 1);
        blockCount.put(playerID, data);
    }

    private void giveReward(Player player) {
        Material toolType = player.getInventory().getItemInMainHand().getType();
        Integer toolReward = rewardMap.getOrDefault(toolType, 50);
        if (Math.random() < rewardProbability) {
            economy.depositPlayer(player, toolReward);
            player.sendMessage("§a " + toolReward + "円を手に入れました。");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        }
    }

    private boolean isBlockBlacklisted(Material material) {
        List<String> blacklist = getConfig().getStringList("block-blacklist");
        return blacklist.contains(material.name());
    }

    public void updateAllPlayersScoreboard() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ScoreboardHandler.updateScoreboard(player);
        }
    }

    @Override
    public void onDisable() {
       logger.info("[ほたまち]整地プラグインを終了しています。データ保存をしていますのでサーバーを強制終了しないでください。");
        saveData();
        getConfig().set("update-interval", scoreboardUpdateInterval);
        getConfig().set("world-blacklist", worldBlacklist);
        saveConfig();
    }

    public void loadData() {
        String dbType = getConfig().getString("database.type", "yaml").toLowerCase();
        switch (dbType) {
            case "mysql":
                loadFromMySQL();
                break;
            case "sqlite":
                loadFromSQLite();
                break;
            case "yaml":
            default:
                loadFromYAML();
                break;
        }
    }

    private void loadFromSQLite() {
        try {
            if (sqLiteDatabase.checkConnection()) {
                Map<UUID, PlayerData> dataFromDatabase = sqLiteDatabase.getData();
                if (dataFromDatabase != null) {
                    blockCount.clear();
                    blockCount.putAll(dataFromDatabase);
                    logger.info("データがSQLiteデータベースから読み込まれました。");
                } else {
                    logger.warning("SQLiteデータベースからのデータが空です。");
                }
            } else {
                logger.warning("SQLiteデータベースへの接続が確立されていません。");
            }
        } catch (SQLException e) {
            logger.severe("SQLiteデータベースからの読み込み中にエラーが発生しました: " + e.getMessage());
        }
    }


    private void loadFromMySQL() {
        try {
            if (mySQLDatabase.isConnected()) {
                Map<UUID, PlayerData> dataFromDatabase = mySQLDatabase.loadData();
                if (dataFromDatabase != null && !dataFromDatabase.isEmpty()) {
                    blockCount.clear();
                    blockCount.putAll(dataFromDatabase);
                    logger.info("データがMySQLデータベースから読み込まれました。");
                } else {
                   logger.warning("MySQLデータベースからのデータが空です。");
                }
            } else {
                logger.warning("MySQLデータベースへの接続が確立されていません。");
            }
        } catch (Exception e) {
            logger.severe("MySQLデータベースからの読み込み中にエラーが発生しました: " + e.getMessage());
        }
    }



    private void loadFromYAML() {
        dataFile = new File(getDataFolder(), "player-data.yml");
        if (!dataFile.exists()) {
           logger.info("player-data.ymlファイルが見つかりませんでした。新しく作成します。");
            saveResource("player-data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.contains("blockCount")) {
            blockCount.clear();
            for (String uuidString : dataConfig.getConfigurationSection("blockCount").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                String playerName = dataConfig.getString("blockCount." + uuidString + ".playerName");
                int blocksMined = dataConfig.getInt("blockCount." + uuidString + ".blocksMined");
                blockCount.put(uuid, new PlayerData(playerName, blocksMined));
            }
           logger.info("データをyamlファイルから読み込みました。");
        }
    }

    public void saveData() {
        String dbType = getConfig().getString("database.type", "yaml").toLowerCase();
        switch (dbType) {
            case "mysql":
                saveToMySQL();
                break;
            case "sqlite":
                saveToSQLite();
                break;
            case "yaml":
            default:
                saveToYAML();
                break;
        }
    }
    private boolean saveToMySQL() {
        try {
            if (mySQLDatabase.isConnected()) {
                mySQLDatabase.savePlayerData(blockCount, placedBlocks, placedBlocksWithUUID);
                logger.info("データをMySQLデータベースに保存しました。");
                return true;
            }
        } catch (Exception e) {
            logger.warning("MySQLデータベースへの保存に失敗しました: " + e.getMessage());
        }
        return false;
    }

    private boolean saveToSQLite() {
        try {
            sqLiteDatabase.saveData(blockCount, placedBlocks);
           logger.info("データをSQLiteデータベースに保存しました。");
            return true;
        } catch (SQLException e) {
            logger.severe("SQLiteデータベースへの保存に失敗しました: " + e.getMessage());
            return false;
        }
    }


    private void saveToYAML() {
        if (dataConfig != null) {
            for (Map.Entry<UUID, PlayerData> entry : blockCount.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerData playerData = entry.getValue();
                dataConfig.set("blockCount." + uuid.toString() + ".blocksMined", playerData.getBlocksMined());
                dataConfig.set("blockCount." + uuid + ".playerName", playerData.getPlayerName());
            }
            try {
                dataConfig.save(dataFile);
                logger.info("データをYAMLファイルに保存しました。");
            } catch (IOException e) {
                logger.severe("YAMLファイルの保存中にエラーが発生しました: " + e.getMessage());
            }
        }
    }

    public static class PlayerData {
        private String playerName;
        private int blocksMined;

        public PlayerData(String playerName, int blocksMined) {
            this.playerName = playerName;
            this.blocksMined = blocksMined;
        }

        public String getPlayerName() {
            return playerName;
        }

        public void setPlayerName(String playerName) {
            this.playerName = playerName;
        }

        public int getBlocksMined() {
            return blocksMined;
        }

        public void setBlocksMined(int blocksMined) {
            this.blocksMined = blocksMined;
        }
    }
}
