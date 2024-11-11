package net.hotamachisubaru.digger.mysql;

import net.hotamachisubaru.digger.Digger;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class MySQLDatabase {
    // 設定ファイルパスとSQLテーブル作成コマンドの定数
    private static final String CONFIG_FILE_PATH = "/config.properties";
    private static final String PLAYER_DATA_TABLE = "CREATE TABLE IF NOT EXISTS digger_player_data ("
            + "UUID CHAR(36) PRIMARY KEY,"
            + "PlayerName VARCHAR(255),"
            + "BlocksMined INT);";
    private static final String PLACED_BLOCKS_TABLE = "CREATE TABLE IF NOT EXISTS digger_placed_blocks ("
            + "UUID CHAR(36),"
            + "World VARCHAR(255),"
            + "X INT,"
            + "Y INT,"
            + "Z INT,"
            + "FOREIGN KEY(UUID) REFERENCES digger_player_data(UUID));";

    // データベース接続のパラメータ
    private String url = "";
    private String user = "";
    private String password = "";

    // コンストラクタでMySQLDatabaseを初期化
    public MySQLDatabase(Properties prop) {
        try {
            // 設定ファイルからプロパティを読み込む
            prop.load(new FileInputStream(Digger.getInstance().getDataFolder().getAbsolutePath() + CONFIG_FILE_PATH));
            this.url = prop.getProperty("db.url", "");
            this.user = prop.getProperty("db.user", "");
            this.password = prop.getProperty("db.password", "");
            initializeDatabase();
        } catch (IOException e) {
            // 設定ファイルが読み込めなかった場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースに接続できませんでした。" + e.getMessage());
        }
    }

    // データベースを初期化し、必要なテーブルを作成
    private void initializeDatabase() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // プレイヤーデータテーブルの作成
            stmt.execute(PLAYER_DATA_TABLE);
            // 配置されたブロックテーブルの作成
            stmt.execute(PLACED_BLOCKS_TABLE);
        } catch (SQLException e) {
            // データベースの初期化が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースに保存できませんでした。接続を確認してください。" + e.getMessage());
        }
    }

    // MySQLデータベースへの接続を確立
    private Connection getConnection() throws SQLException {
        // データベースが存在しない場合は作成するパラメータを追加
        String fullUrl = url + "?createDatabaseIfNotExist=true";
        return DriverManager.getConnection(fullUrl, user, password);
    }

    // プレイヤーデータと配置されたブロックデータを保存
    public void savePlayerData(Map<UUID, Digger.PlayerData> blockCount, List<Location> placedBlocks, Map<Location, UUID> placedBlocksWithUUID) {
        savePlayerDataEntries(blockCount);
        savePlacedBlocksEntries(placedBlocks, placedBlocksWithUUID);
    }

    // プレイヤーデータエントリをデータベースに保存
    private void savePlayerDataEntries(Map<UUID, Digger.PlayerData> blockCount) {
        String playerDataQuery = "INSERT INTO digger_player_data (UUID, PlayerName, BlocksMined) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE BlocksMined = ?;";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(playerDataQuery)) {
            for (Map.Entry<UUID, Digger.PlayerData> entry : blockCount.entrySet()) {
                // SQLステートメントのパラメータを設定
                stmt.setString(1, entry.getKey().toString());
                stmt.setString(2, entry.getValue().getPlayerName());
                stmt.setInt(3, entry.getValue().getBlocksMined());
                stmt.setInt(4, entry.getValue().getBlocksMined());
                // 更新を実行
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            // プレイヤーデータの保存が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースに保存に失敗しました。" + e.getMessage());
        }
    }

    // 配置されたブロックエントリをデータベースに保存
    private void savePlacedBlocksEntries(List<Location> placedBlocks, Map<Location, UUID> placedBlocksWithUUID) {
        String placedBlocksQuery = "INSERT INTO digger_placed_blocks (UUID, World, X, Y, Z) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(placedBlocksQuery)) {
            for (Location loc : placedBlocks) {
                UUID playerId = placedBlocksWithUUID.get(loc);
                // SQLステートメントのパラメータを設定
                stmt.setString(1, playerId.toString());
                stmt.setString(2, loc.getWorld().getName());
                stmt.setInt(3, loc.getBlockX());
                stmt.setInt(4, loc.getBlockY());
                stmt.setInt(5, loc.getBlockZ());
                // 更新を実行
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            // 配置されたブロックデータの保存が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースに保存に失敗しました。" + e.getMessage());
        }
    }

    // MySQLデータベースへの接続をテスト
    public boolean connect() {
        try (Connection conn = getConnection()) {
            return true;
        } catch (SQLException e) {
            // データベースへの接続が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースへ接続に失敗しました。" + e.getMessage());
            return false;
        }
    }

    // MySQLデータベースへの接続がアクティブかどうかを確認
    public boolean isConnected() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // データベースからプレイヤーデータを読み込む
    public Map<UUID, Digger.PlayerData> loadData() {
        Map<UUID, Digger.PlayerData> dataMap = new HashMap<>();
        String query = "SELECT * FROM digger_player_data;";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                // 結果セットからデータを抽出
                UUID uuid = UUID.fromString(rs.getString("UUID"));
                String playerName = rs.getString("PlayerName");
                int blocksMined = rs.getInt("BlocksMined");
                // データをマップに追加
                dataMap.put(uuid, new Digger.PlayerData(playerName, blocksMined));
            }
        } catch (SQLException e) {
            // データベースからのデータ読み込みが失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースからの読み込みに失敗しました。" + e.getMessage());
        }
        return dataMap;
    }

    // データベースに単一の配置ブロックを保存
    public void savePlacedBlock(UUID playerUUID, Location loc) {
        String insertQuery = "INSERT INTO digger_placed_blocks (UUID, World, X, Y, Z) VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
            // SQLステートメントのパラメータを設定
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, loc.getWorld().getName());
            stmt.setInt(3, loc.getBlockX());
            stmt.setInt(4, loc.getBlockY());
            stmt.setInt(5, loc.getBlockZ());
            // 更新を実行
            stmt.executeUpdate();
        } catch (SQLException e) {
            // 配置ブロックデータの保存が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースに保存に失敗しました。" + e.getMessage());
        }
    }
}
