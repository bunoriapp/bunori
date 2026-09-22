package com.halovoid.bunori.ui.navigation

import android.app.Application
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.halovoid.bunori.data.repository.PreferenceRepository
import com.halovoid.bunori.ui.ViewModelFactory
import com.halovoid.bunori.ui.feature.browse.BrowseScreen
import com.halovoid.bunori.ui.feature.browse.BrowseViewModel
import com.halovoid.bunori.ui.feature.source.ExtensionInfoScreen
import com.halovoid.bunori.ui.feature.activity.ActivityScreen
import com.halovoid.bunori.ui.feature.activity.ActivityViewModel
import com.halovoid.bunori.ui.feature.activity.JobDetailScreen
import com.halovoid.bunori.ui.feature.library.LibraryScreen
import com.halovoid.bunori.ui.feature.library.LibraryViewModel
import com.halovoid.bunori.ui.feature.novel.NovelArtifactsScreen
import com.halovoid.bunori.ui.feature.novel.NovelScreen
import com.halovoid.bunori.ui.feature.novel.NovelViewModel
import com.halovoid.bunori.ui.feature.onboarding.ExtensionRepoScreen
import com.halovoid.bunori.ui.feature.onboarding.FolderScreen
import com.halovoid.bunori.ui.feature.onboarding.FolderViewModel
import com.halovoid.bunori.ui.feature.onboarding.PermissionScreen
import com.halovoid.bunori.ui.feature.onboarding.ThemeOnboardingScreen
import com.halovoid.bunori.ui.feature.onboarding.WelcomeScreen
import com.halovoid.bunori.ui.feature.reader.ReaderScreen
import com.halovoid.bunori.ui.feature.reader.ReaderViewModel
import com.halovoid.bunori.ui.feature.search.SearchScreen
import com.halovoid.bunori.ui.feature.search.SearchViewModel
import com.halovoid.bunori.ui.feature.settings.AdvancedSettingsScreen
import com.halovoid.bunori.ui.feature.settings.BackupSettingsScreen
import com.halovoid.bunori.ui.feature.settings.GeneralPreferencesScreen
import com.halovoid.bunori.ui.feature.settings.ExtensionSettingsScreen
import com.halovoid.bunori.ui.feature.settings.LayoutSettingsScreen
import com.halovoid.bunori.ui.feature.settings.ManualCookieScreen
import com.halovoid.bunori.ui.feature.settings.MoreScreen
import com.halovoid.bunori.ui.feature.settings.ReaderSettingsScreen
import com.halovoid.bunori.ui.feature.settings.SettingsViewModel
import com.halovoid.bunori.ui.feature.settings.SupportSettingsScreen
import com.halovoid.bunori.ui.feature.settings.ThemeSettingsScreen
import com.halovoid.bunori.ui.feature.settings.UpdateDetailScreen
import com.halovoid.bunori.ui.feature.settings.WebViewSettingsScreen
import com.halovoid.bunori.ui.feature.source.SourceScreen
import com.halovoid.bunori.ui.feature.source.SourceViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object OnboardingTheme : Screen("onboarding_theme")
    object Permissions : Screen("permissions")
    object OnboardingExtensionRepo : Screen("onboarding_extension_repo")
    object FolderSelection: Screen("folder_selection")
    object Browse : Screen("browse")

    object Search : Screen("search?source={source}") {
        fun createRoute(source: String? = null) = if (source != null) {
            "search?source=${URLEncoder.encode(source, "UTF-8")}"
        } else {
            "search"
        }
    }
    object Library : Screen("library")
    object History : Screen("history")
    object Activity : Screen("activity")
    object Sources : Screen("sources")
    object Support : Screen("support")
    object ReaderSettings : Screen("reader_settings")
    object GeneralPreferences : Screen("general_preferences")
    object LayoutSettings : Screen("layout_settings")
    object ThemeSettings : Screen("theme_settings")
    object ExtensionSettings : Screen("extension_settings")
    object ExtensionInfo : Screen("extension_info/{extensionId}") {
        fun createRoute(extensionId: String) = "extension_info/${URLEncoder.encode(extensionId, "UTF-8")}"
    }
    object AdvancedSettings : Screen("advanced_settings")
    object WebViewSettings : Screen("webview_settings")
    object ManualCookies : Screen("manual_cookies")
    object SupportSettings : Screen("support_settings")
    object BackupSettings : Screen("backup_settings")
    object UpdateDetail : Screen("update_detail")
    object JobDetail : Screen("job_detail/{batchId}") {
        fun createRoute(batchId: String) = "job_detail/${URLEncoder.encode(batchId, "UTF-8")}"
    }

    object Novel : Screen("novel/{crawlerName}/{novelUrl}") {
        fun createRoute(crawlerName: String, novelUrl: String) = 
            "novel/${URLEncoder.encode(crawlerName, "UTF-8")}/${URLEncoder.encode(novelUrl, "UTF-8")}"
    }
    object NovelArtifacts : Screen("novel_artifacts/{novelUrl}") {
        fun createRoute(novelUrl: String) = "novel_artifacts/${URLEncoder.encode(novelUrl, "UTF-8")}"
    }
    object Reader : Screen("reader/{novelUrl}/{initialChapterId}") {
        fun createRoute(novelUrl: String, initialChapterId: Int) = 
            "reader/${URLEncoder.encode(novelUrl, "UTF-8")}/$initialChapterId"
    }
}

