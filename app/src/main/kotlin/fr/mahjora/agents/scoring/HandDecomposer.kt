package fr.mahjora.agents.scoring

import fr.mahjora.core.HandInput
import fr.mahjora.core.Meld
import fr.mahjora.core.MeldKind
import fr.mahjora.core.Suit
import fr.mahjora.core.Tile
import fr.mahjora.core.WinMethod

/** Enumerates every valid reading of a 14-tile hand: standard (4 figures + pair) and special hands. */
object HandDecomposer {

    fun decompose(input: HandInput): List<HandView> {
        val counts = IntArray(Tile.PLAYABLE_COUNT)
        input.concealed.forEach { counts[it.index]++ }
        val slots = 4 - input.declared.size
        val views = ArrayList<HandView>()

        // Standard hands.
        for ((sets, pair) in withPair(counts, slots)) {
            views += variants(input, input.declared + sets, pair, SpecialHand.NONE)
        }

        // Knitted straight + 1 figure + pair (needs at most one declared figure).
        if (input.declared.size <= 1) {
            for (perm in KNITTED_PERMUTATIONS) {
                val knitted = knittedTiles(perm)
                if (knitted.any { counts[it.index] == 0 }) continue
                val rest = counts.copyOf()
                knitted.forEach { rest[it.index]-- }
                val knittedMelds = perm.indices.map { r ->
                    Meld.knitted(knitted.filterIndexed { i, _ -> i / 3 == r })
                }
                for ((sets, pair) in withPair(rest, 1 - input.declared.size)) {
                    views += variants(input, input.declared + knittedMelds + sets, pair, SpecialHand.NONE)
                }
            }
        }

        if (input.declared.isEmpty()) specialHand(input, counts)?.let { views += it }
        return views
    }

    // ---- standard decomposition -------------------------------------------------------------

    private fun withPair(counts: IntArray, slots: Int): List<Pair<List<Meld>, Tile>> {
        val out = ArrayList<Pair<List<Meld>, Tile>>()
        for (p in counts.indices) {
            if (counts[p] < 2) continue
            counts[p] -= 2
            partition(counts, slots).forEach { out += it to Tile.fromIndex(p) }
            counts[p] += 2
        }
        return out
    }

    private fun partition(c: IntArray, slots: Int): List<List<Meld>> {
        val first = c.indexOfFirst { it > 0 }
        if (first < 0) return if (slots == 0) listOf(emptyList()) else emptyList()
        if (slots == 0) return emptyList()
        val tile = Tile.fromIndex(first)
        val out = ArrayList<List<Meld>>()
        if (c[first] >= 3) {
            c[first] -= 3
            partition(c, slots - 1).forEach { out += listOf(Meld.pung(tile, concealed = true)) + it }
            c[first] += 3
        }
        if (tile.isSuited && tile.rank <= 7 && c[first + 1] > 0 && c[first + 2] > 0) {
            c[first]--; c[first + 1]--; c[first + 2]--
            partition(c, slots - 1).forEach { out += listOf(Meld.chow(tile, concealed = true)) + it }
            c[first]++; c[first + 1]++; c[first + 2]++
        }
        return out
    }

    // ---- winning tile placement -------------------------------------------------------------

    /** One view per place the winning tile could occupy (a figure or the pair). */
    private fun variants(input: HandInput, melds: List<Meld>, pair: Tile, special: SpecialHand): List<HandView> {
        val win = input.winningTile
        val tiles = melds.flatMap { it.tiles } + listOf(pair, pair)
        val out = ArrayList<HandView>()
        val declaredCount = input.declared.size
        for (i in declaredCount until melds.size) {
            val m = melds[i]
            if (win !in m.tiles) continue
            var placed = melds
            var pos = WinPosition.NONE
            when (m.kind) {
                MeldKind.PUNG, MeldKind.KONG ->
                    if (input.winMethod == WinMethod.DISCARD) {
                        placed = melds.toMutableList().also { it[i] = m.copy(concealed = false) }
                    }
                MeldKind.CHOW -> pos = when {
                    win == m.tiles[1] -> WinPosition.MIDDLE
                    win == m.tiles[2] && m.first.rank == 1 -> WinPosition.EDGE
                    win == m.tiles[0] && m.first.rank == 7 -> WinPosition.EDGE
                    else -> WinPosition.NONE
                }
                MeldKind.KNITTED -> Unit
            }
            out += HandView(input, placed, pair, special, pos, tiles)
        }
        if (win == pair) out += HandView(input, melds, pair, special, WinPosition.PAIR, tiles)
        return out
    }

    // ---- knitted tiles ----------------------------------------------------------------------

    private val SUITS = listOf(Suit.CIRCLES, Suit.BAMBOOS, Suit.CHARACTERS)

    /** perm[i] = family carrying the (i+1, i+4, i+7) tiles. */
    private val KNITTED_PERMUTATIONS: List<List<Suit>> = listOf(
        listOf(SUITS[0], SUITS[1], SUITS[2]), listOf(SUITS[0], SUITS[2], SUITS[1]),
        listOf(SUITS[1], SUITS[0], SUITS[2]), listOf(SUITS[1], SUITS[2], SUITS[0]),
        listOf(SUITS[2], SUITS[0], SUITS[1]), listOf(SUITS[2], SUITS[1], SUITS[0]),
    )

    /** The 9 knitted tiles, grouped three by three per family. */
    private fun knittedTiles(perm: List<Suit>): List<Tile> =
        perm.withIndex().flatMap { (i, suit) -> listOf(1, 4, 7).map { Tile.of(suit, it + i) } }

    // ---- special hands ----------------------------------------------------------------------

    private val ORPHANS: List<Tile> =
        Tile.PLAYABLE.filter { it.isHonor || it.isTerminal }

    private fun specialHand(input: HandInput, counts: IntArray): HandView? {
        val tiles = input.concealed
        fun view(kind: SpecialHand) = HandView(input, emptyList(), null, kind, WinPosition.NONE, tiles)

        if (counts.all { it % 2 == 0 }) return view(SpecialHand.SEVEN_PAIRS)

        if (ORPHANS.all { counts[it.index] >= 1 } && tiles.all { it in ORPHANS }) {
            return view(SpecialHand.THIRTEEN_ORPHANS)
        }

        if (counts.all { it <= 1 }) {
            val honors = tiles.count { it.isHonor }
            val suited = tiles.filter { it.isSuited }
            val knitted = KNITTED_PERMUTATIONS.any { perm ->
                suited.all { t -> (t.rank - 1) % 3 == perm.indexOf(t.suit) }
            }
            if (knitted) {
                return when (honors) {
                    7 -> view(SpecialHand.KNITTED_GRAND)
                    5, 6 -> view(SpecialHand.KNITTED_PETIT)
                    else -> null
                }
            }
        }
        return null
    }
}
