package fr.mahjora.agents.scoring

import fr.mahjora.core.Circumstance
import fr.mahjora.core.MeldKind
import fr.mahjora.core.Suit
import fr.mahjora.core.Tile

/**
 * A combination of the Vent d'Est table (82 entries). [count] returns how many times it scores in a
 * given reading of the hand (0 = absent). [excludes] lists the combinations it makes redundant.
 *
 * The exclusion table is not printed on the memos: it follows the usual Mahjong-compétition logic
 * and should be checked against the federation rulebook.
 */
class ComboDefinition(
    val id: Int,
    val name: String,
    val points: Int,
    val description: String,
    val excludes: Set<Int>,
    val count: (HandView) -> Int,
)

object ComboCatalog {
    const val NO_VALUE_HAND = 43
    const val FLOWER = 81

    val ALL: List<ComboDefinition> by lazy { build() }
    val BY_ID: Map<Int, ComboDefinition> by lazy { ALL.associateBy { it.id } }

    private fun Boolean.n() = if (this) 1 else 0

    private val GREEN = Tile.parseAll("b2 b3 b4 b6 b8 dg").toSet()
    private val SYMMETRIC =
        Tile.parseAll("c1 c2 c3 c4 c5 c8 c9 b2 b4 b5 b6 b8 b9 dw").toSet()

    private fun HandView.allSuited(range: IntRange) = tiles.all { it.isSuited && it.rank in range }

    private fun List<Int>.isRun(): Boolean = zipWithNext().all { (a, b) -> b - a == 1 }

    /** True when ranks contain [n] consecutive values. */
    private fun Collection<Int>.hasRun(n: Int): Boolean =
        any { a -> (0 until n).all { (a + it) in this } }

    private fun distinctTriple(a: Collection<Suit>, b: Collection<Suit>, c: Collection<Suit>) =
        a.any { x -> b.any { y -> c.any { z -> x != y && y != z && x != z } } }

    private fun HandView.chowSuits(rank: Int) = chows.filter { it.first.rank == rank }.map { it.first.suit }

    private fun HandView.pungSuits(rank: Int) =
        pungs.filter { it.first.isSuited && it.first.rank == rank }.map { it.first.suit }

    private fun HandView.pungRanksIn(suit: Suit) =
        pungs.filter { it.first.suit == suit }.map { it.first.rank }.toSet()

    private fun nineGates(v: HandView): Boolean {
        if (v.input.declared.isNotEmpty() || v.tiles.size != 14) return false
        val suit = v.tiles.first().suit
        if (!v.tiles.all { it.isSuited && it.suit == suit }) return false
        val c = IntArray(10)
        v.tiles.forEach { c[it.rank]++ }
        val base = intArrayOf(0, 3, 1, 1, 1, 1, 1, 1, 1, 3)
        return (1..9).all { c[it] >= base[it] }
    }

    private fun sevenShiftedPairs(v: HandView): Boolean {
        if (v.special != SpecialHand.SEVEN_PAIRS) return false
        val kinds = v.tiles.toSet()
        if (kinds.size != 7 || !kinds.all { it.isSuited }) return false
        if (kinds.map { it.suit }.toSet().size != 1) return false
        return kinds.map { it.rank }.sorted().isRun()
    }

    private fun quadruples(v: HandView): Int {
        val counts = v.tiles.groupingBy { it }.eachCount()
        return counts.count { (t, n) -> n == 4 && v.kongs.none { it.first == t } }
    }

