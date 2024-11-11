package net.hotamachisubaru.digger.sqlite;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import net.hotamachisubaru.digger.Digger;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SQLiteDatabase {
    private Connection connection;

    // データベース接続を開くメソッド
    public void openConnection(String path) throws SQLException {
        // SQLiteデータベースに接続
        String url = "jdbc:sqlite:" + path + "/Database.db";
        connection = DriverManager.getConnection(url);

        // テーブルの作成
        createTables();
    }

    // テーブルの作成
    private void createTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // player_data テーブルの作成
            String playerDataTableCreationQuery = "CREATE TABLE IF NOT EXISTS player_data ("
                    + "UUID VARCHAR(255) NOT NULL,"
                    + "PlayerName VARCHAR(255),"
                    + "BlocksMined INT DEFAULT 0,"
                    + "PRIMARY KEY (UUID));";
            statement.execute(playerDataTableCreationQuery);

            // placed_blocks テーブルの作成
            String placedBlocksTableCreationQuery = "CREATE TABLE IF NOT EXISTS placed_blocks ("
                    + "ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "World VARCHAR(255),"
                    + "X INT,"
                    + "Y INT,"
                    + "Z INT);";
            statement.execute(placedBlocksTableCreationQuery);
        }
    }

    // データベースからデータを取得するメソッド
    public Map<UUID, Digger.PlayerData> getData() throws SQLException {
        if (!checkConnection()) {
            throw new SQLException("データベース接続が確立されていません。");
        }

        Map<UUID, Digger.PlayerData> data = new HashMap<>();
        String query = "SELECT * FROM player_data;";

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                // データベースからUUIDとブロックの採掘数を取得
                UUID uuid = UUID.fromString(rs.getString("UUID"));
                String playerName = rs.getString("PlayerName");
                int blocksMined = rs.getInt("BlocksMined");

                // 取得したデータでPlayerDataオブジェクトを作成
                Digger.PlayerData playerData = new Digger.PlayerData(playerName, blocksMined);
                data.put(uuid, playerData);
            }
        }

        return data;
    }

    // データをロードするメソッド
    public Map<UUID, Digger.PlayerData> loadData() throws SQLException {
        return getData();
    }

    // データを保存するメソッド
    public void saveData(Map<UUID, Digger.PlayerData> blockCount, Iterable<Location> placedBlocks) throws SQLException {
        if (!checkConnection()) {
            throw new SQLException("データベース接続が確立されていません。");
        }

        saveBlockCount(blockCount);
        savePlacedBlocks(placedBlocks);
    }

    // ブロックカウントデータを保存
    private void saveBlockCount(Map<UUID, Digger.PlayerData> blockCount) throws SQLException {
        String blockCountQuery = "INSERT INTO player_data (UUID, BlocksMined, PlayerName) VALUES (?, ?, ?) "
                + "ON CONFLICT(UUID) DO UPDATE SET BlocksMined = excluded.BlocksMined, PlayerName = excluded.PlayerName;";

        try (PreparedStatement pstmt = connection.prepareStatement(blockCountQuery)) {
            for (Map.Entry<UUID, Digger.PlayerData> entry : blockCount.entrySet()) {
                pstmt.setString(1, entry.getKey().toString());
                pstmt.setInt(2, entry.getValue().getBlocksMined());
                pstmt.setString(3, entry.getValue().getPlayerName());
                pstmt.executeUpdate();
            }
        }
    }

    // 配置されたブロックデータを保存
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

    // データベース接続がアクティブかどうかをチェック
    public boolean checkConnection() throws SQLException {
        return connection != null && !connection.isClosed();
    }

    // データベース接続を閉じるメソッド
    public void closeConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // シングルトンインスタンスの取得メソッド
    private static SQLiteDatabase instance;

    public static SQLiteDatabase getInstance() {
        if (instance == null) {
            instance = new SQLiteDatabase();
        }
        return instance;
    }
}
