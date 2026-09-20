package com.halovoid.bunori.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.halovoid.bunori.data.repository.UpdateRepository
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
            TabInfo(Screen.Library, "Library", Icons.AutoMirrored.Outlined.LibraryBooks, Icons.AutoMirrored.Filled.LibraryBooks),
            TabInfo(Screen.Browse, "Browse", Icons.Outlined.Explore, Icons.Filled.Explore),
            TabInfo(Screen.Activity, "Activity", Icons.Outlined.Schedule, Icons.Filled.Schedule),
            TabInfo(Screen.Support, "More", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz)
        )
    }

    val showNavBar = remember(currentDestination) {
        mainTabs.any { tab -> 
            currentDestination?.hierarchy?.any { it.route == tab.screen.route } == true 
        }
    }

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
            if (showNavBar) {
                BunoriNavigationBar(
                    mainTabs = mainTabs,
                    currentDestination = currentDestination,
                    isAppUpdateAvailable = isAppUpdateAvailable,
                    isCrawlerUpdateAvailable = isCrawlerUpdateAvailable,
                    onNavigate = { route ->
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
                            outlinedIcon = tab.outlinedIcon,
                            filledIcon = tab.filledIcon,
                            label = tab.label
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
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector
)

@Composable
fun AnimatedTabIcon(
    isSelected: Boolean,
    outlinedIcon: ImageVector,
    filledIcon: ImageVector,
    label: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "TabAnimation")
    
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Scale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isSelected && label == "Browse") 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "Rotation"
    )

    val clockRotation by animateFloatAsState(
        targetValue = if (isSelected && label == "Activity") 360f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "ClockRotation"
    )

    val tilt by animateFloatAsState(
        targetValue = if (isSelected && label == "Library") -10f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "Tilt"
    )

    val bounce by animateDpAsState(
        targetValue = if (isSelected && (label == "Updates" || label == "History")) (-3).dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
        label = "Bounce"
    )

    val waveOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveOffset"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .rotate(
                when (label) {
                    "Browse" -> rotation
                    "Activity" -> clockRotation
                    else -> tilt
                }
            )
            .offset(
                y = when (label) {
                    "Updates", "History" -> bounce
                    "More" -> if (isSelected) waveOffset.dp else 0.dp
                    else -> 0.dp
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) filledIcon else outlinedIcon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}
