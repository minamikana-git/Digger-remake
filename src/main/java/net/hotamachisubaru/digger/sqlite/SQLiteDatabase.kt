package net.hotamachisubaru.digger.sqlite

import net.hotamachisubaru.digger.Digger.PlayerData
import org.bukkit.Location
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

class SQLiteDatabase {
    private var connection: Connection? = null

    // データベース接続を開くメソッド
    @Throws(SQLException::class)
    fun openConnection(path: String) {
        // SQLiteデータベースに接続
        val url = "jdbc:sqlite:$path/Database.db"
        connection = DriverManager.getConnection(url)

        // テーブルの作成
        createTables()
    }

    // テーブルの作成
    @Throws(SQLException::class)
    private fun createTables() {
        connection!!.createStatement().use { statement ->
            // player_data テーブルの作成
            val playerDataTableCreationQuery = ("CREATE TABLE IF NOT EXISTS player_data ("
                    + "UUID VARCHAR(255) NOT NULL,"
                    + "PlayerName VARCHAR(255),"
                    + "BlocksMined INT DEFAULT 0,"
                    + "PRIMARY KEY (UUID));")
            statement.execute(playerDataTableCreationQuery)

            // placed_blocks テーブルの作成
            val placedBlocksTableCreationQuery = ("CREATE TABLE IF NOT EXISTS placed_blocks ("
                    + "ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "World VARCHAR(255),"
                    + "X INT,"
                    + "Y INT,"
                    + "Z INT);")
            statement.execute(placedBlocksTableCreationQuery)
        }
    }

    @get:Throws(SQLException::class)
    val data: Map<UUID, PlayerData>
        // データベースからデータを取得するメソッド
        get() {
            if (!checkConnection()) {
                throw SQLException("データベース接続が確立されていません。")
            }

            val data: MutableMap<UUID, PlayerData> = HashMap()
            val query = "SELECT * FROM player_data;"

            connection!!.prepareStatement(query).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        // データベースからUUIDとブロックの採掘数を取得
                        val uuid = UUID.fromString(rs.getString("UUID"))
                        val playerName = rs.getString("PlayerName")
                        val diamondMined = rs.getInt("DiamondMined")

                        // 取得したデータでPlayerDataオブジェクトを作成
                        val playerData = PlayerData(playerName, diamondMined)
                        data[uuid] = playerData
                    }
                }
            }
            return data
        }

    // データをロードするメソッド
    @Throws(SQLException::class)
    fun loadData(): Map<UUID, PlayerData> {
        return data
    }

    // データを保存するメソッド
    @Throws(SQLException::class)
    fun saveData(blockCount: Map<UUID, PlayerData>, placedBlocks: Iterable<Location>) {
        if (!checkConnection()) {
            throw SQLException("データベース接続が確立されていません。")
        }

        saveBlockCount(blockCount)
        savePlacedBlocks(placedBlocks)
    }

    // ブロックカウントデータを保存
    @Throws(SQLException::class)
    private fun saveBlockCount(blockCount: Map<UUID, PlayerData>) {
        val blockCountQuery = ("INSERT INTO player_data (UUID, BlocksMined, PlayerName) VALUES (?, ?, ?) "
                + "ON CONFLICT(UUID) DO UPDATE SET BlocksMined = excluded.BlocksMined, PlayerName = excluded.PlayerName;")

        connection!!.prepareStatement(blockCountQuery).use { pstmt ->
            for ((key, value) in blockCount) {
                pstmt.setString(1, key.toString())
                pstmt.setInt(2, value.blocksMined)
                pstmt.setString(3, value.playerName)
                pstmt.executeUpdate()
            }
        }
    }

    // 配置されたブロックデータを保存
    @Throws(SQLException::class)
    private fun savePlacedBlocks(placedBlocks: Iterable<Location>) {
        val placedBlocksQuery = "INSERT INTO placed_blocks (World, X, Y, Z) VALUES (?, ?, ?, ?);"

        connection!!.prepareStatement(placedBlocksQuery).use { pstmt ->
            for (loc in placedBlocks) {
                pstmt.setString(1, loc.world.name)
                pstmt.setInt(2, loc.blockX)
                pstmt.setInt(3, loc.blockY)
                pstmt.setInt(4, loc.blockZ)
                pstmt.executeUpdate()
            }
        }
    }

    // データベース接続がアクティブかどうかをチェック
    @Throws(SQLException::class)
    fun checkConnection(): Boolean {
        return connection != null && !connection!!.isClosed
    }

    // データベース接続を閉じるメソッド
    @Throws(SQLException::class)
    fun closeConnection() {
        if (connection != null && !connection!!.isClosed) {
            connection!!.close()
        }
    }

    companion object {
        // シングルトンインスタンスの取得メソッド
        var instance: SQLiteDatabase? = null
            get() {
                if (field == null) {
                    field = SQLiteDatabase()
                }
                return field
            }
            private set
    }
}