    private fun build(): List<ComboDefinition> {
        val list = ArrayList<ComboDefinition>()
        fun c(id: Int, name: String, pts: Int, desc: String, ex: Set<Int> = emptySet(), count: (HandView) -> Int) {
            list += ComboDefinition(id, name, pts, desc, ex, count)
        }

        // ---- 1 point -------------------------------------------------------------------------
        c(69, "Double Chow pur", 1, "2 chows identiques de même famille") { v ->
            v.chowKeys.groupingBy { it }.eachCount().values.sumOf { it / 2 }
        }
        c(70, "Double Chow", 1, "2 chows identiques dans 2 familles différentes") { v ->
            v.chows.groupBy { it.first.rank }.values.count { g -> g.map { it.first.suit }.toSet().size >= 2 }
        }
        c(71, "Petite Suite pure", 1, "2 chows consécutifs dans une même famille") { v ->
            v.chowKeys.any { (s, r) -> (s to r + 1) in v.chowKeys }.n()
        }
        c(72, "Deux Chows purs d'Extrémité", 1, "Chows 1-2-3 et 7-8-9 d'une même famille") { v ->
            Suit.entries.count { s -> (s to 1) in v.chowKeys && (s to 7) in v.chowKeys }
        }
        c(73, "Pung de Vent ou d'Extrémité", 1, "Pung de 1, de 9 ou de vent (hors vent du joueur et vent dominant)") { v ->
            v.pungs.count {
                it.first.isTerminal || (it.first.isWind && it.first != v.input.seatWind.tile &&
                    it.first != v.input.roundWind.tile)
            }
        }
        c(74, "Kong exposé", 1, "Avoir un Kong exposé") { it.exposedKongs.size }
        c(75, "Une Famille absente", 1, "Jeu dans lequel manque l'une des 3 familles") { (it.suitsPresent.size == 2).n() }
        c(76, "Pas d'Honneur", 1, "Aucun honneur dans le jeu") { v -> v.tiles.none { it.isHonor }.n() }
        c(77, "Finir sur le Côté", 1, "MJ sur le 3 d'un chow 1-2-3 ou le 7 d'un chow 7-8-9") { (it.winPosition == WinPosition.EDGE).n() }
        c(78, "Finir au Milieu", 1, "MJ sur la tuile centrale d'un chow") { (it.winPosition == WinPosition.MIDDLE).n() }
        c(79, "Finir sur la Paire", 1, "MJ en complétant sa paire") { (it.winPosition == WinPosition.PAIR).n() }
        c(80, "Tirer soi-même", 1, "MJ sur une tuile tirée de la muraille") { it.selfDraw.n() }
        c(FLOWER, "Fleur / Saison", 1, "Point bonus par fleur ou saison, ajouté à la valeur du MJ") { it.input.flowers }

        // ---- 2 points ------------------------------------------------------------------------
        c(68, "Tout Ordinaire", 2, "Uniquement des tuiles du 2 au 8", setOf(76)) { v -> v.allSuited(2..8).n() }
        c(67, "Kong caché", 2, "Avoir un Kong caché") { it.concealedKongs.size }
        c(66, "Deux Pungs cachés", 2, "Avoir deux pungs cachés") { (it.concealedPungs.size >= 2).n() }
        c(65, "Double Pung", 2, "Deux pungs de même valeur") { v ->
            (1..9).any { r -> v.pungSuits(r).toSet().size >= 2 }.n()
        }
        c(64, "Quatre Identiques", 2, "4 exemplaires de la même tuile (hors kong)") { quadruples(it) }
        c(63, "Tout Chow", 2, "Uniquement des chows et une paire non honneur", setOf(76)) { v ->
            (v.chows.size == 4 && v.pair?.isHonor == false).n()
        }
        c(62, "Tout Caché donné", 2, "Jeu caché, MJ sur une tuile donnée") { (it.handConcealed && !it.selfDraw).n() }
        c(61, "Vent du Joueur", 2, "Pung du vent de sa place") { v -> v.pungs.any { it.first == v.input.seatWind.tile }.n() }
        c(60, "Vent Dominant", 2, "Pung du vent du tour") { v -> v.pungs.any { it.first == v.input.roundWind.tile }.n() }
        c(59, "Pung de Dragon", 2, "Avoir un pung de dragon") { it.dragonPungs.size }

        // ---- 4 points ------------------------------------------------------------------------
        c(58, "Dernière Tuile existante", 4, "MJ sur une tuile dont les 3 autres exemplaires sont déjà visibles") {
            (Circumstance.LAST_EXISTING_TILE in it.input.circumstances).n()
        }
        c(57, "Deux Kongs exposés", 4, "Avoir deux Kongs exposés", setOf(74)) { (it.exposedKongs.size == 2).n() }
        c(56, "Tout Caché tiré", 4, "Jeu entièrement caché, MJ sur une tuile tirée", setOf(62, 80)) { (it.handConcealed && it.selfDraw).n() }
        c(55, "Extrémité ou Honneur partout", 4, "Chaque figure et la paire contiennent une extrémité ou un honneur") { v ->
            (v.isStandard && v.melds.size == 4 && v.pair != null &&
                v.melds.all { m -> m.tiles.any { it.isTerminal || it.isHonor } } &&
                (v.pair.isTerminal || v.pair.isHonor)).n()
        }

        // ---- 6 points ------------------------------------------------------------------------
        c(54, "Deux Dragons", 6, "Deux pungs de dragon", setOf(59)) { (it.dragonPungs.size >= 2).n() }
        c(53, "Tout Exposé", 6, "MJ sur la paire par une tuile donnée, 4 figures exposées") { v ->
            (v.input.declared.size == 4 && v.input.declared.all { !it.concealed } && !v.selfDraw).n()
        }
        c(52, "Tout Type", 6, "Une figure de chaque famille, un vent et un dragon") { v ->
            (v.suitsPresent.size == 3 && v.tiles.any { it.isWind } && v.tiles.any { it.isDragon }).n()
        }
        c(51, "3 Chows superposés", 6, "3 chows des 3 familles décalés de 1") { v ->
            (1..5).any { a -> distinctTriple(v.chowSuits(a), v.chowSuits(a + 1), v.chowSuits(a + 2)) }.n()
        }
        c(50, "Semi-Pure", 6, "Une seule famille avec des honneurs") { v ->
            (v.suitsPresent.size == 1 && v.tiles.any { it.isHonor }).n()
        }
        c(49, "Tout Pung", 6, "Uniquement des pungs (ou des kongs)") { (it.pungs.size == 4).n() }
        c(82, "Kong Caché + Kong Exposé", 6, "Un Kong caché et un Kong exposé", setOf(74, 67)) { v ->
            (v.concealedKongs.isNotEmpty() && v.exposedKongs.isNotEmpty()).n()
        }

        // ---- 8 points ------------------------------------------------------------------------
        c(48, "Deux Kongs cachés", 8, "Avoir 2 kongs cachés", setOf(67, 66)) { (it.concealedKongs.size == 2).n() }
        c(47, "Voler le Kong", 8, "MJ sur la tuile d'un pung transformé en kong par un adversaire") { (Circumstance.ROBBING_KONG in it.input.circumstances).n() }
        c(46, "Finir sur le Kong", 8, "MJ sur la tuile de compensation d'un kong") { (Circumstance.KONG_REPLACEMENT in it.input.circumstances).n() }
        c(45, "Dernière Tuile jetée", 8, "MJ sur l'ultime défausse de la partie") { (Circumstance.LAST_DISCARD in it.input.circumstances).n() }
        c(44, "Dernière Tuile tirée", 8, "MJ en piochant l'ultime tuile de la muraille") { (Circumstance.LAST_WALL_TILE in it.input.circumstances).n() }
        c(43, "Main sans valeur", 8, "Main ne comportant aucun point") { 0 }
        c(42, "Trois Pungs consécutifs", 8, "3 pungs de valeur consécutive dans les 3 familles") { v ->
            (1..7).any { a -> distinctTriple(v.pungSuits(a), v.pungSuits(a + 1), v.pungSuits(a + 2)) }.n()
        }
        c(41, "Triple Chow", 8, "3 chows identiques dans les 3 familles", setOf(70)) { v ->
            v.chows.groupBy { it.first.rank }.values.any { g -> g.map { it.first.suit }.toSet().size == 3 }.n()
        }
        c(40, "Symétrie", 8, "Tuiles symétriques uniquement") { v -> v.tiles.all { it in SYMMETRIC }.n() }
        c(39, "Grande Suite", 8, "Chows 1-2-3, 4-5-6, 7-8-9 de familles différentes") { v ->
            distinctTriple(v.chowSuits(1), v.chowSuits(4), v.chowSuits(7)).n()
        }

        // ---- 12 points -----------------------------------------------------------------------
        c(38, "Trois Grands Vents", 12, "3 pungs de vents") { (it.windPungs.size >= 3).n() }
        c(37, "Les Quatre Premiers", 12, "Tuiles du 1 au 4 uniquement", setOf(76)) { v -> v.allSuited(1..4).n() }
        c(36, "Les Quatre Derniers", 12, "Tuiles du 6 au 9 uniquement", setOf(76)) { v -> v.allSuited(6..9).n() }
        c(35, "Suite Serpentin", 12, "Suite 1-4-7, 2-5-8, 3-6-9 avec un élément de chaque famille") { v ->
            v.melds.any { it.kind == MeldKind.KNITTED }.n()
        }
        c(34, "Petit Serpentin", 12, "5 ou 6 honneurs uniques avec des tuiles de la suite serpentin", setOf(62, 56)) {
            (it.special == SpecialHand.KNITTED_PETIT).n()
        }

        // ---- 16 points -----------------------------------------------------------------------
        c(33, "Trois Pungs cachés", 16, "Avoir 3 pungs cachés", setOf(66)) { (it.concealedPungs.size == 3).n() }
        c(32, "Triple Pung", 16, "3 pungs de même valeur dans les 3 familles", setOf(65)) { v ->
            (1..9).any { r -> v.pungSuits(r).toSet().size == 3 }.n()
        }
        c(31, "Cinq Partout", 16, "Une tuile 5 dans chaque élément", setOf(68, 76)) { v ->
            (v.isStandard && v.melds.size == 4 && v.pair?.let { it.isSuited && it.rank == 5 } == true &&
                v.melds.all { m ->
                    when (m.kind) {
                        MeldKind.CHOW -> m.first.rank in 3..5
                        MeldKind.PUNG, MeldKind.KONG -> m.first.isSuited && m.first.rank == 5
                        MeldKind.KNITTED -> false
                    }
                }).n()
        }
        c(30, "Trois Chows purs superposés", 16, "3 chows décalés de 1 ou 2 tuiles dans une même famille", setOf(71)) { v ->
            Suit.entries.any { s ->
                val firsts = v.chows.filter { it.first.suit == s }.map { it.first.rank }.toSet()
                firsts.any { a -> (0..2).all { a + it in firsts } || (a + 4 <= 7 && (0..2).all { a + 2 * it in firsts }) }
            }.n()
        }
        c(29, "Deux Dragons dans 3 familles", 16, "Chows 1-2-3 et 7-8-9 dans 2 familles et paire de 5 de la 3e", setOf(72, 63)) { v ->
            val p = v.pair
            if (v.chows.size != 4 || p == null || !p.isSuited || p.rank != 5) 0 else {
                val others = Suit.entries.filter { it.ordinal < 3 && it != p.suit }
                others.all { s -> (s to 1) in v.chowKeys && (s to 7) in v.chowKeys }.n()
            }
        }
        c(28, "Grande Suite pure", 16, "Suite 1 à 9 dans une même famille", setOf(71, 72)) { v ->
            Suit.entries.any { s -> listOf(1, 4, 7).all { (s to it) in v.chowKeys } }.n()
        }

        // ---- 24 points -----------------------------------------------------------------------
        c(27, "Les Trois Premiers", 24, "Tuiles du 1 au 3 uniquement", setOf(76, 37)) { v -> v.allSuited(1..3).n() }
        c(26, "Les Trois Milieux", 24, "Tuiles du 4 au 6 uniquement", setOf(76, 68)) { v -> v.allSuited(4..6).n() }
        c(25, "Les Trois Derniers", 24, "Tuiles du 7 au 9 uniquement", setOf(76, 36)) { v -> v.allSuited(7..9).n() }
        c(24, "Trois Pungs purs consécutifs", 24, "3 pungs consécutifs dans une même famille") { v ->
            Suit.entries.any { s -> v.pungRanksIn(s).hasRun(3) }.n()
        }
        c(23, "Triple Chow pur", 24, "3 chows identiques dans une même famille", setOf(69)) { v ->
            (v.chowKeys.groupingBy { it }.eachCount().values.any { it >= 3 }).n()
        }
        c(22, "Main Pure", 24, "Tuiles d'une seule famille, sans honneur", setOf(75, 76)) { v ->
            (v.suitsPresent.size == 1 && v.tiles.none { it.isHonor }).n()
        }
        c(21, "Tout Pung pairs", 24, "Uniquement des pungs et la paire de valeur paire", setOf(49, 68, 76)) { v ->
            (v.pungs.size == 4 && v.tiles.all { it.isSuited && it.rank % 2 == 0 }).n()
        }
        c(20, "Grand Serpentin", 24, "Les 7 honneurs avec une suite serpentin incomplète", setOf(62, 56)) {
            (it.special == SpecialHand.KNITTED_GRAND).n()
        }
        c(19, "Sept Paires", 24, "Avoir 7 paires", setOf(62, 56)) { (it.special == SpecialHand.SEVEN_PAIRS).n() }

        // ---- 32 points -----------------------------------------------------------------------
        c(18, "Tout Honneurs et Extrémités", 32, "Uniquement des honneurs et des extrémités", setOf(55, 49, 73)) { v ->
            v.tiles.all { it.isHonor || it.isTerminal }.n()
        }
        c(17, "Trois Kongs", 32, "Avoir trois kongs", setOf(48, 57, 82, 67, 74)) { (it.kongs.size == 3).n() }
        c(16, "Quatre Chows purs superposés", 32, "4 chows décalés de 1 ou 2 tuiles dans une même famille", setOf(30, 71)) { v ->
            if (v.chows.size != 4 || v.chows.map { it.first.suit }.toSet().size != 1) 0 else {
                val r = v.chows.map { it.first.rank }.sorted()
                val steps = r.zipWithNext().map { (a, b) -> b - a }.toSet()
                (steps == setOf(1) || steps == setOf(2)).n()
            }
        }

        // ---- 48 points -----------------------------------------------------------------------
        c(15, "Quatre Pungs purs consécutifs", 48, "4 pungs consécutifs dans une même famille", setOf(24, 49)) { v ->
            (v.pungs.size == 4 && v.pungs.map { it.first.suit }.toSet().size == 1 &&
                v.pungs.all { it.first.isSuited } &&
                v.pungs.map { it.first.rank }.sorted().isRun()).n()
        }
        c(14, "Quadruple Chow pur", 48, "4 chows identiques dans une même famille", setOf(23, 69)) { v ->
            (v.chows.size == 4 && v.chowKeys.toSet().size == 1).n()
        }

        // ---- 64 points -----------------------------------------------------------------------
        c(13, "Deux Dragons dans 1 famille", 64, "2 × chows 1-2-3 et 7-8-9 et paire de 5 dans une même famille", setOf(22, 69, 72, 63, 75, 76)) { v ->
            val p = v.pair
            (v.chows.size == 4 && p != null && p.isSuited && p.rank == 5 &&
                v.chows.all { it.first.suit == p.suit } &&
                v.chows.map { it.first.rank }.sorted() == listOf(1, 1, 7, 7)).n()
        }
        c(12, "Quatre Pungs cachés", 64, "Avoir 4 pungs cachés", setOf(33, 66, 49, 62, 56)) { (it.concealedPungs.size == 4).n() }
        c(11, "Tout Honneur", 64, "Uniquement des honneurs", setOf(18, 49, 55, 76, 73)) { v -> v.tiles.all { it.isHonor }.n() }
        c(10, "Trois Petits Dragons", 64, "2 pungs de dragon et une paire du 3e dragon", setOf(54, 59)) { v ->
            (v.dragonPungs.size == 2 && v.pair?.isDragon == true).n()
        }
        c(9, "Quatre Petits Vents", 64, "3 pungs de vent et une paire du 4e vent", setOf(38)) { v ->
            (v.windPungs.size == 3 && v.pair?.isWind == true).n()
        }
        c(8, "Tout Extrémités", 64, "Uniquement des extrémités", setOf(18, 49, 55, 76, 73)) { v -> v.tiles.all { it.isTerminal }.n() }

        // ---- 88 points -----------------------------------------------------------------------
        c(7, "Treize Lanternes merveilleuses", 88, "Chaque honneur et chaque extrémité, l'un en double", setOf(62, 56, 55, 18)) {
            (it.special == SpecialHand.THIRTEEN_ORPHANS).n()
        }
        c(6, "Sept Paires pures consécutives", 88, "7 paires consécutives dans une même famille", setOf(19, 22, 75, 76, 62, 56)) {
            sevenShiftedPairs(it).n()
        }
        c(5, "Quatre Kongs", 88, "Avoir 4 kongs", setOf(17, 48, 57, 49, 82, 67, 74, 66, 33)) { (it.kongs.size == 4).n() }
        c(4, "Neuf Portes", 88, "Séquence cachée 1112345678999 + une tuile de la famille", setOf(22, 75, 76, 62, 56)) { nineGates(it).n() }
        c(3, "Main Verte", 88, "Uniquement des tuiles vertes") { v -> v.tiles.all { it in GREEN }.n() }
        c(2, "Trois Grands Dragons", 88, "Avoir 3 pungs de dragons", setOf(54, 59)) { (it.dragonPungs.size == 3).n() }
        c(1, "Quatre Grands Vents", 88, "Avoir 4 pungs de vents", setOf(38, 9, 49)) { (it.windPungs.size == 4).n() }

        return list.sortedBy { it.id }
    }
}
