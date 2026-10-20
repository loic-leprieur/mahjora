package fr.mahjora.core

enum class MeldKind { CHOW, PUNG, KONG, KNITTED }

/**
 * A figure of 3 tiles. A kong is counted as a figure of 3 tiles (its fourth copy is not stored).
 * For a chow [tiles] holds the three consecutive tiles, lowest first.
 */
data class Meld(val kind: MeldKind, val tiles: List<Tile>, val concealed: Boolean) {
    val first: Tile get() = tiles.first()
    val isPungLike: Boolean get() = kind == MeldKind.PUNG || kind == MeldKind.KONG

    companion object {
        fun chow(first: Tile, concealed: Boolean = false) =
            Meld(MeldKind.CHOW, listOf(first, first.plus(1), first.plus(2)), concealed)

        fun pung(tile: Tile, concealed: Boolean = false) =
            Meld(MeldKind.PUNG, List(3) { tile }, concealed)

        fun kong(tile: Tile, concealed: Boolean = false) =
            Meld(MeldKind.KONG, List(3) { tile }, concealed)

        fun knitted(tiles: List<Tile>) = Meld(MeldKind.KNITTED, tiles, true)
    }
}

enum class WinMethod { SELF_DRAW, DISCARD }

/** Situational bonuses that cannot be derived from the tiles alone. */
enum class Circumstance(val label: String) {
    LAST_WALL_TILE("Dernière tuile tirée"),
    LAST_DISCARD("Dernière tuile jetée"),
    ROBBING_KONG("Voler le kong"),
    KONG_REPLACEMENT("Finir sur le kong"),
    LAST_EXISTING_TILE("Dernière tuile existante"),
}

/**
 * A winning hand as entered by the user or read by the camera.
 *
 * [concealed] = tiles still in the player's hand, winning tile included, flowers excluded.
 * [declared] = figures already shown (exposed, or concealed kongs). Invariant: concealed + 3 × declared = 14.
 */
data class HandInput(
    val concealed: List<Tile>,
    val declared: List<Meld>,
    val flowers: Int,
    val winningTile: Tile,
    val winMethod: WinMethod,
    val circumstances: Set<Circumstance> = emptySet(),
    val seatWind: Wind = Wind.EAST,
    val roundWind: Wind = Wind.EAST,
) {
    /** Returns a French error message, or null when the hand is well formed. */
    fun validate(): String? {
        if (concealed.any { it.isFlower }) return "Les fleurs ne se placent pas dans la main."
        if (concealed.size + 3 * declared.size != 14) {
            return "La main doit compter 14 tuiles (figures déclarées = 3 tuiles) : ${concealed.size + 3 * declared.size}."
        }
        if (winningTile !in concealed) return "La tuile gagnante doit faire partie de la main cachée."
        if (flowers !in 0..8) return "Nombre de fleurs invalide."
        val counts = IntArray(Tile.KIND_COUNT)
        concealed.forEach { counts[it.index]++ }
        declared.forEach { m ->
            when (m.kind) {
                MeldKind.CHOW, MeldKind.KNITTED -> m.tiles.forEach { counts[it.index]++ }
                MeldKind.PUNG -> counts[m.first.index] += 3
                MeldKind.KONG -> counts[m.first.index] += 4
            }
        }
        counts.forEachIndexed { i, c -> if (c > 4) return "Plus de 4 exemplaires de ${Tile.fromIndex(i).label}." }
        return null
    }
}

/** One combination found in a hand. [points] is the value of a single occurrence. */
data class ScoredCombo(val id: Int, val name: String, val points: Int, val count: Int = 1) {
    val total: Int get() = points * count
}
