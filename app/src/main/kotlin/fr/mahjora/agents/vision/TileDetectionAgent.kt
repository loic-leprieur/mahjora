package fr.mahjora.agents.vision

import fr.mahjora.core.Tile

/** Box in normalized image coordinates (0..1). */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2

    fun iou(o: Box): Float {
        val w = minOf(right, o.right) - maxOf(left, o.left)
        val h = minOf(bottom, o.bottom) - maxOf(top, o.top)
        if (w <= 0f || h <= 0f) return 0f
        val inter = w * h
        return inter / (width * height + o.width * o.height - inter)
    }
}

/** Raw output of a model: a class index (see [Tile.index]) with its score. */
data class RawDetection(val classIndex: Int, val score: Float, val box: Box)

data class TileDetection(val tile: Tile, val confidence: Float, val box: Box)

/** Hand read from a photo, ready to be confirmed by the user. */
data class DetectedHand(
    /** Hand tiles in reading order (flowers excluded). */
    val tiles: List<Tile>,
    val flowers: List<Tile>,
    /** Detections kept but with a confidence below the "sure" threshold: to be double-checked. */
    val uncertain: List<TileDetection>,
    val detections: List<TileDetection>,
)

/** Agent in charge of recognising tiles on a photo taken by the device camera. */
interface TileDetectionAgent {
    /** False while no recognition model is installed: the UI then falls back to manual entry. */
    val isAvailable: Boolean

    suspend fun initialize()

    /** @param jpeg a JPEG-encoded photo, already rotated upright. */
    suspend fun detect(jpeg: ByteArray): DetectedHand

    fun close()
}

/** Runs the actual neural network (e.g. a 42-class TFLite detector) and returns raw boxes. */
interface TileInferenceBackend {
    val isReady: Boolean
    suspend fun load()
    suspend fun infer(jpeg: ByteArray): List<RawDetection>
    fun close()
}

/** Default backend until a trained model is bundled in the app. */
class NoModelBackend : TileInferenceBackend {
    override val isReady = false
    override suspend fun load() = Unit
    override suspend fun infer(jpeg: ByteArray): List<RawDetection> = emptyList()
    override fun close() = Unit
}

class ModelTileDetectionAgent(
    private val backend: TileInferenceBackend = NoModelBackend(),
    private val minScore: Float = 0.35f,
    private val sureScore: Float = 0.7f,
    private val nmsIou: Float = 0.5f,
) : TileDetectionAgent {

    override val isAvailable get() = backend.isReady

    override suspend fun initialize() = backend.load()

    override suspend fun detect(jpeg: ByteArray): DetectedHand =
        assemble(DetectionPostProcessor.process(backend.infer(jpeg), minScore, nmsIou), sureScore)

    override fun close() = backend.close()

    companion object {
        fun assemble(detections: List<TileDetection>, sureScore: Float): DetectedHand {
            val ordered = HandAssembler.readingOrder(detections)
            return DetectedHand(
                tiles = ordered.filter { !it.tile.isFlower }.map { it.tile },
                flowers = ordered.filter { it.tile.isFlower }.map { it.tile },
                uncertain = ordered.filter { it.confidence < sureScore },
                detections = ordered,
            )
        }
    }
}

object DetectionPostProcessor {
    /** Drops weak boxes, then applies class-agnostic non-maximum suppression. */
    fun process(raw: List<RawDetection>, minScore: Float, nmsIou: Float): List<TileDetection> {
        val kept = ArrayList<RawDetection>()
        for (d in raw.filter { it.score >= minScore && it.classIndex in 0 until Tile.KIND_COUNT }
            .sortedByDescending { it.score }) {
            if (kept.none { it.box.iou(d.box) > nmsIou }) kept += d
        }
        return kept.map { TileDetection(Tile.fromIndex(it.classIndex), it.score, it.box) }
    }
}

object HandAssembler {
    /** Sorts detections into rows (top to bottom) and each row left to right. */
    fun readingOrder(detections: List<TileDetection>): List<TileDetection> {
        if (detections.isEmpty()) return emptyList()
        val rowHeight = detections.map { it.box.height }.sorted()[detections.size / 2]
        val rows = ArrayList<MutableList<TileDetection>>()
        for (d in detections.sortedBy { it.box.centerY }) {
            val row = rows.lastOrNull()
            if (row != null && d.box.centerY - row.map { it.box.centerY }.average() < rowHeight * 0.6) row += d
            else rows += mutableListOf(d)
        }
        return rows.flatMap { row -> row.sortedBy { it.box.centerX } }
    }
}
