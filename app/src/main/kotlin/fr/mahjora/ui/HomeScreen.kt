@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.mahjora.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mahjora.AppContainer
import fr.mahjora.agents.storage.GameSummary
import fr.mahjora.agents.storage.Player
import fr.mahjora.core.Wind
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Main screen: create a game on top, previous games below. */
@Composable
fun HomeScreen(c: AppContainer, onOpen: (Long) -> Unit, onRules: () -> Unit) {
    var games by remember { mutableStateOf<List<GameSummary>?>(null) }
    var knownPlayers by remember { mutableStateOf<List<Player>>(emptyList()) }
    val names = remember { mutableStateListOf("", "", "", "") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        games = c.storage.listGames()
        knownPlayers = c.storage.listPlayers()
    }

    val cleaned = names.map { it.trim() }
    val ready = cleaned.all { it.isNotEmpty() } && cleaned.map { it.lowercase() }.toSet().size == 4

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Mahjora") },
            actions = { IconButton(onRules) { Icon(Icons.Filled.MenuBook, contentDescription = "Règles et combinaisons") } },
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nouvelle partie", style = MaterialTheme.typography.titleMedium)
                        Wind.entries.forEachIndexed { i, wind ->
                            OutlinedTextField(
                                value = names[i],
                                onValueChange = { names[i] = it },
                                label = { Text("Joueur ${wind.label}") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        val suggestions = knownPlayers.filter { p -> cleaned.none { it.equals(p.name, ignoreCase = true) } }
                        if (suggestions.isNotEmpty()) {
                            Text("Joueurs connus", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                suggestions.forEach { p ->
                                    AssistChip(
                                        onClick = {
                                            val free = names.indexOfFirst { it.isBlank() }
                                            if (free >= 0) names[free] = p.name
                                        },
                                        label = { Text(p.name) },
                                    )
                                }
                            }
                        }
                        Button(
                            enabled = ready,
                            onClick = {
                                scope.launch {
                                    val players = cleaned.map { c.storage.getOrCreatePlayer(it) }
                                    val game = c.storage.createGame(null, players)
                                    names.indices.forEach { names[it] = "" }
                                    onOpen(game.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Commencer la partie") }
                    }
                }
            }

            val list = games.orEmpty()
            if (list.isNotEmpty()) {
                item { Text("Parties précédentes", style = MaterialTheme.typography.titleMedium) }
                items(list, key = { it.game.id }) { s ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(s.game.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                s.game.name ?: DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.game.startedAt)),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            s.game.players.forEachIndexed { i, p ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(p.name)
                                    Text(signed(s.totals[i]))
                                }
                            }
                            Text("${s.handCount} main(s)" + if (s.game.endedAt != null) " · terminée" else "")
                        }
                    }
                }
            }
        }
    }
}

fun signed(n: Int) = if (n > 0) "+$n" else "$n"
