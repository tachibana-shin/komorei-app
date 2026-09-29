package git.shin.komorei.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import git.shin.komorei.data.update.UpdateNotifier
import git.shin.komorei.model.Anime
import git.shin.komorei.sdk.JsChallengeCoordinator
import git.shin.komorei.ui.components.MAX_CONTENT_WIDTH
import git.shin.komorei.ui.components.MainBottomNavigation
import git.shin.komorei.ui.components.MainNavigationRail
import git.shin.komorei.ui.components.dialogs.ChallengeBypassDialog
import git.shin.komorei.ui.deeplink.DeepLinkAction
import git.shin.komorei.ui.deeplink.DeepLinkViewModel
import git.shin.komorei.ui.navigation.Screen
import git.shin.komorei.ui.navigation.mainTabs
import git.shin.komorei.ui.player.PlayerSheetValue
import git.shin.komorei.ui.player.PlayerViewModel
import git.shin.komorei.ui.player.VideoPlayerSheet
import git.shin.komorei.ui.screens.about.AboutScreen
import git.shin.komorei.ui.screens.advanced.AdvancedScreen
import git.shin.komorei.ui.screens.backup.BackupScreen
import git.shin.komorei.ui.screens.home.HomeScreen
import git.shin.komorei.ui.screens.home.HomeViewModel
import git.shin.komorei.ui.screens.insights.InsightsScreen
import git.shin.komorei.ui.screens.library.LibraryScreen
import git.shin.komorei.ui.screens.listing.ListingScreen
import git.shin.komorei.ui.screens.logs.LogsScreen
import git.shin.komorei.ui.screens.notifications.NotificationsScreen
import git.shin.komorei.ui.screens.search.SearchDiscoveryScreen
import git.shin.komorei.ui.screens.search.SourceSearchScreen
import git.shin.komorei.ui.screens.settings.SettingsScreen
import git.shin.komorei.ui.screens.source.SourceBrowserScreen
import git.shin.komorei.ui.screens.source.SourceHomeScreen
import git.shin.komorei.ui.screens.source.SourceSettingsScreen
import git.shin.komorei.ui.screens.sources.SourceReposScreen
import git.shin.komorei.ui.screens.sources.SourcesScreen
import git.shin.komorei.ui.screens.update.UpdateBottomSheet
import git.shin.komorei.ui.screens.update.updateAvailableInfo
import git.shin.komorei.ui.theme.BackgroundDark

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    // Incoming deep links (MainActivity → DeepLinkManager): resolve the URL
    // against the sources and open the target — anime/episode into the player
    // sheet (Lite stubs upgraded inside), listings onto the listing route.
    //
    // A PARAMETER (not a local `hiltViewModel()`) so the whole screen stays
    // constructible in a plain `createComposeRule()` test host, which is not a
    // Hilt component holder — MainScreenTest injects a real one instead.
    deepLinkViewModel: DeepLinkViewModel = hiltViewModel(),
    // Same reasoning for the Home destination's ViewModel, which
    // [MainNavigationHost] would otherwise resolve from Hilt while composing
    // the start destination. The remaining routes are only composed once the
    // user navigates to them and keep their own `hiltViewModel()` defaults.
    homeViewModel: HomeViewModel = hiltViewModel(),
    // Injected by MainActivity (required, no default): the notifier is a Hilt
    // singleton rather than a view model, and there is no way to resolve one from
    // a Composable. Keeping it required also means a test host has to hand in a
    // real one instead of silently composing without update handling.
    updateNotifier: UpdateNotifier,
    navController: NavHostController = rememberNavController(),
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    // Asked for once per composition of the shell. The notifier itself throttles
    // this to once a day and skips any version already dismissed, so a
    // configuration change or a tab switch does not turn into a request.
    LaunchedEffect(Unit) {
        updateNotifier.checkForUpdate(automatic = true)
    }

    val playbackState by playerViewModel.playbackState.collectAsState()

    val deepLinkAction by deepLinkViewModel.action.collectAsState()
    LaunchedEffect(deepLinkAction) {
        when (val action = deepLinkAction) {
            is DeepLinkAction.OpenAnime -> {
                playerViewModel.openAnime(action.anime, action.episode)
                deepLinkViewModel.markConsumed()
            }

            is DeepLinkAction.OpenListing -> {
                navController.navigate(Screen.Listing.createRoute(action.sourceId, action.listing))
                deepLinkViewModel.markConsumed()
            }

            null -> Unit
        }
    }

    val onAnimeSelected: (Anime) -> Unit = { anime ->
        playerViewModel.openAnime(anime)
    }

    // Height of the phone's bottom navigation bar (set from the Scaffold's content
    // padding). The collapsed mini player must never cover it — its bottom corners are
    // raised above this in MiniPlayerGeometry. 0 on wide screens (rail on the left).
    var bottomBarHeightDp by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BackgroundDark),
    ) {
        val isWideScreen = maxWidth > 600.dp
        val isPlayerExpanded = playbackState.sheetValue == PlayerSheetValue.EXPANDED
        val isFullscreen = playbackState.isFullscreen

        // The listing route is a full-screen browsing page — it owns the whole
        // viewport, so the bottom toolbar is hidden there (only in the phone
        // portrait scaffold; wide screens use the rail and are unaffected).
        // The repo-management page is likewise a full-screen sub page.
        val isListingRoute = currentRoute == Screen.Listing.route
        val isSourceReposRoute = currentRoute == Screen.SourceRepos.route
        val isSourceSettingsRoute = currentRoute == Screen.SourceSettings.route
        val isSourceSearchRoute = currentRoute == Screen.SourceSearch.route
        val isNotificationsRoute = currentRoute == Screen.Notifications.route
        val isAdvancedRoute = currentRoute == Screen.Advanced.route
        val isLogsRoute = currentRoute == Screen.Logs.route
        val isAboutRoute = currentRoute == Screen.About.route
        val isInsightsRoute = currentRoute == Screen.Insights.route
        val isBackupsRoute = currentRoute == Screen.Backups.route
        val isSettingsSubPageRoute = isAdvancedRoute || isLogsRoute || isAboutRoute || isInsightsRoute || isBackupsRoute

        Box(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                // TABLET / LANDSCAPE: Navigation Rail on the left
                Row(modifier = Modifier.fillMaxSize()) {
                    MainNavigationRail(
                        tabs = mainTabs,
                        currentRoute = currentRoute,
                        onSelect = { tab ->
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                    )

                    // Main Content — capped + centered on very large screens
                    // (landscape tablets / TVs) so lists never stretch
                    // edge-to-edge; phones and portrait tablets are below the cap.
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().widthIn(max = MAX_CONTENT_WIDTH)) {
                            MainNavigationHost(
                                navController = navController,
                                onAnimeSelect = onAnimeSelected,
                                homeViewModel = homeViewModel,
                                onOpenSearch = { sourceId ->
                                    navController.navigate(Screen.SourceSearch.createRoute(sourceId))
                                },
                                onOpenSourceSearch = { sourceId, query ->
                                    navController.navigate(
                                        Screen.SourceSearch.createRoute(sourceId, query, emptyList()),
                                    )
                                },
                            )
                        }
                    }
                }
            } else {
                // PHONE PORTRAIT: Scaffold with YouTube-style bottom bar hide on full player expand
                Scaffold(
                    bottomBar = {
                        AnimatedVisibility(
                            // SourceHome is a full source page but keeps the shell
                            // navigation visible so the user can jump straight to
                            // Settings (and the other top-level tabs) from it.
                            visible = !isPlayerExpanded && !isFullscreen && !isListingRoute && !isSourceReposRoute && !isSourceSettingsRoute && !isSourceSearchRoute && !isNotificationsRoute && !isSettingsSubPageRoute,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it }),
                        ) {
                            MainBottomNavigation(
                                tabs = mainTabs,
                                currentRoute = currentRoute,
                                onSelect = { tab ->
                                    if (currentRoute != tab.route) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                            )
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxSize(),
                ) { innerPadding ->
                    val actualBottomNavHeight = innerPadding.calculateBottomPadding()
                    bottomBarHeightDp = actualBottomNavHeight

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Screen contents padded so last list items are never cut off behind the bottom bar
                        // (the collapsed floating mini player is an overlay — content scrolls under it)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(bottom = if (isFullscreen) 0.dp else actualBottomNavHeight),
                        ) {
                            MainNavigationHost(
                                navController = navController,
                                onAnimeSelect = onAnimeSelected,
                                homeViewModel = homeViewModel,
                                onOpenListing = { sourceId, listing ->
                                    navController.navigate(Screen.Listing.createRoute(sourceId, listing))
                                },
                                onOpenSearch = { sourceId ->
                                    navController.navigate(Screen.SourceSearch.createRoute(sourceId))
                                },
                                onOpenSourceSearch = { sourceId, query ->
                                    navController.navigate(
                                        Screen.SourceSearch.createRoute(sourceId, query, emptyList()),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // UNIFIED Video Player Overlay Sheet: Outside the adaptive branches to preserve state!
            if (playbackState.sheetValue != PlayerSheetValue.HIDDEN) {
                VideoPlayerSheet(
                    playerViewModel = playerViewModel,
                    onAnimeSelected = onAnimeSelected,
                    onNavigateToCategory = { filters ->
                        // navController.navigate(...)
                    },
                    // Mini player corners sit above the bottom toolbar (0 on wide screens).
                    bottomToolbarPadding = bottomBarHeightDp,
                )
            }

            // JS-challenge bypass (headless WebView + visible browser): when a
            // source request lands on a Cloudflare-style challenge that needs a
            // human, JsChallengeCoordinator.pending goes non-null and this
            // dialog pops over everything (any tab / the player sheet). It is
            // fed from KrxHostImpl.netRequest — see JsChallengeCoordinator.
            val pendingChallenge by JsChallengeCoordinator.pending.collectAsState()
            pendingChallenge?.let { challenge ->
                ChallengeBypassDialog(
                    url = challenge.url,
                    onNext = { JsChallengeCoordinator.completeBypass() },
                    onDismiss = { JsChallengeCoordinator.cancelBypass() },
                )
            }

            // App update. Mounted at the end of the shell for the same reason
            // the challenge dialog is: a new release is worth saying out loud
            // wherever the reader happens to be, and a check fired on launch
            // would otherwise land on whichever tab happened to be in front.
            //
            // The state comes from a singleton rather than a view model so it
            // survives navigation, and the sheet only appears for a version the
            // reader has not already waved away.
            val updateState by updateNotifier.state.collectAsState()
            UpdateBottomSheet(
                state = updateState,
                onDismiss = updateNotifier::dismiss,
                onConfirm = { updateAvailableInfo(updateState)?.let(updateNotifier::downloadAndInstall) },
            )
        }
    }
}

@Composable
fun MainNavigationHost(
    navController: NavHostController,
    onAnimeSelect: (Anime) -> Unit,
    // Injected by MainScreen (default: Hilt). Present as a parameter so the
    // start destination composes in non-Hilt test hosts; every other route
    // resolves its own ViewModel on first navigation.
    homeViewModel: HomeViewModel = hiltViewModel(),
    onOpenListing: (sourceId: String, listing: git.shin.komorei.model.Listing) -> Unit = { _, _ -> },
    onOpenSearch: (sourceId: String) -> Unit = {},
    onOpenSourceSearch: (sourceId: String, query: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onAnimeClick = onAnimeSelect,
                viewModel = homeViewModel,
                onOpenListing = onOpenListing,
                onOpenSearch = onOpenSearch,
                onOpenNotifications = { navController.navigate(Screen.Notifications.route) },
            )
        }
        composable(Screen.Search.route) {
            SearchDiscoveryScreen(
                onAnimeClick = onAnimeSelect,
                onOpenSourceSearch = onOpenSourceSearch,
            )
        }
        composable(Screen.Sources.route) {
            SourcesScreen(
                onOpenRepos = { navController.navigate(Screen.SourceRepos.route) },
                onOpenSource = { sourceId ->
                    navController.navigate(Screen.SourceHome.createRoute(sourceId))
                },
            )
        }
        composable(Screen.SourceRepos.route) {
            SourceReposScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.SourceHome.route,
            arguments =
                listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                ),
        ) { entry ->
            SourceHomeScreen(
                sourceId = entry.arguments?.getString("sourceId").orEmpty(),
                onAnimeClick = onAnimeSelect,
                onOpenListing = onOpenListing,
                onOpenSettings = { sourceId ->
                    navController.navigate(Screen.SourceSettings.createRoute(sourceId))
                },
                onOpenSearch = onOpenSearch,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Screen.SourceSearch.route,
            arguments =
                listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("query") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("filters") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
        ) { entry ->
            SourceSearchScreen(
                sourceId = entry.arguments?.getString("sourceId").orEmpty(),
                onAnimeClick = onAnimeSelect,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Screen.SourceSettings.route,
            arguments =
                listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                ),
        ) { entry ->
            SourceSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenBrowser = { url ->
                    val sourceId = entry.arguments?.getString("sourceId").orEmpty()
                    navController.navigate(Screen.SourceBrowser.createRoute(sourceId, url))
                },
            )
        }
        composable(
            route = Screen.SourceBrowser.route,
            arguments =
                listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType },
                ),
        ) { entry ->
            SourceBrowserScreen(
                initialUrl = entry.arguments?.getString("url").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(onAnimeClick = onAnimeSelect)
        }
        composable(Screen.Notifications.route) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onOpenSourceRepos = { navController.navigate(Screen.SourceRepos.route) },
                onOpenAdvanced = { navController.navigate(Screen.Advanced.route) },
                onOpenInsights = { navController.navigate(Screen.Insights.route) },
                onOpenAbout = { navController.navigate(Screen.About.route) },
                onOpenBackups = { navController.navigate(Screen.Backups.route) },
            )
        }
        composable(Screen.Backups.route) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Insights.route) {
            InsightsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Advanced.route) {
            AdvancedScreen(
                onBack = { navController.popBackStack() },
                onOpenLogs = { navController.navigate(Screen.Logs.route) },
            )
        }
        composable(Screen.Logs.route) {
            LogsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Screen.Listing.route,
            arguments =
                listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("listingArg") { type = NavType.StringType },
                ),
        ) {
            ListingScreen(
                onAnimeClick = onAnimeSelect,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