@Composable
fun NavGraph(navController: NavHostController) {
    val application = LocalContext.current.applicationContext as Application
    val preferenceRepository = remember { PreferenceRepository.getInstance(application) }
    val scope = rememberCoroutineScope()
    
    var startRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val onboardingCompleted = preferenceRepository.isOnboardingCompleted.first()
        startRoute = if (onboardingCompleted) {
            Screen.Library.route
        } else {
            Screen.Welcome.route
        }
    }

    if (startRoute == null) return

    val mainTabRoutes = remember {
        setOf(Screen.Library.route, Screen.Browse.route, Screen.Activity.route, Screen.Support.route)
    }

    val density = LocalDensity.current
    val slideDistance = remember(density) { with(density) { 30.dp.roundToPx() } }

    NavHost(
        navController = navController,
        startDestination = startRoute!!,
        enterTransition = {
            val isTabSwitch = initialState.destination.route in mainTabRoutes && targetState.destination.route in mainTabRoutes
            if (isTabSwitch) {
                fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing))
            } else {
                slideInHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { slideDistance } + fadeIn(
                    animationSpec = tween(210, delayMillis = 90, easing = FastOutSlowInEasing)
                )
            }
        },
        exitTransition = {
            val isTabSwitch = initialState.destination.route in mainTabRoutes && targetState.destination.route in mainTabRoutes
            if (isTabSwitch) {
                fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing))
            } else {
                slideOutHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { -slideDistance } + fadeOut(
                    animationSpec = tween(90, easing = FastOutLinearInEasing)
                )
            }
        },
        popEnterTransition = {
            val isTabSwitch = initialState.destination.route in mainTabRoutes && targetState.destination.route in mainTabRoutes
            if (isTabSwitch) {
                fadeIn(animationSpec = tween(200, easing = LinearOutSlowInEasing))
            } else {
                slideInHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { -slideDistance } + fadeIn(
                    animationSpec = tween(210, delayMillis = 90, easing = FastOutSlowInEasing)
                )
            }
        },
        popExitTransition = {
            val isTabSwitch = initialState.destination.route in mainTabRoutes && targetState.destination.route in mainTabRoutes
            if (isTabSwitch) {
                fadeOut(animationSpec = tween(150, easing = FastOutLinearInEasing))
            } else {
                slideOutHorizontally(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) { slideDistance } + fadeOut(
                    animationSpec = tween(90, easing = FastOutLinearInEasing)
                )
            }
        }
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onNext = {
                    navController.navigate(Screen.FolderSelection.route)
                }
            )
        }
        composable(Screen.FolderSelection.route) {
            val folderViewModel: FolderViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            FolderScreen(
                viewModel = folderViewModel,
                onNext = {
                    navController.navigate(Screen.OnboardingTheme.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.OnboardingTheme.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            ThemeOnboardingScreen(
                viewModel = settingsViewModel,
                onNext = {
                    navController.navigate(Screen.Permissions.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Permissions.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            PermissionScreen(
                viewModel = settingsViewModel,
                onNext = {
                    navController.navigate(Screen.OnboardingExtensionRepo.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.OnboardingExtensionRepo.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            ExtensionRepoScreen(
                viewModel = settingsViewModel,
                onComplete = {
                    scope.launch {
                        preferenceRepository.setOnboardingCompleted(true)
                        navController.navigate(Screen.Library.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Browse.route) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(navController.graph.id)
            }
            val browseViewModel: BrowseViewModel = viewModel(
                viewModelStoreOwner = parentEntry,
                factory = remember { ViewModelFactory(application) }
            )
            val crawlerViewModel: SourceViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            BrowseScreen(
                viewModel = browseViewModel,
                sourceViewModel = crawlerViewModel,
                onNavigateToSearch = { sourceName ->
                    navController.navigate(Screen.Search.createRoute(sourceName))
                },
                onNavigateToExtensionSettings = {
                    navController.navigate(Screen.ExtensionSettings.route)
                },
                onNavigateToExtensionInfo = { extensionId ->
                    navController.navigate(Screen.ExtensionInfo.createRoute(extensionId))
                }
            )
        }
        composable(
            route = Screen.Search.route,
            arguments = listOf(
                navArgument("source") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val sourceParam = backStackEntry.arguments?.getString("source")?.let {
                try { URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
            }
            val searchViewModel: SearchViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            val browseViewModel: BrowseViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )

            SearchScreen(
                viewModel = searchViewModel,
                browseViewModel = browseViewModel,
                initialSource = sourceParam,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { crawlerName, novelUrl ->
                    navController.navigate(Screen.Novel.createRoute(crawlerName, novelUrl))
                }
            )
        }
        composable(Screen.Library.route) {
            val factory = remember { ViewModelFactory(application) }
            val libraryViewModel: LibraryViewModel = viewModel(factory = factory)
            LibraryScreen(
                viewModel = libraryViewModel,
                onNovelClick = { crawlerName, novelUrl ->
                    navController.navigate(Screen.Novel.createRoute(crawlerName, novelUrl))
                }
            )
        }
        composable(Screen.Sources.route) {
            val crawlerViewModel: SourceViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            SourceScreen(
                viewModel = crawlerViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToExtensionSettings = {
                    navController.navigate(Screen.ExtensionSettings.route)
                },
                onNavigateToExtensionInfo = { extensionId ->
                    navController.navigate(Screen.ExtensionInfo.createRoute(extensionId))
                }
            )
        }
        composable(Screen.ExtensionInfo.route) { backStackEntry ->
            val encodedId = backStackEntry.arguments?.getString("extensionId") ?: ""
            val extensionId = URLDecoder.decode(encodedId, "UTF-8")
            val crawlerViewModel: SourceViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            ExtensionInfoScreen(
                extensionId = extensionId,
                viewModel = crawlerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.JobDetail.route) { backStackEntry ->
            val encodedId = backStackEntry.arguments?.getString("batchId") ?: ""
            val batchId = URLDecoder.decode(encodedId, "UTF-8")

            JobDetailScreen (
                batchId = batchId,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Activity.route) {
            val activityViewModel: ActivityViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            ActivityScreen(
                viewModel = activityViewModel,
                onRequestClick = { requestId: String ->
                    navController.navigate(Screen.JobDetail.createRoute(requestId))
                }
            )
        }
        composable(Screen.Support.route) {
            val settingsViewModel = rememberSettingsViewModel(navController, it, application)
            MoreScreen(
                viewModel = settingsViewModel,
                onNavigateToLayout = {
                    navController.navigate(Screen.LayoutSettings.route)
                },
                onNavigateToThemeSettings = {
                    navController.navigate(Screen.ThemeSettings.route)
                },
                onNavigateToReaderSettings = {
                    navController.navigate(Screen.ReaderSettings.route)
                },
                onNavigateToDownloadsPref = {
                    navController.navigate(Screen.GeneralPreferences.route)
                },
                onNavigateToExtensionSettings = {
                    navController.navigate(Screen.ExtensionSettings.route)
                },
                onNavigateToAdvanced = {
                    navController.navigate(Screen.AdvancedSettings.route)
                },
                onNavigateToBackupSettings = {
                    navController.navigate(Screen.BackupSettings.route)
                },
                onNavigateToSupportSettings = {
                    navController.navigate(Screen.SupportSettings.route)
                },
                onNavigateToUpdate = {
                    navController.navigate(Screen.UpdateDetail.route)
                }
            )
        }
        composable(Screen.ReaderSettings.route) { backStackEntry ->
            val settingsViewModel = rememberSettingsViewModel(navController, backStackEntry, application)
            ReaderSettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.GeneralPreferences.route) { backStackEntry ->
            val settingsViewModel = rememberSettingsViewModel(navController, backStackEntry, application)
            GeneralPreferencesScreen (
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.LayoutSettings.route) { backStackEntry ->
            val settingsViewModel = rememberSettingsViewModel(navController, backStackEntry, application)
            LayoutSettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ThemeSettings.route) { backStackEntry ->
            val settingsViewModel = rememberSettingsViewModel(navController, backStackEntry, application)
            ThemeSettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.ExtensionSettings.route) { backStackEntry ->
            val settingsViewModel = rememberSettingsViewModel(navController, backStackEntry, application)
            ExtensionSettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.AdvancedSettings.route) { backStackEntry ->
            val settingsViewModel = rememberSettingsViewModel(navController, backStackEntry, application)
            AdvancedSettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToWebView = {
                    navController.navigate(Screen.WebViewSettings.route)
                }
            )
        }
        composable(Screen.WebViewSettings.route) { backStackEntry ->
            val settingsViewModel = rememberSettingsViewModel(navController, backStackEntry, application)
            WebViewSettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onNavigateToManualCookies = {
                    navController.navigate(Screen.ManualCookies.route)
                }
            )
        }
        composable(Screen.ManualCookies.route) {
            ManualCookieScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.BackupSettings.route) { backStackEntry ->
            val settingsViewModel = rememberSettingsViewModel(navController, backStackEntry, application)
            BackupSettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.UpdateDetail.route) { backStackEntry ->
            val settingsViewModel = rememberSettingsViewModel(navController, backStackEntry, application)
            UpdateDetailScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.SupportSettings.route) {
            SupportSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Novel.route) { backStackEntry ->
            val novelUrl = URLDecoder.decode(
                backStackEntry.arguments?.getString("novelUrl") ?: "",
                "UTF-8"
            )
            NovelScreen(
                novelUrl = novelUrl,
                onChapterClick = { url, chapterId ->
                    navController.navigate(Screen.Reader.createRoute(url, chapterId))
                },
                onBack = {
                    navController.popBackStack()
                },
                onArtifactsClick = {
                    navController.navigate(Screen.NovelArtifacts.createRoute(novelUrl))
                }
            )
        }
        composable(Screen.NovelArtifacts.route) { backStackEntry ->
            val novelUrl = URLDecoder.decode(
                backStackEntry.arguments?.getString("novelUrl") ?: "",
                "UTF-8"
            )
            val viewModel: NovelViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )
            LaunchedEffect(novelUrl) {
                viewModel.loadNovel(novelUrl)
            }
            val artifacts by viewModel.artifacts.collectAsStateWithLifecycle()
            val novel by viewModel.novel.collectAsStateWithLifecycle()

            NovelArtifactsScreen(
                novel = novel,
                artifacts = artifacts,
                onBack = { navController.popBackStack() },
                onDownload = { _ -> },
                viewModel = viewModel
            )
        }
        composable(Screen.Reader.route) { backStackEntry ->
            val novelUrl = URLDecoder.decode(
                backStackEntry.arguments?.getString("novelUrl") ?: "",
                "UTF-8"
            )
            val initialChapterId = backStackEntry.arguments?.getString("initialChapterId")?.toIntOrNull() ?: -1

            val viewModel: ReaderViewModel = viewModel(
                factory = remember { ViewModelFactory(application) }
            )

            ReaderScreen(
                novelUrl = novelUrl,
                initialChapterId = initialChapterId,
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun rememberSettingsViewModel(
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
    application: Application
): SettingsViewModel {
    val owner = remember(backStackEntry) {
        navController.getBackStackEntry(navController.graph.id)
    }
    return viewModel(
        viewModelStoreOwner = owner,
        factory = remember { ViewModelFactory(application) }
    )
}
