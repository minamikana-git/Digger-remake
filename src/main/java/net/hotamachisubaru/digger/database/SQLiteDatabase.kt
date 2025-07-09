package net.hotamachisubaru.digger.database

import net.hotamachisubaru.digger.Digger
import org.bukkit.Location
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import kotlin.collections.iterator

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
            // player_data テーブルの作成（DiamondMined に変更）
            val playerDataTableCreationQuery = ("CREATE TABLE IF NOT EXISTS player_data ("
                    + "UUID VARCHAR(255) NOT NULL,"
                    + "PlayerName VARCHAR(255),"
                    + "DiamondMined INT DEFAULT 0,"
                    + "PRIMARY KEY (UUID));")
            statement.execute(playerDataTableCreationQuery)

            // diamond_mined テーブルの作成
            val diamondMinedTableCreationQuery = ("CREATE TABLE IF NOT EXISTS diamond_mined ("
                    + "ID INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "World VARCHAR(255),"
                    + "X INT,"
                    + "Y INT,"
                    + "Z INT);")
            statement.execute(diamondMinedTableCreationQuery)
        }
    }

    @get:Throws(SQLException::class)
    val data: Map<UUID, Digger.PlayerData>
        // データベースからデータを取得するメソッド
        get() {
            if (!checkConnection()) {
                throw SQLException("データベース接続が確立されていません。")
            }

            val data: MutableMap<UUID, Digger.PlayerData> = HashMap()
            val query = "SELECT * FROM player_data;"

            connection!!.prepareStatement(query).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        // データベースからUUIDとダイヤ鉱采数を取得
                        val uuid = UUID.fromString(rs.getString("UUID"))
                        val playerName = rs.getString("PlayerName")
                        val diamondMined = rs.getInt("DiamondMined")

                        // 取得したデータでPlayerDataオブジェクトを作成
                        val playerData = Digger.PlayerData(playerName, diamondMined)
                        data[uuid] = playerData
                    }
                }
            }
            return data
        }

    // データをロードするメソッド
    @Throws(SQLException::class)
    fun loadData(): Map<UUID, Digger.PlayerData> {
        return data
    }

    // データを保存するメソッド
    @Throws(SQLException::class)
    fun saveData(diamondCount: Map<UUID, Digger.PlayerData>, diamondLocations: Iterable<Location>) {
        if (!checkConnection()) {
            throw SQLException("データベース接続が確立されていません。")
        }

        saveDiamondCount(diamondCount)
        saveDiamondLocations(diamondLocations)
    }

    // ダイヤ採掘数データを保存
    @Throws(SQLException::class)
    private fun saveDiamondCount(diamondCount: Map<UUID, Digger.PlayerData>) {
        val diamondCountQuery = ("INSERT INTO player_data (UUID, DiamondMined, PlayerName) VALUES (?, ?, ?) "
                + "ON CONFLICT(UUID) DO UPDATE SET DiamondMined = excluded.DiamondMined, PlayerName = excluded.PlayerName;")

        connection!!.prepareStatement(diamondCountQuery).use { pstmt ->
            for ((key, value) in diamondCount) {
                pstmt.setString(1, key.toString())
                pstmt.setInt(2, value.diamondMined)
                pstmt.setString(3, value.playerName)
                pstmt.executeUpdate()
            }
        }
    }

    // ダイヤ設置データを保存
    @Throws(SQLException::class)
    private fun saveDiamondLocations(diamondLocations: Iterable<Location>) {
        val diamondLocationsQuery = "INSERT INTO diamond_mined (World, X, Y, Z) VALUES (?, ?, ?, ?);"

        connection!!.prepareStatement(diamondLocationsQuery).use { pstmt ->
            for (loc in diamondLocations) {
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


        // シングルトンインスタンスの取得メソッド
    companion object {
            @JvmStatic
            fun getInstance(): SQLiteDatabase {
                if (_instance == null) _instance = SQLiteDatabase()
                return _instance!!
            }

            private var _instance: SQLiteDatabase? = null
        }
    }