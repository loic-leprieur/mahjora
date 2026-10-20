package fr.mahjora.agents.scoring

import fr.mahjora.core.Wind

/** Points exchanged between the four players for one hand. */
object Payments {
    private const val BASE = 8

    /**
     * @param discarderSlot null when the winner drew the tile himself.
     * @return the delta of each seat slot (0..3); the sum is always zero.
     */
    fun deltas(winnerSlot: Int, discarderSlot: Int?, mahjongValue: Int): IntArray {
        val d = IntArray(4)
        for (slot in 0..3) {
            if (slot == winnerSlot) continue
            val pays = when {
                discarderSlot == null -> mahjongValue + BASE
                slot == discarderSlot -> mahjongValue + BASE
                else -> BASE
            }
            d[slot] -= pays
            d[winnerSlot] += pays
        }
        return d
    }
}

/** "Différentiel points": Mahjong value needed to gain a given lead (memo 4). */
object TargetPoints {
    class Needed(val neutralPlayer: Int?, val paidByPlayer: Int?, val selfDraw: Int?)

    /** Null means any Mahjong is enough ("Tout Mahjong", below the 8-point minimum). */
    fun needed(pointsDifference: Int): Needed {
        val base = pointsDifference - 32
        fun value(divisor: Int) = (base / divisor).takeIf { it >= 8 }
        return Needed(value(1), value(2), value(4))
    }
}

/** "Rotation des places": wind of each player slot at each hand of a round of 4 hands. */
object SeatRotation {
    private val TABLE = listOf(
        listOf(Wind.EAST, Wind.SOUTH, Wind.NORTH, Wind.WEST),
        listOf(Wind.SOUTH, Wind.EAST, Wind.WEST, Wind.NORTH),
        listOf(Wind.WEST, Wind.NORTH, Wind.EAST, Wind.SOUTH),
        listOf(Wind.NORTH, Wind.WEST, Wind.SOUTH, Wind.EAST),
    )

    /** Wind of the player who started the game at [slot] (0 = Est), for the 0-based [handNumber]. */
    fun seatWind(slot: Int, handNumber: Int): Wind = TABLE[slot][handNumber % 4]

    /** Prevailing wind: changes every 4 hands (E, S, O, N). */
    fun roundWind(handNumber: Int): Wind = Wind.entries[(handNumber / 4) % 4]
}
