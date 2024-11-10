package net.hotamachisubaru.digger.mysql;
import net.hotamachisubaru.digger.Digger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;

public class MySQLDatabase {
    private String url;
    private String user;
    private String password;

    public MySQLDatabase(Properties prop) {
        try {
            prop.load(new FileInputStream(Digger.getInstance().getDataFolder().getAbsolutePath() + "/config.properties"));
            this.url = prop.getProperty("db.url");
            this.user = prop.getProperty("db.user");
            this.password = prop.getProperty("db.password");
            initializeDatabase();
        } catch (IOException e) {
            Bukkit.getLogger().severe("MySQLデータベースに接続できませんでした。");
            // エラーハンドリング...
        }
    }

    private void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // player_data テーブルの作成
            String playerDataTable = "CREATE TABLE IF NOT EXISTS digger_player_data ("
                    + "UUID CHAR(36) PRIMARY KEY,"
                    + "PlayerName VARCHAR(255),"
                    + "BlocksMined INT);";
            stmt.execute(playerDataTable);

            // placed_blocks テーブルの作成
            String placedBlocksTable = "CREATE TABLE IF NOT EXISTS digger_placed_blocks ("
                    + "UUID CHAR(36),"
                    + "World VARCHAR(255),"
                    + "X INT,"
                    + "Y INT,"
                    + "Z INT,"
                    + "FOREIGN KEY(UUID) REFERENCES digger_player_data(UUID));";
            stmt.execute(placedBlocksTable);
        } catch (SQLException e) {
            Bukkit.getLogger().severe("MySQLデータベースに保存できませんでした。接続を確認してください。");
            // エラーハンドリング...
        }
    }

    private Connection getConnection() throws SQLException {
        // MySQLデータベースへの接続
        String fullUrl = url + "?createDatabaseIfNotExist=true";
        return DriverManager.getConnection(fullUrl, user, password);
    }


    private Connection getSQLiteConnection() throws SQLException {
        // プラグインのデータフォルダ内にデータベースファイルを保存する
        String fullUrl = url + "?createDatabaseIfNotExist=true";
        String sqliteUrl = "jdbc:sqlite:" + Digger.getInstance().getDataFolder().getAbsolutePath() + "/Database.db";
        return DriverManager.getConnection(sqliteUrl);
    }





    public void savePlayerData(Map<UUID, Digger.PlayerData> blockCount, List<Location> placedBlocks, Map<Location, UUID> placedBlocksWithUUID) {
        // プレイヤーデータの保存
        String playerDataQuery = "INSERT INTO digger_player_data (UUID, PlayerName, BlocksMined) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE BlocksMined = ?;";
        for (Map.Entry<UUID, Digger.PlayerData> entry : blockCount.entrySet()) {
            UUID playerId = entry.getKey();
            Digger.PlayerData playerData = entry.getValue();

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(playerDataQuery)) {

                stmt.setString(1, playerId.toString());
                stmt.setString(2, playerData.getPlayerName());
                stmt.setInt(3, playerData.getBlocksMined());
                stmt.setInt(4, playerData.getBlocksMined());

                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        // placedBlocks の保存
        String placedBlocksQuery = "INSERT INTO digger_placed_blocks (UUID, World, X, Y, Z) VALUES (?, ?, ?, ?, ?);";

        for (Location loc : placedBlocks) {
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(placedBlocksQuery)) {

                UUID playerId = placedBlocksWithUUID.get(loc); // ここでUUIDを取得

                stmt.setString(1, playerId.toString());
                stmt.setString(2, loc.getWorld().getName());
                stmt.setInt(3, loc.getBlockX());
                stmt.setInt(4, loc.getBlockY());
                stmt.setInt(5, loc.getBlockZ());

                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean connect() {
        try (Connection conn = getConnection()) {
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean isConnected() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public Map<UUID, Digger.PlayerData> loadData() {
        Map<UUID, Digger.PlayerData> dataMap = new HashMap<>();
        String query = "SELECT * FROM digger_player_data;"; // SQLクエリ

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("UUID"));
                String playerName = rs.getString("PlayerName");
                int blocksMined = rs.getInt("BlocksMined");

                dataMap.put(uuid, new Digger.PlayerData(playerName, blocksMined));
            }
        } catch (SQLException e) {
            e.printStackTrace(); // エラーログを出力
        }
        return dataMap;
    }


    public void savePlacedBlock(UUID playerName, Location loc) {
        String insertQuery = "INSERT INTO digger_placed_blocks (World, X, Y, Z) VALUES (?, ?, ?, ?);";

        try (Connection conn = getSQLiteConnection(); // SQLiteデータベースへの接続を取得
             PreparedStatement stmt = conn.prepareStatement(insertQuery)) {

            stmt.setString(1, loc.getWorld().getName());
            stmt.setInt(2, loc.getBlockX());
            stmt.setInt(3, loc.getBlockY());
            stmt.setInt(4, loc.getBlockZ());

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}