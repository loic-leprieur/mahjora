@file:OptIn(ExperimentalMaterial3Api::class)

package fr.mahjora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mahjora.AppContainer

/** Reference list of the 82 combinations, searchable, from the scoring agent's catalog. */
@Composable
fun RulesScreen(c: AppContainer, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val combos = c.scoring.catalog
        .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true) }
        .sortedWith(compareBy({ it.points }, { it.name }))

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Combinaisons") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, label = { Text("Rechercher") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(combos, key = { it.id }) { d ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Pill("${d.points} pt${if (d.points > 1) "s" else ""}")
                        Column {
                            Text(d.name, style = MaterialTheme.typography.titleSmall)
                            Text(d.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
