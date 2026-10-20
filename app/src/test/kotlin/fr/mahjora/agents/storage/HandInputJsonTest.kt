package fr.mahjora.agents.storage

import fr.mahjora.core.Circumstance
import fr.mahjora.core.HandInput
import fr.mahjora.core.Meld
import fr.mahjora.core.Tile
import fr.mahjora.core.WinMethod
import fr.mahjora.core.Wind
import org.junit.Assert.assertEquals
import org.junit.Test

class HandInputJsonTest {
    @Test
    fun roundTrip() {
        val input = HandInput(
            concealed = Tile.parseAll("c2 c3 c4 b3 b4 b5 k5 k6 k7 wn wn"),
            declared = listOf(Meld.chow(Tile.parse("b7")), Meld.kong(Tile.parse("dr"), concealed = true)),
            flowers = 2,
            winningTile = Tile.parse("k7"),
            winMethod = WinMethod.SELF_DRAW,
            circumstances = setOf(Circumstance.LAST_WALL_TILE),
            seatWind = Wind.WEST,
            roundWind = Wind.NORTH,
        )
        assertEquals(input, HandInputJson.decode(HandInputJson.encode(input)))
    }
}
