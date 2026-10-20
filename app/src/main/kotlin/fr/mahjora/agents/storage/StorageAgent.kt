package fr.mahjora.agents.storage

import fr.mahjora.core.HandInput
import fr.mahjora.core.ScoredCombo
import fr.mahjora.core.WinMethod

data class Player(val id: Long, val name: String)

/** [players] are listed by seat slot: slot 0 started East, then South, West, North. */
data class Game(
    val id: Long,
    val name: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val players: List<Player>,
)

data class GameSummary(val game: Game, val totals: List<Int>, val handCount: Int)

/** A hand to save. A draw has no winner. */
data class NewHand(
    val gameId: Long,
    val handNumber: Int,
    val winnerSlot: Int?,
    val discarderSlot: Int?,
    val winMethod: WinMethod?,
    val mahjongValue: Int,
    /** Points won/lost by each seat slot. */
    val deltas: List<Int>,
    val combos: List<ScoredCombo>,
    val input: HandInput?,
    val photoPath: String? = null,
    val playedAt: Long = System.currentTimeMillis(),
)

data class HandRecord(
    val id: Long,
    val gameId: Long,
    val handNumber: Int,
    val winnerSlot: Int?,
    val discarderSlot: Int?,
    val winMethod: WinMethod?,
    val mahjongValue: Int,
    val deltas: List<Int>,
    val combos: List<ScoredCombo>,
    val input: HandInput?,
    val photoPath: String?,
    val playedAt: Long,
)

/** Agent in charge of persisting players, games and hands in the device's SQLite database. */
interface StorageAgent {
    suspend fun getOrCreatePlayer(name: String): Player
    suspend fun listPlayers(): List<Player>

    /** @param players the four players in seat order (East, South, West, North). */
    suspend fun createGame(name: String?, players: List<Player>): Game
    suspend fun getGame(id: Long): Game?
    suspend fun listGames(): List<GameSummary>
    suspend fun finishGame(id: Long)
    suspend fun deleteGame(id: Long)

    suspend fun saveHand(hand: NewHand): HandRecord
    suspend fun listHands(gameId: Long): List<HandRecord>
    suspend fun deleteHand(handId: Long)
}
