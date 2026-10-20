package fr.mahjora.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.mahjora.AppContainer

/** Key used to hand the tiles read by the camera back to the hand entry screen. */
const val SCAN_RESULT_KEY = "scan"

@Composable
fun MahjoraNav(c: AppContainer) {
    val nav = rememberNavController()
    val gameArg = listOf(navArgument("id") { type = NavType.LongType })

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                c,
                onOpen = { nav.navigate("game/$it") },
                onRules = { nav.navigate("rules") },
            )
        }
        composable("game/{id}", arguments = gameArg) { e ->
            val id = e.arguments!!.getLong("id")
            GameScreen(c, id, onBack = { nav.popBackStack() }, onNewHand = { nav.navigate("hand/$id") })
        }
        composable("hand/{id}", arguments = gameArg) { e ->
            HandEntryScreen(
                c, e.arguments!!.getLong("id"),
                savedState = e.savedStateHandle,
                onScan = { nav.navigate("scan") },
                onDone = { nav.popBackStack() },
            )
        }
        composable("scan") {
            ScanScreen(
                c,
                onBack = { nav.popBackStack() },
                onResult = { codes ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(SCAN_RESULT_KEY, ArrayList(codes))
                    nav.popBackStack()
                },
            )
        }
        composable("rules") { RulesScreen(c, onBack = { nav.popBackStack() }) }
    }
}
