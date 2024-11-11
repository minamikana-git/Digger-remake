package net.hotamachisubaru.digger.mysql

import net.hotamachisubaru.digger.Digger
import net.hotamachisubaru.digger.Digger.PlayerData
import org.bukkit.Bukkit
import org.bukkit.Location
import java.io.FileInputStream
import java.io.IOException
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

class MySQLDatabase(prop: Properties) {
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
            Bukkit.getLogger().severe("MySQLデータベースに接続できませんでした。" + e.message)
        }
    }

    // データベースを初期化し、必要なテーブルを作成
    private fun initializeDatabase() {
        try {
            connection.use { conn ->
                conn.createStatement().use { stmt ->
                    // プレイヤーデータテーブルの作成
                    stmt.execute(PLAYER_DATA_TABLE)
                    // 配置されたブロックテーブルの作成
                    stmt.execute(PLACED_BLOCKS_TABLE)
                }
            }
        } catch (e: SQLException) {
            // データベースの初期化が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースに保存できませんでした。接続を確認してください。" + e.message)
        }
    }

    @get:Throws(SQLException::class)
    private val connection: Connection
        // MySQLデータベースへの接続を確立
        get() {
            // データベースが存在しない場合は作成するパラメータを追加
            val fullUrl = "$url?createDatabaseIfNotExist=true"
            return DriverManager.getConnection(fullUrl, user, password)
        }

    // プレイヤーデータと配置されたブロックデータを保存
    fun savePlayerData(
        blockCount: Map<UUID, PlayerData>,
        placedBlocks: List<Location>,
        placedBlocksWithUUID: Map<Location, UUID>
    ) {
        savePlayerDataEntries(blockCount)
        savePlacedBlocksEntries(placedBlocks, placedBlocksWithUUID)
    }

    // プレイヤーデータエントリをデータベースに保存
    private fun savePlayerDataEntries(blockCount: Map<UUID, PlayerData>) {
        val playerDataQuery =
            "INSERT INTO digger_player_data (UUID, PlayerName, BlocksMined) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE BlocksMined = ?;"
        try {
            connection.use { conn ->
                conn.prepareStatement(playerDataQuery).use { stmt ->
                    for ((key, value) in blockCount) {
                        // SQLステートメントのパラメータを設定
                        stmt.setString(1, key.toString())
                        stmt.setString(2, value.playerName)
                        stmt.setInt(3, value.blocksMined)
                        stmt.setInt(4, value.blocksMined)
                        // 更新を実行
                        stmt.executeUpdate()
                    }
                }
            }
        } catch (e: SQLException) {
            // プレイヤーデータの保存が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースに保存に失敗しました。" + e.message)
        }
    }

    // 配置されたブロックエントリをデータベースに保存
    private fun savePlacedBlocksEntries(placedBlocks: List<Location>, placedBlocksWithUUID: Map<Location, UUID>) {
        val placedBlocksQuery = "INSERT INTO digger_placed_blocks (UUID, World, X, Y, Z) VALUES (?, ?, ?, ?, ?);"
        try {
            connection.use { conn ->
                conn.prepareStatement(placedBlocksQuery).use { stmt ->
                    for (loc in placedBlocks) {
                        val playerId = placedBlocksWithUUID[loc]
                        // SQLステートメントのパラメータを設定
                        stmt.setString(1, playerId.toString())
                        stmt.setString(2, loc.world.name)
                        stmt.setInt(3, loc.blockX)
                        stmt.setInt(4, loc.blockY)
                        stmt.setInt(5, loc.blockZ)
                        // 更新を実行
                        stmt.executeUpdate()
                    }
                }
            }
        } catch (e: SQLException) {
            // 配置されたブロックデータの保存が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースに保存に失敗しました。" + e.message)
        }
    }

    // MySQLデータベースへの接続をテスト
    fun connect(): Boolean {
        try {
            connection.use { conn ->
                return true
            }
        } catch (e: SQLException) {
            // データベースへの接続が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースへ接続に失敗しました。" + e.message)
            return false
        }
    }

    val isConnected: Boolean
        // MySQLデータベースへの接続がアクティブかどうかを確認
        get() {
            try {
                connection.use { conn ->
                    return conn != null && !conn.isClosed
                }
            } catch (e: SQLException) {
                return false
            }
        }

    // データベースからプレイヤーデータを読み込む
    fun loadData(): Map<UUID, PlayerData> {
        val dataMap: MutableMap<UUID, PlayerData> = HashMap()
        val query = "SELECT * FROM digger_player_data;"
        try {
            connection.use { conn ->
                conn.prepareStatement(query).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            // 結果セットからデータを抽出
                            val uuid = UUID.fromString(rs.getString("UUID"))
                            val playerName = rs.getString("PlayerName")
                            val blocksMined = rs.getInt("BlocksMined")
                            // データをマップに追加
                            dataMap[uuid] = PlayerData(playerName, blocksMined)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            // データベースからのデータ読み込みが失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースからの読み込みに失敗しました。" + e.message)
        }
        return dataMap
    }

    // データベースに単一の配置ブロックを保存
    fun savePlacedBlock(playerUUID: UUID, loc: Location) {
        val insertQuery = "INSERT INTO digger_placed_blocks (UUID, World, X, Y, Z) VALUES (?, ?, ?, ?, ?);"
        try {
            connection.use { conn ->
                conn.prepareStatement(insertQuery).use { stmt ->
                    // SQLステートメントのパラメータを設定
                    stmt.setString(1, playerUUID.toString())
                    stmt.setString(2, loc.world.name)
                    stmt.setInt(3, loc.blockX)
                    stmt.setInt(4, loc.blockY)
                    stmt.setInt(5, loc.blockZ)
                    // 更新を実行
                    stmt.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            // 配置ブロックデータの保存が失敗した場合にエラーログを出力
            Bukkit.getLogger().severe("MySQLデータベースに保存に失敗しました。" + e.message)
        }
    }

    companion object {
        // 設定ファイルパスとSQLテーブル作成コマンドの定数
        private const val CONFIG_FILE_PATH = "/config.properties"
        private const val PLAYER_DATA_TABLE = ("CREATE TABLE IF NOT EXISTS digger_player_data ("
                + "UUID CHAR(36) PRIMARY KEY,"
                + "PlayerName VARCHAR(255),"
                + "BlocksMined INT);")
        private const val PLACED_BLOCKS_TABLE = ("CREATE TABLE IF NOT EXISTS digger_placed_blocks ("
                + "UUID CHAR(36),"
                + "World VARCHAR(255),"
                + "X INT,"
                + "Y INT,"
                + "Z INT,"
                + "FOREIGN KEY(UUID) REFERENCES digger_player_data(UUID));")
    }
}
