package com.halovoid.bunori.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.halovoid.bunori.R
import com.halovoid.bunori.data.repository.UpdateRepository
import com.halovoid.bunori.ui.core.components.ContextualBottomBar
import com.halovoid.bunori.ui.navigation.NavGraph
import com.halovoid.bunori.ui.navigation.Screen
import com.halovoid.bunori.ui.navigation.AppNavigationManager
import com.halovoid.bunori.ui.core.theme.*

/**
 * The primary entry point Composable for the UI.
 * Manages the [NavGraph] within a Scaffold with a persistent [NavigationBar]:
 * Library | Browse | Activity | More.
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val mainTabs = remember {
        listOf(
            TabInfo(Screen.Library, "Library", R.drawable.anim_library_enter),
            TabInfo(Screen.Browse, "Browse", R.drawable.anim_browse_enter),
            TabInfo(Screen.Activity, "Activity", R.drawable.anim_activity_enter),
            TabInfo(Screen.Support, "More", R.drawable.anim_more_enter)
        )
    }

    val showNavBar = remember(currentDestination) {
        mainTabs.any { tab -> 
            currentDestination?.hierarchy?.any { it.route == tab.screen.route } == true 
        }
    }

    val contextualConfig by AppNavigationManager.contextualBottomBar.collectAsStateWithLifecycle()

    val isAppUpdateAvailable by UpdateRepository.getInstance(navController.context)
        .isAppUpdateAvailable.collectAsStateWithLifecycle()
    val isCrawlerUpdateAvailable by UpdateRepository.getInstance(navController.context)
        .isCrawlerUpdateAvailable.collectAsStateWithLifecycle()

    LaunchedEffect(navController) {
        AppNavigationManager.navigationEvents.collect { route ->
            try {
                navController.navigate(route) {
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                android.util.Log.e("MainScreen", "Failed to navigate to route: $route", e)
            }
        }
    }

    Scaffold(
        bottomBar = {
            Box(
                contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (showNavBar) {
                    BunoriNavigationBar(
                        mainTabs = mainTabs,
                        currentDestination = currentDestination,
                        isAppUpdateAvailable = isAppUpdateAvailable,
                        isCrawlerUpdateAvailable = isCrawlerUpdateAvailable,
                        onNavigate = { route ->
                            AppNavigationManager.clearContextualBottomBar()
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                ContextualBottomBar(
                    visible = contextualConfig.visible,
                    selectedCount = contextualConfig.selectedCount,
                    actions = contextualConfig.actions
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            color = MaterialTheme.colorScheme.background
        ) {
            NavGraph(navController = navController)
        }
    }
}

@Composable
private fun BunoriNavigationBar(
    mainTabs: List<TabInfo>,
    currentDestination: androidx.navigation.NavDestination?,
    isAppUpdateAvailable: Boolean,
    isCrawlerUpdateAvailable: Boolean,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = DarkBackground,
        tonalElevation = 8.dp
    ) {
        mainTabs.forEach { tab ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == tab.screen.route } == true
            NavigationBarItem(
                icon = { 
                    BadgedBox(
                        badge = {
                            if (tab.label == "More" && isAppUpdateAvailable) {
                                Badge(
                                    containerColor = BrandAccent,
                                    contentColor = Color.White
                                ) {
                                    Text("1")
                                }
                            } else if (tab.label == "Browse" && isCrawlerUpdateAvailable) {
                                Badge(
                                    containerColor = BrandAccent,
                                    contentColor = Color.White
                                ) {
                                    Text("1")
                                }
                            }
                        }
                    ) {
                        AnimatedTabIcon(
                            isSelected = isSelected,
                            animResId = tab.animResId,
                            contentDescription = tab.label
                        )
                    }
                },
                label = { 
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) BrandAccent else SecondaryText
                    ) 
                },
                selected = isSelected,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandAccent,
                    unselectedIconColor = SecondaryText,
                    selectedTextColor = BrandAccent,
                    unselectedTextColor = SecondaryText,
                    indicatorColor = BrandAccent.copy(alpha = 0.2f)
                ),
                onClick = { onNavigate(tab.screen.route) }
            )
        }
    }
}

data class TabInfo(
    val screen: Screen,
    val label: String,
    val animResId: Int
)

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun AnimatedTabIcon(
    isSelected: Boolean,
    animResId: Int,
    contentDescription: String? = null
) {
    val image = AnimatedImageVector.animatedVectorResource(animResId)
    val painter = rememberAnimatedVectorPainter(animatedImageVector = image, atEnd = isSelected)

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "TabScale"
    )

    Icon(
        painter = painter,
        contentDescription = contentDescription,
        modifier = Modifier
            .size(24.dp)
            .scale(scale)
    )
}
