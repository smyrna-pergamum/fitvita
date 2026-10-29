package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.GameState
import io.github.smyrna_pergamum.exercisetracker.domain.Badge
import io.github.smyrna_pergamum.exercisetracker.domain.BadgeState

private data class BadgeLook(val icon: ImageVector, val color: Color, val title: Int, val desc: Int)

private fun look(b: Badge): BadgeLook = when (b) {
    Badge.FIRST_STEPS -> BadgeLook(Icons.AutoMirrored.Filled.DirectionsWalk, Accent.Steps, R.string.b_first_steps, R.string.b_first_steps_d)
    Badge.STEPS_10K -> BadgeLook(Icons.AutoMirrored.Filled.DirectionsWalk, Accent.Steps, R.string.b_steps_10k, R.string.b_steps_10k_d)
    Badge.STEPS_20K -> BadgeLook(Icons.AutoMirrored.Filled.DirectionsRun, Accent.StepsEnd, R.string.b_steps_20k, R.string.b_steps_20k_d)
    Badge.FIRST_WORKOUT -> BadgeLook(Icons.Filled.FitnessCenter, Accent.Move, R.string.b_first_workout, R.string.b_first_workout_d)
    Badge.WORKOUTS_10 -> BadgeLook(Icons.Filled.FitnessCenter, Accent.Move, R.string.b_workouts_10, R.string.b_workouts_10_d)
    Badge.WORKOUTS_50 -> BadgeLook(Icons.Filled.MilitaryTech, Accent.Move, R.string.b_workouts_50, R.string.b_workouts_50_d)
    Badge.FIRST_YOGA -> BadgeLook(Icons.Filled.SelfImprovement, Accent.Mind, R.string.b_first_yoga, R.string.b_first_yoga_d)
    Badge.YOGA_10 -> BadgeLook(Icons.Filled.SelfImprovement, Accent.Mind, R.string.b_yoga_10, R.string.b_yoga_10_d)
    Badge.FIRST_ROUTE -> BadgeLook(Icons.Filled.Map, Accent.Route, R.string.b_first_route, R.string.b_first_route_d)
    Badge.ROUTE_5K -> BadgeLook(Icons.AutoMirrored.Filled.DirectionsRun, Accent.Route, R.string.b_route_5k, R.string.b_route_5k_d)
    Badge.TOTAL_42K -> BadgeLook(Icons.Filled.EmojiEvents, Accent.Route, R.string.b_total_42k, R.string.b_total_42k_d)
    Badge.HYDRATED -> BadgeLook(Icons.Filled.WaterDrop, Accent.Water, R.string.b_hydrated, R.string.b_hydrated_d)
    Badge.HYDRATED_7 -> BadgeLook(Icons.Filled.WaterDrop, Accent.Water, R.string.b_hydrated_7, R.string.b_hydrated_7_d)
    Badge.STREAK_3 -> BadgeLook(Icons.Filled.LocalFireDepartment, Accent.Steps, R.string.b_streak_3, R.string.b_streak_3_d)
    Badge.STREAK_7 -> BadgeLook(Icons.Filled.LocalFireDepartment, Accent.StepsEnd, R.string.b_streak_7, R.string.b_streak_7_d)
    Badge.STREAK_30 -> BadgeLook(Icons.Filled.LocalFireDepartment, Accent.Move, R.string.b_streak_30, R.string.b_streak_30_d)
    Badge.PERFECT_DAY -> BadgeLook(Icons.Filled.Star, Accent.Xp, R.string.b_perfect_day, R.string.b_perfect_day_d)
    Badge.EARLY_BIRD -> BadgeLook(Icons.Filled.WbSunny, Accent.Xp, R.string.b_early_bird, R.string.b_early_bird_d)
    Badge.LEVEL_5 -> BadgeLook(Icons.Filled.WorkspacePremium, Accent.Violet, R.string.b_level_5, R.string.b_level_5_d)
    Badge.LEVEL_10 -> BadgeLook(Icons.Filled.WorkspacePremium, Accent.Mind, R.string.b_level_10, R.string.b_level_10_d)
    Badge.FIRST_WEIGH -> BadgeLook(Icons.Filled.MonitorWeight, Accent.Body, R.string.b_first_weigh, R.string.b_first_weigh_d)
    Badge.WEIGH_30 -> BadgeLook(Icons.Filled.MonitorWeight, Accent.Body, R.string.b_weigh_30, R.string.b_weigh_30_d)
    Badge.WEIGHT_GOAL -> BadgeLook(Icons.Filled.Flag, Accent.Body, R.string.b_weight_goal, R.string.b_weight_goal_d)
}

private fun rank(level: Int) = when {
    level < 3 -> R.string.rank_1
    level < 5 -> R.string.rank_2
    level < 10 -> R.string.rank_3
    level < 20 -> R.string.rank_4
    else -> R.string.rank_5
}

/** Level, streak, how points are earned, and the badge collection. */
@Composable
fun RewardsScreen(game: GameState?, nav: NavHostController) {
    val g = game ?: return
    val unlocked = g.badges.count { it.unlocked }
    LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tab_rewards), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SettingsAction(nav)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            GradientCard(LocalLook.current.hero) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Text("${g.level.level}", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(rank(g.level.level)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.xp_total, g.xpTotal))
                    }
                }
                LinearProgressIndicator(
                    progress = { g.level.intoLevel.toFloat() / g.level.forNext }, color = Accent.Xp, trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(12.dp).clip(RoundedCornerShape(6.dp)),
                )
                Text(stringResource(R.string.to_next_level, g.level.forNext - g.level.intoLevel, g.level.level + 1), modifier = Modifier.padding(top = 6.dp))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientCard(Accent.steps(), Modifier.weight(1f)) {
                    Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(32.dp))
                    Text(stringResource(R.string.days_n, g.streak), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.streak), style = MaterialTheme.typography.bodySmall)
                }
                GradientCard(Accent.water(), Modifier.weight(1f)) {
                    Icon(Icons.Filled.EmojiEvents, null, Modifier.size(32.dp))
                    Text("$unlocked / ${g.badges.size}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.badges), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.how_xp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.how_xp_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.badges), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(g.badges, key = { it.badge.name }) { BadgeTile(it) }
    }
}

@Composable
private fun BadgeTile(b: BadgeState) {
    val l = look(b.badge)
    val title = stringResource(l.title)
    val desc = stringResource(l.desc)
    val state = stringResource(if (b.unlocked) R.string.badge_unlocked else R.string.badge_locked)
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "$title. $desc. $state" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(if (b.unlocked) l.color else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(l.icon, null, tint = if (b.unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
        }
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        Text(if (b.unlocked) desc else "${b.progress} / ${b.target}", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}
