package fr.mahjora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mahjora.core.Suit
import fr.mahjora.core.Tile

private val Green = Color(0xFF0B6B3A)

@Composable
fun MahjoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFC8E6C9),
            onPrimaryContainer = Color(0xFF00391C),
            secondary = Color(0xFF4F7A28),
            secondaryContainer = Color(0xFFDDEBC8),
            onSecondaryContainer = Color(0xFF1B3300),
            background = Color(0xFFF7FBF4),
            surface = Color(0xFFF7FBF4),
        ),
        content = content,
    )
}

private fun Tile.color(): Color = when (suit) {
    Suit.CIRCLES -> Color(0xFF1E3A9E)
    Suit.BAMBOOS -> Color(0xFF0B7A3B)
    Suit.CHARACTERS -> Color(0xFFB3261E)
    Suit.WIND -> Color(0xFF1E3A9E)
    Suit.DRAGON -> when (rank) {
        1 -> Color(0xFFB3261E)
        2 -> Color(0xFF1E3A9E)
        else -> Color(0xFF0B7A3B)
    }
    Suit.FLOWER -> Color(0xFF8A4FA0)
}

/** (big text, small caption) drawn on a tile. */
private fun Tile.face(): Pair<String, String> = when (suit) {
    Suit.CIRCLES -> "$rank" to "●"
    Suit.BAMBOOS -> "$rank" to "竹"
    Suit.CHARACTERS -> "$rank" to "萬"
    Suit.WIND -> "東南西北"[rank - 1].toString() to ""
    Suit.DRAGON -> "中白發"[rank - 1].toString() to ""
    Suit.FLOWER -> "$rank" to if (rank <= 4) "花" else "季"
}

@Composable
fun TileView(
    tile: Tile,
    modifier: Modifier = Modifier,
    width: Dp = 40.dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val (big, small) = tile.face()
    val color = tile.color()
    Surface(
        modifier = modifier
            .size(width, width * 1.45f)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(5.dp),
        color = if (selected) Color(0xFFFFF3C4) else Color(0xFFFAF7EE),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFE0A800) else Color(0xFF9E9E9E)),
        shadowElevation = 1.dp,
    ) {
        Column(Modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(big, color = color, fontWeight = FontWeight.Bold, fontSize = (width.value * 0.6f).sp, lineHeight = (width.value * 0.65f).sp)
            if (small.isNotEmpty()) Text(small, color = color, fontWeight = FontWeight.Bold, fontSize = (width.value * 0.42f).sp, lineHeight = (width.value * 0.45f).sp)
        }
    }
}

/** All 34 playable tiles; [onPick] is called when one is tapped. */
@Composable
fun TilePalette(onPick: (Tile) -> Unit, modifier: Modifier = Modifier, tileWidth: Dp = 38.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(Suit.CIRCLES, Suit.BAMBOOS, Suit.CHARACTERS).forEach { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Tile.PLAYABLE.filter { it.suit == s }.forEach { t -> TileView(t, width = tileWidth, onClick = { onPick(t) }) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Tile.PLAYABLE.filter { it.isHonor }.forEach { t -> TileView(t, width = tileWidth, onClick = { onPick(t) }) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
