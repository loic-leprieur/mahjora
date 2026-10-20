package fr.mahjora

import android.app.Application
import android.content.Context
import fr.mahjora.agents.scoring.ScoringAgent
import fr.mahjora.agents.scoring.StandardScoringAgent
import fr.mahjora.agents.storage.SqliteStorageAgent
import fr.mahjora.agents.storage.StorageAgent
import fr.mahjora.agents.vision.ModelTileDetectionAgent
import fr.mahjora.agents.vision.TileDetectionAgent

/** Wires the three agents; swap an implementation here (e.g. a TFLite-backed detector). */
class AppContainer(context: Context) {
    val scoring: ScoringAgent = StandardScoringAgent()
    val vision: TileDetectionAgent = ModelTileDetectionAgent()
    val storage: StorageAgent = SqliteStorageAgent(context)
}

class MahjoraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
