package fr.mahjora.agents.scoring

import fr.mahjora.core.HandInput
import fr.mahjora.core.Meld
import fr.mahjora.core.MeldKind
import fr.mahjora.core.Suit
import fr.mahjora.core.Tile
import fr.mahjora.core.WinMethod

enum class SpecialHand { NONE, SEVEN_PAIRS, THIRTEEN_ORPHANS, KNITTED_GRAND, KNITTED_PETIT }

enum class WinPosition { NONE, EDGE, MIDDLE, PAIR }

/**
 * One way of reading a winning hand (4 figures + pair, or a special hand), with the position of the
 * winning tile fixed. Combination rules are evaluated against this view.
 */
class HandView(
    val input: HandInput,
    val melds: List<Meld>,
    val pair: Tile?,
    val special: SpecialHand,
    val winPosition: WinPosition,
    val tiles: List<Tile>,
) {
    val isStandard get() = special == SpecialHand.NONE

    val chows = melds.filter { it.kind == MeldKind.CHOW }
    val pungs = melds.filter { it.isPungLike }
    val kongs = melds.filter { it.kind == MeldKind.KONG }
    val windPungs = pungs.filter { it.first.isWind }
    val dragonPungs = pungs.filter { it.first.isDragon }
    val concealedPungs = pungs.filter { it.concealed }
    val concealedKongs = kongs.filter { it.concealed }
    val exposedKongs = kongs.filter { !it.concealed }

    /** True when every declared figure is a concealed kong (nothing was taken from a discard). */
    val handConcealed = input.declared.all { it.concealed }
    val selfDraw = input.winMethod == WinMethod.SELF_DRAW

    val suitsPresent: Set<Suit> = tiles.filter { it.isSuited }.map { it.suit }.toSet()

    val chowKeys: List<Pair<Suit, Int>> = chows.map { it.first.suit to it.first.rank }
}
