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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.navigation.Screen
import git.shin.komorei.ui.player.PlayerViewModel
import git.shin.komorei.ui.player.PlayerSheetValue
import git.shin.komorei.ui.player.VideoPlayerSheet
import git.shin.komorei.ui.screens.home.HomeScreen
import git.shin.komorei.ui.screens.library.LibraryScreen
import git.shin.komorei.ui.screens.search.SearchDiscoveryScreen
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val playbackState by playerViewModel.playbackState.collectAsState()

    val onAnimeSelected: (Anime) -> Unit = { anime ->
        playerViewModel.openAnime(anime)
    }

    BoxWithConstraints(modifier = Modifier
        .fillMaxSize()
        .background(BackgroundDark)) {
        val isWideScreen = maxWidth > 600.dp
        val isPlayerExpanded = playbackState.sheetValue == PlayerSheetValue.EXPANDED
        val isFullscreen = playbackState.isFullscreen

        Box(modifier = Modifier.fillMaxSize()) {
            if (isWideScreen) {
                // TABLET / LANDSCAPE: Navigation Rail on the left
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = SurfaceDark,
                        contentColor = TextPrimary,
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("main_navigation_rail")
                    ) {
                        NavigationRailItem(
                            selected = currentRoute == Screen.Home.route,
                            onClick = {
                                if (currentRoute != Screen.Home.route) {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Screen.Home.route) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = stringResource(R.string.tab_home)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_home), fontSize = 11.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = AnimeRed,
                                indicatorColor = AnimeRed,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag("rail_tab_home")
                        )

                        NavigationRailItem(
                            selected = currentRoute == Screen.Search.route,
                            onClick = {
                                if (currentRoute != Screen.Search.route) {
                                    navController.navigate(Screen.Search.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Screen.Search.route) Icons.Filled.Explore else Icons.Outlined.Explore,
                                    contentDescription = stringResource(R.string.tab_search)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_search), fontSize = 11.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = AnimeRed,
                                indicatorColor = AnimeRed,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag("rail_tab_search")
                        )

                        NavigationRailItem(
                            selected = currentRoute == Screen.Library.route,
                            onClick = {
                                if (currentRoute != Screen.Library.route) {
                                    navController.navigate(Screen.Library.route) {
                                        popUpTo(navController.graph.startDestinationId)
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == Screen.Library.route) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = stringResource(R.string.tab_library)
                                )
                            },
                            label = { Text(stringResource(R.string.tab_library), fontSize = 11.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = AnimeRed,
                                indicatorColor = AnimeRed,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            modifier = Modifier.testTag("rail_tab_library")
                        )
                    }

                    // Main Content
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        MainNavigationHost(navController = navController, onAnimeSelect = onAnimeSelected)
                    }
                }
            } else {
                // PHONE PORTRAIT: Scaffold with YouTube-style bottom bar hide on full player expand
                Scaffold(
                    bottomBar = {
                        AnimatedVisibility(
                            visible = !isPlayerExpanded && !isFullscreen,
                            enter = slideInVertically(initialOffsetY = { it }),
                            exit = slideOutVertically(targetOffsetY = { it })
                        ) {
                            NavigationBar(
                                containerColor = SurfaceDark,
                                contentColor = TextPrimary,
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("main_bottom_navigation")
                            ) {
                                NavigationBarItem(
                                    selected = currentRoute == Screen.Home.route,
                                    onClick = {
                                        if (currentRoute != Screen.Home.route) {
                                            navController.navigate(Screen.Home.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == Screen.Home.route) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = stringResource(R.string.tab_home)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.tab_home), fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = AnimeRed,
                                        indicatorColor = AnimeRed,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("tab_home")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.Search.route,
                                    onClick = {
                                        if (currentRoute != Screen.Search.route) {
                                            navController.navigate(Screen.Search.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == Screen.Search.route) Icons.Filled.Explore else Icons.Outlined.Explore,
                                            contentDescription = stringResource(R.string.tab_search)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.tab_search), fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = AnimeRed,
                                        indicatorColor = AnimeRed,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("tab_search")
                                )

                                NavigationBarItem(
                                    selected = currentRoute == Screen.Library.route,
                                    onClick = {
                                        if (currentRoute != Screen.Library.route) {
                                            navController.navigate(Screen.Library.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (currentRoute == Screen.Library.route) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = stringResource(R.string.tab_library)
                                        )
                                    },
                                    label = { Text(stringResource(R.string.tab_library), fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color.White,
                                        selectedTextColor = AnimeRed,
                                        indicatorColor = AnimeRed,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier.testTag("tab_library")
                                )
                            }
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val actualBottomNavHeight = innerPadding.calculateBottomPadding()
                    val isMiniPlayerShowing = playbackState.sheetValue == PlayerSheetValue.COLLAPSED

                    Box(modifier = Modifier.fillMaxSize()) {
                        // Screen contents padded so last list items are never cut off behind bottom bar & mini player
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = if (isFullscreen) 0.dp else (actualBottomNavHeight + (if (isMiniPlayerShowing) 64.dp else 0.dp)))
                        ) {
                            MainNavigationHost(
                                navController = navController,
                                onAnimeSelect = onAnimeSelected
                            )
                        }
                    }
                }
            }

            // UNIFIED Video Player Overlay Sheet: Outside the adaptive branches to preserve state!
            if (playbackState.sheetValue != PlayerSheetValue.HIDDEN) {
                val actualBottomNavHeight = if (isWideScreen) 0.dp else 80.dp // Approximate fallback
                VideoPlayerSheet(
                    playbackState = playbackState,
                    player = playerViewModel.player,
                    relatedAnimeList = playerViewModel.allAnimes,
                    onStateChange = { playerViewModel.setPlayerSheetValue(it) },
                    onPlayPauseToggle = { playerViewModel.togglePlayPause() },
                        onToggleFullscreen = { playerViewModel.toggleFullscreen() },
                    onSpeedChange = { playerViewModel.setPlaybackSpeed(it) },
                    onStartFastForward = { playerViewModel.startFastForward() },
                    onStopFastForward = { playerViewModel.stopFastForward() },
                    onEpisodeSelected = { playerViewModel.selectEpisode(it) },
                    onStreamSelected = { playerViewModel.selectStream(it) },
                    onRetryStreams = { playerViewModel.retryStreams() },
                    onToggleLock = { playerViewModel.toggleLock() },
                    onToggleSubtitles = { playerViewModel.toggleSubtitles() },
                    onTrackSelected = { group, index -> playerViewModel.selectTrack(group, index) },
                    onClearTrackType = { playerViewModel.clearTrackType(it) },
                    onDismiss = { playerViewModel.dismissPlayer() },
                    onAnimeSelected = onAnimeSelected,
                    onNavigateToCategory = { filters ->
                        // navController.navigate(...)
                    },
                    bottomNavHeight = if (isPlayerExpanded || isFullscreen) 0.dp else actualBottomNavHeight
                )
            }
        }
    }
}

@Composable
fun MainNavigationHost(
    navController: NavHostController,
    onAnimeSelect: (Anime) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier.fillMaxSize()
    ) {
        composable(Screen.Home.route) {
            HomeScreen(onAnimeClick = onAnimeSelect)
        }
        composable(Screen.Search.route) {
            SearchDiscoveryScreen(onAnimeClick = onAnimeSelect)
        }
        composable(Screen.Library.route) {
            LibraryScreen(onAnimeClick = onAnimeSelect)
        }
    }
}
