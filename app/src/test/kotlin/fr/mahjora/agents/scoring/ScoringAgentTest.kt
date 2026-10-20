package fr.mahjora.agents.scoring

import fr.mahjora.core.HandInput
import fr.mahjora.core.Meld
import fr.mahjora.core.Tile
import fr.mahjora.core.WinMethod
import fr.mahjora.core.Wind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringAgentTest {
    private val agent = StandardScoringAgent()

    private fun hand(
        concealed: String,
        win: String,
        declared: List<Meld> = emptyList(),
        method: WinMethod = WinMethod.DISCARD,
        flowers: Int = 0,
        seat: Wind = Wind.SOUTH,
        round: Wind = Wind.SOUTH,
    ) = HandInput(Tile.parseAll(concealed), declared, flowers, Tile.parse(win), method, emptySet(), seat, round)

    @Test
    fun catalogHas82UniqueCombinations() {
        assertEquals(82, ComboCatalog.ALL.size)
        assertEquals((1..82).toList(), ComboCatalog.ALL.map { it.id })
    }

    @Test
    fun grandeSuiteWithTypeBonus() {
        val r = agent.score(hand("c1 c2 c3 b4 b5 b6 k7 k8 k9 we we we dr dr", "dr"))
        assertTrue(r.valid)
        val ids = r.combos.map { it.id }.toSet()
        assertTrue(39 in ids && 52 in ids)
        // Grande suite 8 + tout type 6 + pung de vent 1 + finir sur la paire 1 + tout caché donné 2
        assertEquals(18, r.total)
    }

    @Test
    fun flowersAreAddedOnceMinimumIsReached() {
        val r = agent.score(hand("c1 c2 c3 b4 b5 b6 k7 k8 k9 we we we dr dr", "dr", flowers = 2))
        assertEquals(20, r.total)
        assertEquals(2, r.flowerPoints)
    }

    @Test
    fun sevenPureConsecutivePairsExcludesLowerCombos() {
        val r = agent.score(
            hand("c1 c1 c2 c2 c3 c3 c4 c4 c5 c5 c6 c6 c7 c7", "c4", method = WinMethod.SELF_DRAW),
        )
        assertEquals(listOf(6), r.combos.map { it.id })
        assertEquals(88, r.total)
    }

    @Test
    fun fourGreatWinds() {
        val r = agent.score(hand("we we we ws ws ws wo wo wo wn wn wn c5 c5", "c5"))
        val ids = r.combos.map { it.id }
        assertTrue(1 in ids)
        assertFalse(38 in ids)
        assertTrue(r.total >= 88)
    }

    @Test
    fun noValueHand() {
        val r = agent.score(
            hand(
                "c2 c3 c4 b3 b4 b5 k5 k6 k7 wn wn", "k7",
                declared = listOf(Meld.chow(Tile.parse("b7"))),
            ),
        )
        assertEquals(listOf(43), r.combos.map { it.id })
        assertEquals(8, r.total)
    }

    @Test
    fun nineGates() {
        val r = agent.score(hand("c1 c1 c1 c2 c3 c4 c5 c5 c6 c7 c8 c9 c9 c9", "c5"))
        assertTrue(4 in r.combos.map { it.id })
        assertTrue(r.total >= 88)
    }

    @Test
    fun malformedOrNonWinningHandIsRejected() {
        assertFalse(agent.score(hand("c1 c2 c3", "c1")).valid)
        assertFalse(agent.score(hand("c1 c4 c7 b2 b5 b9 k1 k3 k5 we ws wn dr dw", "c1")).valid)
    }

    @Test
    fun paymentsAreZeroSum() {
        assertArrayEquals(intArrayOf(54, -18, -18, -18), Payments.deltas(0, null, 10))
        assertArrayEquals(intArrayOf(34, -8, -18, -8), Payments.deltas(0, 2, 10))
    }

    @Test
    fun targetPointsTable() {
        val a = TargetPoints.needed(64)
        assertEquals(listOf(32, 16, 8), listOf(a.neutralPlayer, a.paidByPlayer, a.selfDraw))
        val b = TargetPoints.needed(48)
        assertEquals(listOf(16, 8), listOf(b.neutralPlayer, b.paidByPlayer))
        assertNull(b.selfDraw)
    }

    @Test
    fun seatRotation() {
        assertEquals(
            listOf(Wind.EAST, Wind.SOUTH, Wind.NORTH, Wind.WEST),
            (0..3).map { SeatRotation.seatWind(0, it) },
        )
        assertEquals(Wind.SOUTH, SeatRotation.roundWind(4))
    }
}
