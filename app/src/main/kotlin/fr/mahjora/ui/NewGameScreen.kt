@file:OptIn(ExperimentalMaterial3Api::class)

package fr.mahjora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mahjora.AppContainer
import fr.mahjora.core.Wind
import kotlinx.coroutines.launch

@Composable
fun NewGameScreen(c: AppContainer, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val names = remember { mutableStateListOf("", "", "", "") }
    val scope = rememberCoroutineScope()
    val ready = names.all { it.isNotBlank() } && names.map { it.trim().lowercase() }.toSet().size == 4

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Nouvelle partie") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Joueurs, dans l'ordre des places au départ.")
            Wind.entries.forEachIndexed { i, wind ->
                OutlinedTextField(
                    value = names[i],
                    onValueChange = { names[i] = it },
                    label = { Text("Joueur ${wind.label}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                enabled = ready,
                onClick = {
                    scope.launch {
                        val players = names.map { c.storage.getOrCreatePlayer(it) }
                        onCreated(c.storage.createGame(null, players).id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Commencer") }
        }
    }
}
