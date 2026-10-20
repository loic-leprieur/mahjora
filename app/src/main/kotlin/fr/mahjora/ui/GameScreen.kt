@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.mahjora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mahjora.AppContainer
import fr.mahjora.agents.scoring.SeatRotation
import fr.mahjora.agents.storage.Game
import fr.mahjora.agents.storage.HandRecord
import fr.mahjora.agents.storage.NewHand
import kotlinx.coroutines.launch

private const val HANDS_PER_GAME = 16

@Composable
fun GameScreen(c: AppContainer, gameId: Long, onBack: () -> Unit, onNewHand: () -> Unit) {
    var game by remember { mutableStateOf<Game?>(null) }
    var hands by remember { mutableStateOf<List<HandRecord>>(emptyList()) }
    var version by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(gameId, version) {
        game = c.storage.getGame(gameId)
        hands = c.storage.listHands(gameId)
    }
    val g = game ?: return
    val next = hands.size
    val totals = List(4) { slot -> hands.sumOf { it.deltas[slot] } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(g.name ?: "Partie") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (next < HANDS_PER_GAME) {
                        Text(
                            "Main ${next + 1} / $HANDS_PER_GAME · vent du tour : ${SeatRotation.roundWind(next).label}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    g.players.forEachIndexed { slot, p ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${p.name} (${SeatRotation.seatWind(slot, next).label})")
                            Text(signed(totals[slot]), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onNewHand, enabled = next < HANDS_PER_GAME) { Text("Nouvelle main") }
                        OutlinedButton(
                            enabled = next < HANDS_PER_GAME,
                            onClick = {
                                scope.launch {
                                    c.storage.saveHand(NewHand(gameId, next, null, null, null, 0, List(4) { 0 }, emptyList(), null))
                                    version++
                                }
                            },
                        ) { Text("Main nulle") }
                    }
                }
            }
            items(hands.reversed(), key = { it.id }) { h ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Main ${h.handNumber + 1}", style = MaterialTheme.typography.titleSmall)
                            if (h.winnerSlot == null) Text("Main nulle") else {
                                Text("${g.players[h.winnerSlot].name} · ${h.mahjongValue} pts" +
                                    if (h.discarderSlot != null) " · sur ${g.players[h.discarderSlot].name}" else " · tirée soi-même")
                                Text(h.combos.joinToString(", ") { if (it.count > 1) "${it.name} ×${it.count}" else it.name },
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (h.id == hands.last().id) {
                            IconButton({ scope.launch { c.storage.deleteHand(h.id); version++ } }) {
                                Icon(Icons.Filled.Delete, "Supprimer la dernière main")
                            }
                        }
                    }
                }
            }
        }
    }
}
