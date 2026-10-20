package fr.mahjora.agents.vision

import fr.mahjora.core.Tile
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectionTest {
    private fun raw(code: String, score: Float, x: Float, y: Float = 0.5f) =
        RawDetection(Tile.parse(code).index, score, Box(x, y - 0.05f, x + 0.05f, y + 0.05f))

    @Test
    fun nmsKeepsBestOfOverlappingBoxes() {
        val out = DetectionPostProcessor.process(
            listOf(raw("c1", 0.9f, 0.10f), raw("c2", 0.6f, 0.11f), raw("b3", 0.2f, 0.5f)),
            minScore = 0.35f, nmsIou = 0.5f,
        )
        assertEquals(listOf("c1"), out.map { it.tile.code })
    }

    @Test
    fun assemblesRowsLeftToRightAndSplitsFlowers() {
        val dets = listOf(
            raw("c3", 0.9f, 0.30f), raw("c1", 0.9f, 0.10f), raw("f2", 0.8f, 0.9f),
            raw("we", 0.5f, 0.10f, y = 0.8f), raw("c2", 0.9f, 0.20f),
        ).let { DetectionPostProcessor.process(it, 0.35f, 0.5f) }
        val hand = ModelTileDetectionAgent.assemble(dets, sureScore = 0.7f)
        assertEquals(listOf("c1", "c2", "c3", "we"), hand.tiles.map { it.code })
        assertEquals(listOf("f2"), hand.flowers.map { it.code })
        assertEquals(listOf("we"), hand.uncertain.map { it.tile.code })
    }
}
