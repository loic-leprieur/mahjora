@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package fr.mahjora.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import fr.mahjora.AppContainer
import fr.mahjora.agents.scoring.Payments
import fr.mahjora.agents.scoring.ScoreResult
import fr.mahjora.agents.scoring.SeatRotation
import fr.mahjora.agents.storage.Game
import fr.mahjora.agents.storage.NewHand
import fr.mahjora.core.Circumstance
import fr.mahjora.core.HandInput
import fr.mahjora.core.Meld
import fr.mahjora.core.MeldKind
import fr.mahjora.core.Tile
import fr.mahjora.core.WinMethod
import fr.mahjora.core.Wind
import kotlinx.coroutines.launch

@Composable
fun HandEntryScreen(
    c: AppContainer,
    gameId: Long,
    savedState: SavedStateHandle,
    onScan: () -> Unit,
    onDone: () -> Unit,
) {
    var game by remember { mutableStateOf<Game?>(null) }
    var handNumber by remember { mutableIntStateOf(0) }
    LaunchedEffect(gameId) {
        game = c.storage.getGame(gameId)
        handNumber = c.storage.listHands(gameId).size
    }

    val concealed = remember { mutableStateListOf<Tile>() }
    val declared = remember { mutableStateListOf<Meld>() }
    val circumstances = remember { mutableStateListOf<Circumstance>() }
    var flowers by remember { mutableIntStateOf(0) }
    var winning by remember { mutableStateOf<Tile?>(null) }
    var winner by remember { mutableStateOf<Int?>(null) }
    var discarder by remember { mutableStateOf<Int?>(null) }
    var method by remember { mutableStateOf(WinMethod.DISCARD) }
    var showMeldDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val capacity = 14 - 3 * declared.size

    // Tiles read by the camera.
    val scan by savedState.getStateFlow<ArrayList<String>?>(SCAN_RESULT_KEY, null).collectAsState()
    LaunchedEffect(scan) {
        val codes = scan ?: return@LaunchedEffect
        val tiles = codes.map(Tile::parse)
        concealed.clear()
        concealed.addAll(tiles.filter { !it.isFlower }.take(capacity))
        flowers = tiles.count { it.isFlower }.coerceAtMost(8)
        winning = concealed.lastOrNull()
        savedState[SCAN_RESULT_KEY] = null
    }

    fun copies(t: Tile) = concealed.count { it == t } + declared.sumOf { m ->
        when (m.kind) {
            MeldKind.CHOW, MeldKind.KNITTED -> if (t in m.tiles) 1 else 0
            MeldKind.PUNG -> if (m.first == t) 3 else 0
            MeldKind.KONG -> if (m.first == t) 4 else 0
        }
    }

    fun addTile(t: Tile) {
        if (concealed.size >= capacity || copies(t) >= 4) return
        concealed += t
        winning = t
    }

    val g = game ?: return
    val seat = winner?.let { SeatRotation.seatWind(it, handNumber) } ?: Wind.EAST
    val input = winning?.takeIf { it in concealed && concealed.size == capacity }?.let {
        HandInput(
            concealed.toList(), declared.toList(), flowers, it, method, circumstances.toSet(),
            seatWind = seat, roundWind = SeatRotation.roundWind(handNumber),
        )
    }
    val score: ScoreResult? = input?.let { c.scoring.score(it) }
    val canSave = score != null && score.valid && score.meetsMinimum && winner != null &&
        (method == WinMethod.SELF_DRAW || (discarder != null && discarder != winner))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Main ${handNumber + 1}") },
                navigationIcon = { IconButton(onDone) { Icon(Icons.Filled.Close, "Annuler") } },
                actions = { IconButton(onScan) { Icon(Icons.Filled.CameraAlt, "Scanner avec l'appareil photo") } },
            )
        },
        bottomBar = {
            Button(
                enabled = canSave,
                onClick = {
                    val s = score!!
                    val w = winner!!
                    val from = if (method == WinMethod.DISCARD) discarder else null
                    scope.launch {
                        c.storage.saveHand(
                            NewHand(
                                gameId, handNumber, w, from, method, s.total,
                                Payments.deltas(w, from, s.total).toList(), s.combos, input,
                            ),
                        )
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Enregistrer la main") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Vainqueur")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                g.players.forEachIndexed { slot, p ->
                    FilterChip(winner == slot, { winner = slot }, label = { Text("${p.name} (${SeatRotation.seatWind(slot, handNumber).label})") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(method == WinMethod.DISCARD, { method = WinMethod.DISCARD }, label = { Text("Sur défausse") })
                FilterChip(method == WinMethod.SELF_DRAW, { method = WinMethod.SELF_DRAW }, label = { Text("Tirée soi-même") })
            }
            if (method == WinMethod.DISCARD) {
                Text("Défausse de :")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    g.players.forEachIndexed { slot, p ->
                        if (slot != winner) FilterChip(discarder == slot, { discarder = slot }, label = { Text(p.name) })
                    }
                }
            }

            SectionTitle("Main cachée (${concealed.size}/$capacity)")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                concealed.sorted().forEach { t ->
                    TileView(t, selected = t == winning, onClick = { winning = t })
                }
            }
            if (concealed.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({
                        concealed.removeLastOrNull()
                        if (winning !in concealed) winning = concealed.lastOrNull()
                    }) { Text("Retirer la dernière") }
                    TextButton({ concealed.clear(); winning = null }) { Text("Vider") }
                }
                Text("Touchez une tuile pour la désigner comme tuile gagnante (surlignée).", style = MaterialTheme.typography.bodySmall)
            }
            TilePalette(onPick = ::addTile)

            SectionTitle("Figures déclarées")
            declared.forEachIndexed { i, m ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    m.tiles.forEach { TileView(it, width = 34.dp) }
                    Text(meldLabel(m), Modifier.weight(1f))
                    IconButton({ declared.removeAt(i) }) { Icon(Icons.Filled.Close, "Retirer") }
                }
            }
            OutlinedButton({ showMeldDialog = true }, enabled = declared.size < 4) { Text("Ajouter une figure déclarée") }

            SectionTitle("Fleurs et saisons")
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton({ if (flowers > 0) flowers-- }) { Icon(Icons.Filled.Remove, "Moins") }
                Text("$flowers", style = MaterialTheme.typography.titleMedium)
                IconButton({ if (flowers < 8) flowers++ }) { Icon(Icons.Filled.Add, "Plus") }
            }

            SectionTitle("Circonstances")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Circumstance.entries.forEach { ci ->
                    FilterChip(
                        ci in circumstances,
                        { if (ci in circumstances) circumstances.remove(ci) else circumstances.add(ci) },
                        label = { Text(ci.label) },
                    )
                }
            }

            ScorePanel(score, concealed.size, capacity)
            if (score != null && score.valid && !score.meetsMinimum) {
                Text("Il faut au moins 8 points pour déclarer Mahjong.", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showMeldDialog) {
        MeldDialog(
            onDismiss = { showMeldDialog = false },
            onPick = { m ->
                declared += m
                while (concealed.size > 14 - 3 * declared.size) concealed.removeAt(concealed.lastIndex)
                if (winning !in concealed) winning = concealed.lastOrNull()
                showMeldDialog = false
            },
        )
    }
}

