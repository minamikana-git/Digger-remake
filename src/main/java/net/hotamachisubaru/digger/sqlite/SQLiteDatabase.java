package net.hotamachisubaru.digger.sqlite;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import net.hotamachisubaru.digger.Digger;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SQLiteDatabase{
    private Connection connection;

    public void openConnection(String path) throws SQLException {
        // データベース接続の確立
        String url = "jdbc:sqlite:" + path + "/Database.db";
        connection = DriverManager.getConnection(url);

        // テーブルの作成
        try (Statement statement = connection.createStatement()) {
            String playerDataTableCreationQuery = "CREATE TABLE IF NOT EXISTS player_data ("
                    + "UUID VARCHAR(255) NOT NULL,"
                    + "PlayerName VARCHAR(255),"
                    + "BlocksMined INT DEFAULT 0,"
                    + "PRIMARY KEY (UUID));";
            statement.execute(playerDataTableCreationQuery);

            String placedBlocksTableCreationQuery = "CREATE TABLE IF NOT EXISTS placed_blocks ("
                    + "UUID INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "World VARCHAR(255),"
                    + "X INT,"
                    + "Y INT,"
                    + "Z INT);";
            statement.execute(placedBlocksTableCreationQuery);
        }
    }

    // データベースからデータを取得するメソッド
    public static Map<UUID, Digger.PlayerData> getData() throws SQLException {
        Map<UUID, Digger.PlayerData> data = new HashMap<>();
        String query = "SELECT * FROM player_data"; // データベースのテーブル名を適宜変更してください

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                // データベースからUUIDとブロックの採掘数を取得
                String uuidString = rs.getString("UUID");
                UUID uuid = UUID.fromString(uuidString);
                String playerName = rs.getString("PlayerName");
                int blocksMined = rs.getInt("BlocksMined");

                // 取得したデータでPlayerDataオブジェクトを作成
                Digger.PlayerData playerData = new Digger.PlayerData(playerName, blocksMined);
                data.put(uuid, playerData);
            }
        }

        return data;
    }

    public Map<UUID, Digger.PlayerData> loadData() throws SQLException {
        Map<UUID, Digger.PlayerData> loadedData = new HashMap<>();
        // SQLクエリを実行し、結果を取得
        String query = "SELECT * FROM player_data;";
        try (Statement stmt = this.connection.createStatement();
             ResultSet results = stmt.executeQuery(query)) {

            while (results.next()) {
                UUID uuid = UUID.fromString(results.getString("UUID"));
                String playerName = results.getString("PlayerName");
                int blocksMined = results.getInt("BlocksMined");

                loadedData.put(uuid, new Digger.PlayerData(playerName, blocksMined));
            }
        }
        return loadedData;
    }

    public static void saveData(Map<UUID, Digger.PlayerData> blockCount, Iterable<Location> placedBlocks) throws SQLException {
        // blockCount の保存
        saveBlockCount(blockCount);


        // placedBlocks の保存
        savePlacedBlocks(placedBlocks);
    }

    private void saveBlockCount(Map<UUID, Digger.PlayerData> blockCount) throws SQLException {
        for (Map.Entry<UUID, Digger.PlayerData> entry : blockCount.entrySet()) {
            Bukkit.getLogger().info("UUIDのPlayerDataをセーブしています: " + entry.getKey() + ", PlayerName: " + entry.getValue().getPlayerName());
        }
        String blockCountQuery = "INSERT INTO player_data (UUID, BlocksMined, PlayerName) VALUES (?, ?, ?) "
                + "ON CONFLICT(UUID) DO UPDATE SET BlocksMined = excluded.BlocksMined, PlayerName = excluded.PlayerName;";

        try (PreparedStatement pstmt = connection.prepareStatement(blockCountQuery)) {
            for (Map.Entry<UUID, Digger.PlayerData> entry : blockCount.entrySet()) {
                pstmt.setString(1, entry.getKey().toString());
                pstmt.setInt(2, entry.getValue().getBlocksMined());
                pstmt.setString(3, entry.getValue().getPlayerName()); // PlayerNameの設定
                pstmt.executeUpdate();
            }
        }
    }


    private void savePlacedBlocks(Iterable<Location> placedBlocks) throws SQLException {
        String placedBlocksQuery = "INSERT INTO placed_blocks (World, X, Y, Z) VALUES (?, ?, ?, ?);";

        try (PreparedStatement pstmt = connection.prepareStatement(placedBlocksQuery)) {
            for (Location loc : placedBlocks) {
                pstmt.setString(1, loc.getWorld().getName());
                pstmt.setInt(2, loc.getBlockX());
                pstmt.setInt(3, loc.getBlockY());
                pstmt.setInt(4, loc.getBlockZ());
                pstmt.executeUpdate();
            }
        }
    }
    public static boolean checkConnection() throws SQLException {
        // connection はデータベースへの接続オブジェクトです
        return connection != null && !connection.isClosed();
    }

    public void closeConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
}