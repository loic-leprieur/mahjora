@file:OptIn(ExperimentalMaterial3Api::class)

package fr.mahjora.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mahjora.AppContainer
import fr.mahjora.agents.storage.GameSummary
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(c: AppContainer, onNewGame: () -> Unit, onOpen: (Long) -> Unit, onRules: () -> Unit) {
    var games by remember { mutableStateOf<List<GameSummary>?>(null) }
    LaunchedEffect(Unit) { games = c.storage.listGames() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mahjora") },
                actions = { IconButton(onRules) { Icon(Icons.Filled.MenuBook, contentDescription = "Règles et combinaisons") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewGame, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Nouvelle partie") })
        },
    ) { pad ->
        val list = games
        when {
            list == null -> Unit
            list.isEmpty() -> Text("Aucune partie pour le moment.", Modifier.padding(pad).padding(24.dp))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(list, key = { it.game.id }) { s ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(s.game.id) }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                s.game.name ?: DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.game.startedAt)),
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
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
