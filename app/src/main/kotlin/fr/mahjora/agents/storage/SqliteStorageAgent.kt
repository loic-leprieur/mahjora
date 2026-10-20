package fr.mahjora.agents.storage

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import fr.mahjora.core.ScoredCombo
import fr.mahjora.core.WinMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [StorageAgent] backed by a SQLite file in the app's private storage. */
class SqliteStorageAgent(context: Context, dbName: String? = "mahjora.db") : StorageAgent {

    private val helper = Helper(context.applicationContext, dbName)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    override suspend fun getOrCreatePlayer(name: String): Player = io {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Le nom du joueur est vide." }
        db.rawQuery("SELECT id, name FROM players WHERE name = ? COLLATE NOCASE", arrayOf(clean)).use {
            if (it.moveToFirst()) return@io Player(it.getLong(0), it.getString(1))
        }
        val id = db.insertOrThrow("players", null, ContentValues().apply {
            put("name", clean)
            put("created_at", System.currentTimeMillis())
        })
        Player(id, clean)
    }

    override suspend fun listPlayers(): List<Player> = io {
        db.rawQuery("SELECT id, name FROM players ORDER BY name COLLATE NOCASE", null)
            .use { c -> c.map { Player(it.getLong(0), it.getString(1)) } }
    }

    override suspend fun createGame(name: String?, players: List<Player>): Game = io {
        require(players.size == 4) { "Une partie compte 4 joueurs." }
        val now = System.currentTimeMillis()
        db.inTransaction {
            val id = insertOrThrow("games", null, ContentValues().apply {
                put("name", name)
                put("started_at", now)
            })
            players.forEachIndexed { slot, p ->
                insertOrThrow("game_players", null, ContentValues().apply {
                    put("game_id", id); put("slot", slot); put("player_id", p.id)
                })
            }
            Game(id, name, now, null, players)
        }
    }

    override suspend fun getGame(id: Long): Game? = io { loadGame(id) }

    override suspend fun listGames(): List<GameSummary> = io {
        val ids = db.rawQuery("SELECT id FROM games ORDER BY started_at DESC", null).use { c -> c.map { it.getLong(0) } }
        ids.mapNotNull { id ->
            val game = loadGame(id) ?: return@mapNotNull null
            val totals = totals(id)
            val count = db.rawQuery("SELECT COUNT(*) FROM hands WHERE game_id = ?", arrayOf(id.toString()))
                .use { it.moveToFirst(); it.getInt(0) }
            GameSummary(game, totals, count)
        }
    }

    override suspend fun finishGame(id: Long) = io {
        db.update("games", ContentValues().apply { put("ended_at", System.currentTimeMillis()) }, "id = ?", arrayOf(id.toString()))
        Unit
    }

    override suspend fun deleteGame(id: Long) = io {
        db.delete("games", "id = ?", arrayOf(id.toString()))
        Unit
    }

    override suspend fun saveHand(hand: NewHand): HandRecord = io {
        require(hand.deltas.size == 4) { "Il faut un score par joueur." }
        val id = db.inTransaction {
            val handId = insertOrThrow("hands", null, ContentValues().apply {
                put("game_id", hand.gameId)
                put("hand_number", hand.handNumber)
                put("winner_slot", hand.winnerSlot)
                put("discarder_slot", hand.discarderSlot)
                put("win_method", hand.winMethod?.name)
                put("mj_points", hand.mahjongValue)
                put("input_json", hand.input?.let(HandInputJson::encode))
                put("photo_path", hand.photoPath)
                put("played_at", hand.playedAt)
            })
            hand.deltas.forEachIndexed { slot, d ->
                insertOrThrow("hand_scores", null, ContentValues().apply {
                    put("hand_id", handId); put("slot", slot); put("delta", d)
                })
            }
            hand.combos.forEach { c ->
                insertOrThrow("hand_combos", null, ContentValues().apply {
                    put("hand_id", handId); put("combo_id", c.id); put("name", c.name)
                    put("points", c.points); put("count", c.count)
                })
            }
            handId
        }
        HandRecord(
            id, hand.gameId, hand.handNumber, hand.winnerSlot, hand.discarderSlot, hand.winMethod,
            hand.mahjongValue, hand.deltas, hand.combos, hand.input, hand.photoPath, hand.playedAt,
        )
    }

    override suspend fun listHands(gameId: Long): List<HandRecord> = io {
        db.rawQuery(
            "SELECT id, hand_number, winner_slot, discarder_slot, win_method, mj_points, input_json, photo_path, played_at " +
                "FROM hands WHERE game_id = ? ORDER BY hand_number",
            arrayOf(gameId.toString()),
        ).use { c ->
            c.map {
                val id = it.getLong(0)
                HandRecord(
                    id = id,
                    gameId = gameId,
                    handNumber = it.getInt(1),
                    winnerSlot = if (it.isNull(2)) null else it.getInt(2),
                    discarderSlot = if (it.isNull(3)) null else it.getInt(3),
                    winMethod = it.getString(4)?.let(WinMethod::valueOf),
                    mahjongValue = it.getInt(5),
                    deltas = deltas(id),
                    combos = combos(id),
                    input = it.getString(6)?.let(HandInputJson::decode),
                    photoPath = it.getString(7),
                    playedAt = it.getLong(8),
                )
            }
        }
    }

