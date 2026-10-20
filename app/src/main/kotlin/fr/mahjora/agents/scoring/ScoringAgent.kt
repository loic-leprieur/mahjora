package fr.mahjora.agents.scoring

import fr.mahjora.core.HandInput
import fr.mahjora.core.ScoredCombo

data class ScoreResult(
    /** False when the tiles do not form a winning hand (or the input is malformed). */
    val valid: Boolean,
    val error: String? = null,
    val combos: List<ScoredCombo> = emptyList(),
    val comboPoints: Int = 0,
    val flowerPoints: Int = 0,
    /** Mahjong value: combinations + flowers (flowers only count once the minimum is reached). */
    val total: Int = 0,
    val meetsMinimum: Boolean = false,
)

/** Agent in charge of computing the value of a winning hand. */
interface ScoringAgent {
    val minimumPoints: Int
    val catalog: List<ComboDefinition>
    fun score(input: HandInput): ScoreResult
}

class StandardScoringAgent(override val minimumPoints: Int = 8) : ScoringAgent {

    override val catalog: List<ComboDefinition> get() = ComboCatalog.ALL

    override fun score(input: HandInput): ScoreResult {
        input.validate()?.let { return ScoreResult(valid = false, error = it) }
        val views = HandDecomposer.decompose(input)
        if (views.isEmpty()) return ScoreResult(valid = false, error = "Ces tuiles ne forment pas un Mahjong.")

        val best = views.map { evaluate(it) }.maxBy { it.sumOf { c -> c.total } }
        val comboPoints = best.sumOf { it.total }
        val meets = comboPoints >= minimumPoints
        val flowerPoints = if (meets) input.flowers else 0
        val combos = if (flowerPoints > 0) {
            best + ScoredCombo(ComboCatalog.FLOWER, ComboCatalog.BY_ID.getValue(ComboCatalog.FLOWER).name, 1, flowerPoints)
        } else best
        return ScoreResult(
            valid = true,
            combos = combos.sortedByDescending { it.total },
            comboPoints = comboPoints,
            flowerPoints = flowerPoints,
            total = comboPoints + flowerPoints,
            meetsMinimum = meets,
        )
    }

    /** Combinations scoring in one reading, after removing the redundant ones (flowers excluded). */
    private fun evaluate(view: HandView): List<ScoredCombo> {
        val found = ComboCatalog.ALL
            .filter { it.id != ComboCatalog.FLOWER && it.id != ComboCatalog.NO_VALUE_HAND }
            .map { it to it.count(view) }
            .filter { it.second > 0 }
        val removed = found.flatMap { it.first.excludes }.toSet()
        val kept = found.filter { it.first.id !in removed }
            .map { (def, n) -> ScoredCombo(def.id, def.name, def.points, n) }
        if (kept.isNotEmpty()) return kept
        val noValue = ComboCatalog.BY_ID.getValue(ComboCatalog.NO_VALUE_HAND)
        return listOf(ScoredCombo(noValue.id, noValue.name, noValue.points))
    }
}
