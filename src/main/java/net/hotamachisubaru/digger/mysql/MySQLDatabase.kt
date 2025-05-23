package net.hotamachisubaru.digger.mysql

import net.hotamachisubaru.digger.Digger
import org.bukkit.Location
import java.io.FileInputStream
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.UUID
import kotlin.collections.iterator
import kotlin.use

public class MySQLDatabase(prop: Properties) {
    // データベース接続のパラメータ
    private var url = ""
    private var user = ""
    private var password = ""

    // コンストラクタでMySQLDatabaseを初期化
    init {
        try {
            // 設定ファイルからプロパティを読み込む
            prop.load(FileInputStream(Digger.getInstance().dataFolder.absolutePath + CONFIG_FILE_PATH))
            this.url = prop.getProperty("db.url", "")
            this.user = prop.getProperty("db.user", "")
            this.password = prop.getProperty("db.password", "")
            initializeDatabase()
        } catch (e: IOException) {
            // 設定ファイルが読み込めなかった場合にエラーログを出力
            Digger.getInstance().logger.severe("MySQLデータベースに接続できませんでした: ${e.message}")
        }
    }

    // データベースを初期化し、必要なテーブルを作成
    private fun initializeDatabase() {
        try {
            connection.use { conn ->
                conn.createStatement().use { stmt ->
                    // プレイヤーデータテーブルの作成（DiamondMined）
                    stmt.execute(PLAYER_DATA_TABLE)
                    // ダイヤ設置テーブルの作成（diamond_mined）
                    stmt.execute(DIAMOND_MINED_TABLE)
                }
            }
        } catch (e: SQLException) {
            // データベースの初期化が失敗した場合にエラーログを出力
            Digger.getInstance().logger.severe("MySQLデータベース初期化エラー: ${e.message}")
        }
    }

    @get:Throws(SQLException::class)
    private val connection: Connection
        // MySQLデータベースへの接続を確立
        get() {
            // データベースが存在しない場合は作成する
            val fullUrl = "$url?createDatabaseIfNotExist=true"
            return DriverManager.getConnection(fullUrl, user, password)
        }

    /**
     * プレイヤーデータとダイヤ設置データを保存
     */
    fun saveData(
        diamondCount: Map<UUID, Digger.PlayerData>,
        diamondLocations: List<Location>,
        diamondLocationsWithUUID: Map<Location, UUID>
    ) {
        saveDiamondCountEntries(diamondCount)
        saveDiamondLocationEntries(diamondLocations, diamondLocationsWithUUID)
    }

    // ダイヤ採掘数データを保存
    private fun saveDiamondCountEntries(diamondCount: Map<UUID, Digger.PlayerData>) {
        val query = """
            INSERT INTO player_data (UUID, PlayerName, DiamondMined)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE DiamondMined = VALUES(DiamondMined), PlayerName = VALUES(PlayerName);
        """.trimIndent()
        try {
            connection.use { conn ->
                conn.prepareStatement(query).use { stmt ->
                    for ((uuid, data) in diamondCount) {
                        stmt.setString(1, uuid.toString())
                        stmt.setString(2, data.playerName)
                        stmt.setInt(3, data.getDiamondMined())
                        stmt.executeUpdate()
                    }
                }
            }
        } catch (e: SQLException) {
            Digger.getInstance().logger.severe("ダイヤ採掘数の保存に失敗しました: ${e.message}")
        }
    }

    // ダイヤ設置データを保存
    private fun saveDiamondLocationEntries(
        locations: List<Location>,
        withUUID: Map<Location, UUID>
    ) {
        val query = "INSERT INTO diamond_mined (UUID, World, X, Y, Z) VALUES (?, ?, ?, ?, ?);"
        try {
            connection.use { conn ->
                conn.prepareStatement(query).use { stmt ->
                    for (loc in locations) {
                        val uuid = withUUID[loc] ?: continue
                        stmt.setString(1, uuid.toString())
                        stmt.setString(2, loc.world.name)
                        stmt.setInt(3, loc.blockX)
                        stmt.setInt(4, loc.blockY)
                        stmt.setInt(5, loc.blockZ)
                        stmt.executeUpdate()
                    }
                }
            }
        } catch (e: SQLException) {
            Digger.getInstance().logger.severe("ダイヤ設置データの保存に失敗しました: ${e.message}")
        }
    }

    /**
     * データベースからプレイヤーデータを読み込む
     */
    fun loadData(): Map<UUID, Digger.PlayerData> {
        val result = mutableMapOf<UUID, Digger.PlayerData>()
        val query = "SELECT * FROM player_data;"
        try {
            connection.use { conn ->
                conn.prepareStatement(query).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val uuid = UUID.fromString(rs.getString("UUID"))
                            val name = rs.getString("PlayerName")
                            val count = rs.getInt("DiamondMined")
                            result[uuid] = Digger.PlayerData(name, count)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            Digger.getInstance().logger.severe("データ読み込みに失敗しました: ${e.message}")
        }
        return result
    }

    // データベース接続が有効か確認
    fun isConnected(): Boolean {
        return try {
            connection.use { conn -> conn.isValid(5) } // タイムアウト2秒
        } catch (e: SQLException) {
            Digger.getInstance().logger.severe("データベース接続の確認に失敗しました: ${e.message}")
            false
        }
    }

    companion object {
        private const val CONFIG_FILE_PATH = "/config.properties"

        // テーブル定義
        private const val PLAYER_DATA_TABLE = (
                "CREATE TABLE IF NOT EXISTS player_data (" +
                        "UUID CHAR(36) PRIMARY KEY, " +
                        "PlayerName VARCHAR(255), " +
                        "DiamondMined INT" +
                        ");"
                )
        private const val DIAMOND_MINED_TABLE = (
                "CREATE TABLE IF NOT EXISTS diamond_mined (" +
                        "UUID CHAR(36), " +
                        "World VARCHAR(255), " +
                        "X INT, " +
                        "Y INT, " +
                        "Z INT, " +
                        "FOREIGN KEY(UUID) REFERENCES player_data(UUID)" +
                        ");"
                )
    }
}