    override suspend fun deleteHand(handId: Long) = io {
        db.delete("hands", "id = ?", arrayOf(handId.toString()))
        Unit
    }

    // ---- helpers ----------------------------------------------------------------------------

    private fun loadGame(id: Long): Game? {
        val row = db.rawQuery("SELECT name, started_at, ended_at FROM games WHERE id = ?", arrayOf(id.toString()))
            .use { if (it.moveToFirst()) Triple(it.getString(0), it.getLong(1), if (it.isNull(2)) null else it.getLong(2)) else null }
            ?: return null
        val players = db.rawQuery(
            "SELECT p.id, p.name FROM game_players gp JOIN players p ON p.id = gp.player_id WHERE gp.game_id = ? ORDER BY gp.slot",
            arrayOf(id.toString()),
        ).use { c -> c.map { Player(it.getLong(0), it.getString(1)) } }
        return Game(id, row.first, row.second, row.third, players)
    }

    private fun totals(gameId: Long): List<Int> {
        val totals = IntArray(4)
        db.rawQuery(
            "SELECT hs.slot, SUM(hs.delta) FROM hand_scores hs JOIN hands h ON h.id = hs.hand_id WHERE h.game_id = ? GROUP BY hs.slot",
            arrayOf(gameId.toString()),
        ).use { c -> c.forEach { totals[it.getInt(0)] = it.getInt(1) } }
        return totals.toList()
    }

    private fun deltas(handId: Long): List<Int> {
        val d = IntArray(4)
        db.rawQuery("SELECT slot, delta FROM hand_scores WHERE hand_id = ?", arrayOf(handId.toString()))
            .use { c -> c.forEach { d[it.getInt(0)] = it.getInt(1) } }
        return d.toList()
    }

    private fun combos(handId: Long): List<ScoredCombo> =
        db.rawQuery("SELECT combo_id, name, points, count FROM hand_combos WHERE hand_id = ? ORDER BY id", arrayOf(handId.toString()))
            .use { c -> c.map { ScoredCombo(it.getInt(0), it.getString(1), it.getInt(2), it.getInt(3)) } }

    private inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
        beginTransaction()
        try {
            val r = block()
            setTransactionSuccessful()
            return r
        } finally {
            endTransaction()
        }
    }

    private inline fun <T> Cursor.map(transform: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        while (moveToNext()) out += transform(this)
        return out
    }

    private inline fun Cursor.forEach(action: (Cursor) -> Unit) {
        while (moveToNext()) action(this)
    }

    private class Helper(context: Context, name: String?) : SQLiteOpenHelper(context, name, null, VERSION) {
        override fun onConfigure(db: SQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            SCHEMA_V1.forEach(db::execSQL)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Add one `if (oldVersion < N)` block per schema change; never drop user data.
        }
    }

    private companion object {
        const val VERSION = 1

        val SCHEMA_V1 = listOf(
            """CREATE TABLE players(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                created_at INTEGER NOT NULL)""",
            """CREATE TABLE games(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                started_at INTEGER NOT NULL,
                ended_at INTEGER)""",
            """CREATE TABLE game_players(
                game_id INTEGER NOT NULL REFERENCES games(id) ON DELETE CASCADE,
                slot INTEGER NOT NULL CHECK(slot BETWEEN 0 AND 3),
                player_id INTEGER NOT NULL REFERENCES players(id),
                PRIMARY KEY(game_id, slot))""",
            """CREATE TABLE hands(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                game_id INTEGER NOT NULL REFERENCES games(id) ON DELETE CASCADE,
                hand_number INTEGER NOT NULL,
                winner_slot INTEGER,
                discarder_slot INTEGER,
                win_method TEXT,
                mj_points INTEGER NOT NULL DEFAULT 0,
                input_json TEXT,
                photo_path TEXT,
                played_at INTEGER NOT NULL,
                UNIQUE(game_id, hand_number))""",
            """CREATE TABLE hand_scores(
                hand_id INTEGER NOT NULL REFERENCES hands(id) ON DELETE CASCADE,
                slot INTEGER NOT NULL CHECK(slot BETWEEN 0 AND 3),
                delta INTEGER NOT NULL,
                PRIMARY KEY(hand_id, slot))""",
            """CREATE TABLE hand_combos(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                hand_id INTEGER NOT NULL REFERENCES hands(id) ON DELETE CASCADE,
                combo_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                points INTEGER NOT NULL,
                count INTEGER NOT NULL DEFAULT 1)""",
            "CREATE INDEX idx_hands_game ON hands(game_id)",
            "CREATE INDEX idx_combos_hand ON hand_combos(hand_id)",
        )
    }
}
