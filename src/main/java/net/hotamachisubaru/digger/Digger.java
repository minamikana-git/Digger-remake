package net.hotamachisubaru.digger;

import net.hotamachisubaru.digger.enchant.EnchantManager;
import net.hotamachisubaru.digger.mysql.MySQLDatabase;
import net.hotamachisubaru.digger.sqlite.SQLiteDatabase;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class Digger extends JavaPlugin implements Listener {

    private final Map<Location, UUID> placedBlocksWithUUID = new HashMap<>();


    private MySQLDatabase mySQLDatabase;
    private SQLiteDatabase sqLiteDatabase = new SQLiteDatabase();
    private FileConfiguration dataConfig;
    private File dataFile;

    private final EnchantManager enchantManager = new EnchantManager();

    public boolean isToolRewardEnabled = true;

    private boolean useToolMoney;

    private final Map<UUID, Boolean> scoreboardToggles = new HashMap<>();
    private static Digger instance;
    private Boolean currentSetting = null;

    public static double rewardProbability = 0.02;


    public ToolMoney toolMoney = new ToolMoney(getConfig(), this);

    private Scoreboard scoreboard;
    private Economy economy;
    private long scoreboardUpdateInterval = 20L;
    private Objective objective;
    private final Map<Material, Integer> rewardMap = new HashMap<>();
    public final Map<UUID, PlayerData> blockCount = new HashMap<>();


    private final List<Location> placedBlocks = new ArrayList<>();
    private List<String> worldBlacklist = new ArrayList<>();
    private Material toolType;
    private Connection connection;
    private String url;
    private String user;
    private String password;


    public Digger() {
        instance = this;
    }

    public static Digger getInstance() {
        return instance;
    }

    public boolean getCurrentSetting() {
        if (currentSetting == null) {
            currentSetting = Digger.getInstance().isToolRewardEnabled;
        }
        return currentSetting;
    }

    @Override
    public void onEnable() {
        getLogger().info("[ほたまち]整地プラグインを起動しています。データのロード中です。");
        saveDefaultConfig();
        Properties prop = new Properties();

        // config.properties ファイルの処理
        File configFile = new File(getDataFolder(), "config.properties");
        if (!configFile.exists()) {
            try {
                configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                // デフォルト設定を書き込む（必要に応じて設定値を追加）
                prop.setProperty("db.url", "jdbc:mysql://localhost:3306/yourdatabase");
                prop.setProperty("db.user", "username");
                prop.setProperty("db.password", "password");
                prop.store(new FileWriter(configFile), "Database Configurations");
            } catch (IOException e) {
                getLogger().severe("config.properties ファイルの生成に失敗しました: " + e.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        try {
            prop.load(new FileInputStream(configFile));
            // MySQLDatabase インスタンスの初期化
            mySQLDatabase = new MySQLDatabase(prop);
            if (!mySQLDatabase.connect()) {
                getLogger().severe("MySQLデータベースへの接続に失敗しました。");
            }
        } catch (IOException e) {
            getLogger().severe("config.properties ファイルの読み込みに失敗しました: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
        // MySQLDatabase の初期化
        if (!mySQLDatabase.connect()) {
            getLogger().severe("MySQLデータベースへの接続に失敗しました。");
        }

        // PointsDatabase の初期化

        try {
            sqLiteDatabase.openConnection(getDataFolder().getAbsolutePath());
        } catch (SQLException e) {
            getLogger().severe("SQLiteデータベースの初期化に失敗しました: " + e.getMessage());
        }


        Digger.rewardProbability = this.getConfig().getDouble("rewardProbability", 0.02); //2%

        ToolMoney toolMoneyInstance = new ToolMoney(getConfig(), this);
        Commands commandExecutor = new Commands(this, toolMoneyInstance);

        getCommand("reload").setExecutor(commandExecutor);
        getCommand("set").setExecutor(commandExecutor);
        startScoreboardUpdater();
        if (this.getConfig().contains("scoreboardUpdateInterval")) {
            scoreboardUpdateInterval = this.getConfig().getLong("scoreboardUpdateInterval");
            useToolMoney = getConfig().getBoolean("use-tool-money", false);
            new BukkitRunnable() {
                @Override
                public void run() {
                    // スコアボードの初期化
                    scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
                    objective = scoreboard.registerNewObjective("整地の順位", "dummy", "あなたの順位");
                    objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                    loadData(); // player-data.ymlの中身を読み込む。

                }
            }.runTaskLater(this, 20L); //1秒遅延（20tick=1秒）


        }

        loadToolRewards();
        rewardMap.put(Material.NETHERITE_PICKAXE, 250);
        rewardMap.put(Material.NETHERITE_SHOVEL, 250);
        rewardMap.put(Material.DIAMOND_PICKAXE, 200);
        rewardMap.put(Material.DIAMOND_SHOVEL, 200);
        rewardMap.put(Material.GOLDEN_PICKAXE, 175);
        rewardMap.put(Material.GOLDEN_SHOVEL, 175);
        rewardMap.put(Material.IRON_PICKAXE, 150);
        rewardMap.put(Material.IRON_SHOVEL, 150);
        rewardMap.put(Material.STONE_PICKAXE, 100);
        rewardMap.put(Material.STONE_SHOVEL, 100);
        rewardMap.put(Material.WOODEN_PICKAXE, 50);
        rewardMap.put(Material.WOODEN_SHOVEL, 50);
        FileConfiguration dataConfig;
        dataFile = new File(getDataFolder(), "config.properties");
        dataFile = new File(getDataFolder(), "player-data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            saveResource("player-data.yml", false);
            saveResource("config.properties", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        configFile = new File(getDataFolder(), "config.yml");

        reloadConfig();  // すでに存在する config.yml の内容を読み込む

        toolMoney = new ToolMoney(getConfig(), this);

        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(dataFile);
        this.dataConfig = yamlConfiguration;
        scoreboardUpdateInterval = getConfig().getLong("update-interval", scoreboardUpdateInterval);
        worldBlacklist = getConfig().getStringList("world-blacklist");

        if (!setupEconomy()) { // 起動時のVault関係があるかどうか
            getLogger().severe("エラー：Vaultプラグインが見つかりませんでした。プラグインを無効化します。");

            if (getServer().getPluginManager().getPlugin("Vault") == null) {
                getLogger().severe("エラー：Vaultプラグインが見つかりません。");
            } else {
                getLogger().severe("エラー：Economyサービスプロバイダが見つかりません。");
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.getServer().getPluginManager().registerEvents(this, this);


    }


    // データの保存
    public void savePlayerData(Map<UUID, Integer> blockCount) {
        Map<UUID, PlayerData> playerDataMap = new HashMap<>();
        for (Map.Entry<UUID, Integer> entry : blockCount.entrySet()) {
            // プレイヤーのUUIDを取得
            UUID playerId = entry.getKey();
            // プレイヤーの名前を取得
            Player player = Bukkit.getPlayer(playerId);
            String playerName = (player != null) ? player.getName() : "Unknown";
            // PlayerDataオブジェクトを作成
            playerDataMap.put(playerId, new PlayerData(playerName, entry.getValue()));
        }


        // データを保存する
        try {
            sqLiteDatabase.saveData(playerDataMap, placedBlocks); // playerDataMapを使用
        } catch (SQLException e) {
            // エラーハンドリング
            getLogger().severe("データベースへの保存中にエラーが発生しました。YAMLファイルに変更しています: " + e.getMessage());
            saveToYaml(blockCount, placedBlocks);
        }

    }

    private void saveToYaml(Map<UUID, Integer> blockCount, List<Location> placedBlocks) {
        // blockCount の保存
        for (Map.Entry<UUID, Integer> entry : blockCount.entrySet()) {
            dataConfig.set("blockCount." + entry.getKey().toString(), entry.getValue());
        }

        // placedBlocks の保存 (リスト内の各 Location オブジェクトを文字列に変換して保存)
        List<String> blockLocStrings = placedBlocks.stream()
                .map(loc -> loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ())
                .collect(Collectors.toList());
        dataConfig.set("placedBlocks", blockLocStrings);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("YAMLファイルへの保存中にエラーが発生しました: " + e.getMessage());
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("§4エラー：Vaultプラグインが見つかりませんでした。");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("§4エラー:Economyサービスプロバイダが登録されていません。");
            return false;
        }
        economy = rsp.getProvider();
        if (economy == null) {
            getLogger().warning("§4エラー：Economyサービスが見つかりません。");
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
        }.runTaskTimer(this, 20L, scoreboardUpdateInterval);  // 開始は1秒後、その後は指定された間隔で更新
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーからのみ実行できます。");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("reload")) {
            if (cmd.getName().equalsIgnoreCase("reload")) {
                if (!player.hasPermission("digger.reload")) {
                    player.sendMessage("§cあなたにはこのコマンドを実行する権限がありません。");
                    return true;
                }

                this.reloadConfig();
                Digger.rewardProbability = this.getConfig().getDouble("rewardProbability", 0.02);
                player.sendMessage("§a config.ymlを再読み込みしました。");
                return true;
            }
            return true;
        }
        return false;
    }

    public void updateScoreboard(Player viewingPlayer) {

        Boolean showScoreboard = scoreboardToggles.getOrDefault(viewingPlayer.getUniqueId(), true);
        if (showScoreboard) {

            // スコアボードのセットアップ
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective("stats", "dummy", ChatColor.GREEN + "整地の順位");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);


// ソートされたリストを取得
            List<Map.Entry<UUID, PlayerData>> sortedList = blockCount.entrySet().stream()
                    .sorted((entry1, entry2) -> Integer.compare(entry2.getValue().getBlocksMined(), entry1.getValue().getBlocksMined()))
                    .limit(10)
                    .collect(Collectors.toList());


            // 空行
            objective.getScore(" ").setScore(1);


            // プレイヤーのランクを決定
            PlayerData defaultData = new PlayerData("", 0);
            PlayerData playerData = blockCount.getOrDefault(viewingPlayer.getUniqueId(), defaultData);
            int viewerScore = playerData.getBlocksMined();
            int viewerRankIndex = sortedList.stream()
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList())
                    .indexOf(viewingPlayer.getUniqueId());
            String rankDisplayText = viewerRankIndex < 0 || viewerRankIndex >= 10 ? "ランキング外" : (viewerRankIndex + 1) + "位";

            // トップ10プレイヤーをスコアボードに表示
            for (int i = 0; i < sortedList.size(); i++) {
                Map.Entry<UUID, PlayerData> entry = sortedList.get(i);
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


    private void saveUpdateIntervalToConfig(long interval) {
        this.getConfig().set("scoreboardUpdateInterval", interval);
        this.saveConfig();
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
        int before = data.getBlocksMined(); // 更新前のカウント
        data.setBlocksMined(data.getBlocksMined() + 1);
        blockCount.put(playerID, data);
    }


    private void giveReward(Player player) {
        Material toolType = player.getInventory().getItemInMainHand().getType();
        Integer toolReward = isToolRewardEnabled ? rewardMap.getOrDefault(toolType, 50) : 50;
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
        // すべてのプレイヤー（オンライン・オフライン）のUUIDを使用してスコアボードを更新
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateScoreboard(player);
        }
    }


    @Override
    public void onDisable() {
        getLogger().info("[ほたまち]整地プラグインを終了しています。データ保存をしていますのでサーバーを強制終了しないでください。");
        try {
            if (mySQLDatabase != null && mySQLDatabase.isConnected()) {
                saveData();
            }
        } catch (Exception e) {
            getLogger().severe("データの保存中にエラーが発生しました: " + e.getMessage());
        }
        saveData();
        getConfig().set("update-interval", scoreboardUpdateInterval);
        getConfig().set("world-blacklist", worldBlacklist);
        saveConfig();

    }

    public void loadData() {
        try {
            Map<UUID, PlayerData> dataFromDatabase = mySQLDatabase.loadData();
            if (!dataFromDatabase.isEmpty()) {
                blockCount.clear();
                blockCount.putAll(dataFromDatabase);
                getLogger().info("データがMySQLデータベースから読み込まれました。");
                return;
            }
        } catch (Exception e) {
            getLogger().warning("MySQLデータベースからの読み込みに失敗しました: " + e.getMessage());
        }
        // エラーが発生した場合はSQLiteからの読み込みに変更
        // SQLiteデータベースから読み込む
        try {
            // データベース接続の確認とデータの取得
            if (SQLiteDatabase.checkConnection()) {
                Map<UUID, PlayerData> dataFromDatabase = SQLiteDatabase.getData();
                if (dataFromDatabase != null) {
                    blockCount.clear();
                    blockCount.putAll(dataFromDatabase);
                    getLogger().info("データがデータベースから読み込まれました。");
                    return;
                }
            }
        } catch (SQLException e) {
            getLogger().severe("データベースからの読み込み中にエラーが発生しました: " + e.getMessage());
            // それでもエラーが発生した場合はYAMLからの読み込みに変更
        }

        // YAMLファイルからの読み込みに変更
        dataFile = new File(getDataFolder(), "player-data.yml");
        if (!dataFile.exists()) {
            getLogger().info("player-data.ymlファイルが見つかりませんでした。新しく作成します。");
            saveResource("player-data.yml", false);
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        if (dataConfig.contains("blockCount")) {
            blockCount.clear();
            for (String uuidString : dataConfig.getConfigurationSection("blockCount").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                String playerName = dataConfig.getString("blockCount." + uuidString + ".playerName");
                int blocksMined = dataConfig.getInt("blockCount." + uuidString + ".blocksMined");
                PlayerData playerData = new PlayerData(playerName, blocksMined);
                blockCount.put(uuid, playerData);
            }
            getLogger().info("データをyamlファイルから読み込みました。");
        }
    }


    private Location stringToLocation(String s) {
        String[] parts = s.split(",");
        World world = Bukkit.getWorld(parts[0]);
        int x = Integer.parseInt(parts[1]);
        int y = Integer.parseInt(parts[2]);
        int z = Integer.parseInt(parts[3]);
        return new Location(world, x, y, z);
    }


    // データベースへの保存処理
    public void savePlacedBlocks() {
        for (Map.Entry<Location, UUID> entry : placedBlocksWithUUID.entrySet()) {
            Location loc = entry.getKey();
            UUID playerId = entry.getValue();

            // ここでデータベース保存処理を行う
            mySQLDatabase.savePlacedBlock(playerId, loc);
        }
    }

    public void saveData() {
        boolean mysqlSaved = saveToMySQL();
        boolean sqliteSaved = saveToSQLite();
        boolean yamlSaved = true;

        if (!mysqlSaved) {
            getLogger().warning("MySQLデータベースへの保存に失敗しました。");
        }
        if (!sqliteSaved) {
            getLogger().warning("SQLiteデータベースへの保存に失敗しました。");
            try {
                saveToYAML();
                if (!yamlSaved) {
                    getLogger().severe("YAMLファイルへの保存に失敗しました。");
                }
            } catch (IOException e) {
                getLogger().severe("YAMLファイルの保存中にエラーが発生しました: " + e.getMessage());
            }

        }
    }

    private boolean saveToMySQL() {
        try {
            if (mySQLDatabase.isConnected()) {
                mySQLDatabase.savePlayerData(blockCount, placedBlocks, placedBlocksWithUUID);
                getLogger().info("データをMySQLデータベースに保存しました。");
                return true;
            } else {
                throw new SQLException("MySQLデータベースに接続できませんでした。");
            }
        } catch (Exception e) {
            getLogger().warning("MySQLデータベースへの保存に失敗しました: " + e.getMessage());
            return false;
        }
    }

    private boolean saveToSQLite() {
        try {
           SQLiteDatabase.saveData(blockCount, placedBlocks);
            getLogger().info("データをSQLiteデータベースに保存しました。");
            return true;
        } catch (SQLException e) {
            getLogger().severe("SQLiteデータベースへの保存に失敗しました: " + e.getMessage());
            return false;
        }
    }


    private void saveToYAML() throws IOException {
        if (dataConfig != null) {
            for (Map.Entry<UUID, PlayerData> entry : blockCount.entrySet()) {
                UUID uuid = entry.getKey();
                PlayerData playerData = entry.getValue();
                dataConfig.set("blockCount." + uuid.toString() + ".blocksMined", playerData.getBlocksMined());
                dataConfig.set("blockCount." + uuid + ".playerName", playerData.getPlayerName());
            }

            // placedBlocks の保存
            List<String> blockLocStrings = placedBlocks.stream()
                    .map(loc -> loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ())
                    .collect(Collectors.toList());
            dataConfig.set("placedBlocks", blockLocStrings);


            dataConfig.save(dataFile);
            getLogger().info("データをYAMLファイルに保存しました。");
        }
    }


    private String locationToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void loadToolRewards() {
        // "tools"セクションを取得
        ConfigurationSection toolsSection = getConfig().getConfigurationSection("tools");

        if (toolsSection == null) {

            return;
        }

        // セクション内の全てのキーと値を取得してrewardMapに保存
        for (String key : toolsSection.getKeys(false)) {
            Material material = Material.getMaterial(key);
            int reward = toolsSection.getInt(key);

            if (material != null) {
                rewardMap.put(material, reward);
            } else {

            }
        }
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            getLogger().severe("データベース接続に失敗しました: " + e.getMessage());
            return null;
        }
    }


    public void setConnection(Connection connection) {
        this.connection = connection;
    }


    public static class PlayerData {
        private String playerName;
        private int blocksMined;

        public PlayerData(String playerName, int blocksMined) {
            this.playerName = playerName;
            this.blocksMined = blocksMined;
        }

        // ゲッターとセッターを追加
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


















