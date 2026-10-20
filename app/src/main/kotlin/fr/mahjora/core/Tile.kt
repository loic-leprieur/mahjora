package fr.mahjora.core

enum class Suit { CIRCLES, BAMBOOS, CHARACTERS, WIND, DRAGON, FLOWER }

/**
 * One of the 42 tile kinds. [index] is stable and doubles as the class index of the vision model:
 * 0-8 cercles, 9-17 bambous, 18-26 caractères, 27-30 vents (E S O N), 31-33 dragons (rouge, blanc, vert),
 * 34-41 fleurs/saisons.
 */
class Tile private constructor(val index: Int) : Comparable<Tile> {

    val suit: Suit
        get() = when {
            index < 9 -> Suit.CIRCLES
            index < 18 -> Suit.BAMBOOS
            index < 27 -> Suit.CHARACTERS
            index < 31 -> Suit.WIND
            index < 34 -> Suit.DRAGON
            else -> Suit.FLOWER
        }

    /** 1-based rank inside the suit (1-9 for families, 1-4 winds, 1-3 dragons, 1-8 flowers). */
    val rank: Int
        get() = index - OFFSETS.getValue(suit) + 1

    val isSuited get() = index < 27
    val isWind get() = index in 27..30
    val isDragon get() = index in 31..33
    val isHonor get() = index in 27..33
    val isFlower get() = index >= 34
    val isTerminal get() = isSuited && (rank == 1 || rank == 9)
    val isSimple get() = isSuited && rank in 2..8

    /** Next tile of the same family (for chows); caller must make sure rank < 9. */
    fun plus(n: Int): Tile = ALL[index + n]

    val code: String
        get() = when (suit) {
            Suit.CIRCLES -> "c$rank"
            Suit.BAMBOOS -> "b$rank"
            Suit.CHARACTERS -> "k$rank"
            Suit.WIND -> "w" + "eson"[rank - 1]
            Suit.DRAGON -> "d" + "rwg"[rank - 1]
            Suit.FLOWER -> "f$rank"
        }

    val label: String
        get() = when (suit) {
            Suit.CIRCLES -> "$rank Cercles"
            Suit.BAMBOOS -> "$rank Bambous"
            Suit.CHARACTERS -> "$rank Caractères"
            Suit.WIND -> listOf("Est", "Sud", "Ouest", "Nord")[rank - 1]
            Suit.DRAGON -> listOf("Dragon rouge", "Dragon blanc", "Dragon vert")[rank - 1]
            Suit.FLOWER -> if (rank <= 4) "Fleur $rank" else "Saison ${rank - 4}"
        }

    override fun compareTo(other: Tile) = index.compareTo(other.index)
    override fun equals(other: Any?) = other is Tile && other.index == index
    override fun hashCode() = index
    override fun toString() = code

    companion object {
        const val KIND_COUNT = 42
        const val PLAYABLE_COUNT = 34

        private val OFFSETS = mapOf(
            Suit.CIRCLES to 0, Suit.BAMBOOS to 9, Suit.CHARACTERS to 18,
            Suit.WIND to 27, Suit.DRAGON to 31, Suit.FLOWER to 34,
        )

        val ALL: List<Tile> = List(KIND_COUNT) { Tile(it) }
        val PLAYABLE: List<Tile> = ALL.subList(0, PLAYABLE_COUNT)
        private val BY_CODE: Map<String, Tile> = ALL.associateBy { it.code }

        fun of(suit: Suit, rank: Int): Tile = ALL[OFFSETS.getValue(suit) + rank - 1]
        fun fromIndex(index: Int): Tile = ALL[index]
        fun parse(code: String): Tile =
            BY_CODE[code.trim().lowercase()] ?: throw IllegalArgumentException("Tuile inconnue : $code")

        /** Parses "c1 c2 c3 b4 we" style strings. */
        fun parseAll(codes: String): List<Tile> =
            codes.split(Regex("\\s+")).filter { it.isNotBlank() }.map(::parse)
    }
}

enum class Wind(val label: String) {
    EAST("Est"), SOUTH("Sud"), WEST("Ouest"), NORTH("Nord");

    val tile: Tile get() = Tile.of(Suit.WIND, ordinal + 1)
}
