package git.shin.komorei.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.PlayerSheetValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import git.shin.komorei.ui.navigation.Screen
import git.shin.komorei.ui.player.PlayerViewModel
import git.shin.komorei.ui.player.VideoPlayerSheet
import git.shin.komorei.ui.screens.home.HomeScreen
import git.shin.komorei.ui.screens.home.HomeViewModel
import git.shin.komorei.ui.screens.library.LibraryScreen
import git.shin.komorei.ui.screens.library.LibraryViewModel
import git.shin.komorei.ui.screens.search.SearchDiscoveryScreen
import git.shin.komorei.ui.screens.search.SearchViewModel
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.SurfaceDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val playbackState by playerViewModel.playbackState.collectAsState()
    val bookmarkedIds by playerViewModel.bookmarkedAnimeIds.collectAsState()

    val onAnimeSelect: (Anime) -> Unit = { anime ->
        playerViewModel.openAnime(anime)
        libraryViewModel.addToHistory(anime)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        val isWideScreen = maxWidth > 600.dp
        val isPlayerExpanded = playbackState.sheetValue == PlayerSheetValue.EXPANDED

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
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(Screen.Home.route) {
                            HomeScreen(
                                onAnimeClick = onAnimeSelect,
                                viewModel = homeViewModel
                            )
                        }
                        composable(Screen.Search.route) {
                            SearchDiscoveryScreen(
                                onAnimeClick = onAnimeSelect,
                                viewModel = searchViewModel
                            )
                        }
                        composable(Screen.Library.route) {
                            LibraryScreen(
                                onAnimeClick = onAnimeSelect,
                                viewModel = libraryViewModel
                            )
                        }
                    }

                    // Video Player Overlay Sheet
                    if (playbackState.sheetValue != PlayerSheetValue.HIDDEN) {
                        VideoPlayerSheet(
                            playbackState = playbackState,
                            isBookmarked = playbackState.currentAnime?.id in bookmarkedIds,
                            relatedAnimeList = playerViewModel.allAnimes,
                            onStateChange = { playerViewModel.setPlayerSheetValue(it) },
                            onPlayPauseToggle = { playerViewModel.togglePlayPause() },
                            onSeekTo = { playerViewModel.seekTo(it) },
                            onEpisodeSelected = { playerViewModel.selectEpisode(it) },
                            onToggleBookmark = {
                                playbackState.currentAnime?.id?.let { id ->
                                    playerViewModel.toggleBookmark(id)
                                    libraryViewModel.toggleBookmark(id)
                                }
                            },
                            onDismiss = { playerViewModel.dismissPlayer() },
                            onAnimeSelected = { onAnimeSelect(it) },
                            onNavigateToCategory = { filters ->
                                // navController.navigate(...)
                            },
                            bottomNavHeight = 0.dp
                        )
                    }
                }
            }
        } else {
            // PHONE PORTRAIT: Scaffold with YouTube-style bottom bar hide on full player expand
            Scaffold(
                bottomBar = {
                    AnimatedVisibility(
                        visible = !isPlayerExpanded,
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
                            .padding(bottom = actualBottomNavHeight + (if (isMiniPlayerShowing) 64.dp else 0.dp))
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Home.route,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(Screen.Home.route) {
                                HomeScreen(
                                    onAnimeClick = onAnimeSelect,
                                    viewModel = homeViewModel
                                )
                            }
                            composable(Screen.Search.route) {
                                SearchDiscoveryScreen(
                                    onAnimeClick = onAnimeSelect,
                                    viewModel = searchViewModel
                                )
                            }
                            composable(Screen.Library.route) {
                                LibraryScreen(
                                    onAnimeClick = onAnimeSelect,
                                    viewModel = libraryViewModel
                                )
                            }
                        }
                    }

                    // Floating YouTube Swipe-Down Minimize Player Sheet
                    if (playbackState.sheetValue != PlayerSheetValue.HIDDEN) {
                        VideoPlayerSheet(
                            playbackState = playbackState,
                            isBookmarked = playbackState.currentAnime?.id in bookmarkedIds,
                            relatedAnimeList = playerViewModel.allAnimes,
                            onStateChange = { playerViewModel.setPlayerSheetValue(it) },
                            onPlayPauseToggle = { playerViewModel.togglePlayPause() },
                            onSeekTo = { playerViewModel.seekTo(it) },
                            onEpisodeSelected = { playerViewModel.selectEpisode(it) },
                            onToggleBookmark = {
                                playbackState.currentAnime?.id?.let { id ->
                                    playerViewModel.toggleBookmark(id)
                                    libraryViewModel.toggleBookmark(id)
                                }
                            },
                            onDismiss = { playerViewModel.dismissPlayer() },
                            onAnimeSelected = { onAnimeSelect(it) },
                            onNavigateToCategory = { filters ->
                                // navController.navigate(...)
                            },
                            bottomNavHeight = if (isPlayerExpanded) 0.dp else actualBottomNavHeight
                        )
                    }
                }
            }
        }
    }
}
