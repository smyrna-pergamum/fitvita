package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.smyrna_pergamum.exercisetracker.domain.Goals
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.data.Settings

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val TABS = listOf(
    Tab("activity", R.string.tab_activity, Icons.AutoMirrored.Filled.DirectionsWalk),
    Tab("workout", R.string.tab_workout, Icons.Filled.FitnessCenter),
    Tab("yoga", R.string.tab_yoga, Icons.Filled.Spa),
    Tab("rewards", R.string.tab_rewards, Icons.Filled.EmojiEvents),
    Tab("radio", R.string.radio, Icons.Filled.Radio),
)

@Composable
fun AppNav(app: App, repo: Repository, settings: Settings) {
    val nav = androidx.navigation.compose.rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route?.substringBefore('?')
    val showTabs = TABS.any { it.route == route }
    // Points, levels and badges, shared by the home and reward screens.
    val game by remember { repo.game(app.settings.flow.map { Goals(it.stepGoal, it.waterGoal, it.goalKg) }.distinctUntilChanged()) }.collectAsState(initial = null)

    StartupPermissions(app)
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Without the tab bar the player sits right above the system navigation bar.
            androidx.compose.foundation.layout.Column(if (showTabs) Modifier else Modifier.navigationBarsPadding()) {
            MiniPlayer(app)
            if (showTabs) NavigationBar {
                TABS.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo("activity"); launchSingleTop = true } },
                        icon = { Icon(t.icon, null) },
                        label = { Text(stringResource(t.label), maxLines = 1) },
                    )
                }
            }
            }
        },
    ) { pad ->
        NavHost(nav, "activity", Modifier.padding(pad).imePadding()) {
            composable("workout") { WorkoutTab(app, repo, settings, nav) }
            composable("exercises?pick={pick}", arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })) { e ->
                ExercisesScreen(repo, nav, pick = e.arguments?.getBoolean("pick") == true)
            }
            composable("exercise/{id}") { e -> ExerciseDetail(repo, nav, e.arguments!!.getString("id")!!) }
            composable("activity") { ActivityTab(app, repo, settings, game, nav) }
            composable("yoga") { YogaScreen(app, nav) }
            composable("rewards") { RewardsScreen(game, nav) }
            composable("breathe/{min}") { e -> CalmSession(app, repo, nav, "ex:calm:" + e.arguments?.getString("min")) }
            composable("calm/{spec}") { e -> CalmSession(app, repo, nav, e.arguments!!.getString("spec")!!) }
            composable("timer/{tab}") { e -> TimerScreen(nav, e.arguments?.getString("tab")?.toIntOrNull() ?: 0) }
            composable("activity/{id}") { e -> ActivityDetail(app, repo, nav, e.arguments!!.getString("id")!!) }
            composable("history") { HistoryScreen(repo, nav) }
            composable("done/{id}") { e -> WorkoutDetail(repo, settings, nav, e.arguments!!.getString("id")!!) }
            composable("settings") { SettingsScreen(app, settings, nav) }
            composable("radio?back={back}", arguments = listOf(navArgument("back") { type = NavType.BoolType; defaultValue = false })) { e ->
                RadioScreen(app, nav, back = e.arguments?.getBoolean("back") == true)
            }
            composable("body") { BodyScreen(app, repo, settings, nav) }
            composable("preview/{source}") { e -> WorkoutPreview(repo, nav, e.arguments!!.getString("source")!!) }
            composable("guided/{id}") { e -> GuidedWorkout(app, repo, settings, nav, e.arguments!!.getString("id")!!) }
        }
    }
}