private fun meldLabel(m: Meld) = when (m.kind) {
    MeldKind.CHOW -> "Chow"
    MeldKind.PUNG -> "Pung"
    MeldKind.KONG -> if (m.concealed) "Kong caché" else "Kong exposé"
    MeldKind.KNITTED -> "Serpentin"
}

@Composable
private fun MeldDialog(onDismiss: () -> Unit, onPick: (Meld) -> Unit) {
    var kind by remember { mutableStateOf(MeldKind.PUNG) }
    var hidden by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Fermer") } },
        title = { Text("Figure déclarée") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(MeldKind.CHOW to "Chow", MeldKind.PUNG to "Pung", MeldKind.KONG to "Kong").forEach { (k, l) ->
                        FilterChip(kind == k, { kind = k }, label = { Text(l) })
                    }
                }
                if (kind == MeldKind.KONG) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Switch(hidden, { hidden = it })
                        Text(" Kong caché")
                    }
                }
                Text(if (kind == MeldKind.CHOW) "Touchez la plus petite tuile du chow :" else "Touchez la tuile :")
                TilePalette(tileWidth = 26.dp, onPick = { t ->
                    when (kind) {
                        MeldKind.CHOW -> if (t.isSuited && t.rank <= 7) onPick(Meld.chow(t))
                        MeldKind.PUNG -> onPick(Meld.pung(t))
                        else -> onPick(Meld.kong(t, concealed = hidden))
                    }
                })
            }
        },
    )
}

@Composable
fun ScorePanel(score: ScoreResult?, have: Int, need: Int) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                score == null -> Text("Complétez la main ($have/$need tuiles) et désignez la tuile gagnante.")
                !score.valid -> Text(score.error ?: "Main invalide", color = MaterialTheme.colorScheme.error)
                else -> {
                    Text("${score.total} points", style = MaterialTheme.typography.headlineSmall)
                    score.combos.forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (it.count > 1) "${it.name} ×${it.count}" else it.name)
                            Text("${it.total}")
                        }
                    }
                }
            }
        }
    }
}
