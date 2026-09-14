package git.shin.komorei.ui.screens.source

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.model.Listing
import git.shin.komorei.ui.components.BannerCarouselSkeleton
import git.shin.komorei.ui.components.SectionSkeleton
import git.shin.komorei.ui.screens.home.HomeViewModel
import git.shin.komorei.ui.screens.home.SourceHomeContent
import git.shin.komorei.ui.screens.home.SourceHomeData
import git.shin.komorei.ui.screens.home.SourceListingState
import git.shin.komorei.ui.screens.sources.sourceVersionSubtitle
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

/**
 * A source's "home screen" — Aidoku's NewSourceViewController: the same
 * browse content the Home tab shows for that source (listings chips + full
 * home layout), in its own full-screen page. The top bar hosts the source's
 * ⋮ context menu: **Cài đặt** (opens [SourceSettingsScreen]) and **Mở trang
 * web** (opens the source's homepage URL in a browser).
 *
 * The screen is backed by a fresh, route-scoped [HomeViewModel] (the `sourceId`
 * nav arg scopes it to this one source), so it reuses the exact Home-tab state
 * logic through the shared [SourceHomeContent] renderer without touching the
 * Home tab's own instance.
 */
@Composable
fun SourceHomeScreen(
    sourceId: String,
    onAnimeClick: (Anime) -> Unit,
    onOpenListing: (sourceId: String, listing: Listing) -> Unit,
    onOpenSettings: (sourceId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val source by viewModel.source.collectAsState()
    val sourceDataMap by viewModel.sourceDataMap.collectAsState()
    val listingStateMap by viewModel.listingStateMap.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }

    // Load this source's dynamic listings (Aidoku chip row) — the home layout
    // itself is loaded by the VM's scoped init.
    LaunchedEffect(sourceId) {
        viewModel.loadListings(sourceId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .testTag("source_home_screen"),
    ) {
        // Top bar: back + source name + ⋮ menu (Cài đặt / Mở trang web).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source?.name ?: sourceId,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                source?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sourceVersionSubtitle(it.version, it.languages),
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("source_home_menu"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.source_home_menu_cd),
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (!source?.baseUrl.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.source_home_open_website)) },
                            onClick = {
                                menuExpanded = false
                                openSourceWebsite(context, source!!.baseUrl)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.source_home_settings)) },
                        onClick = {
                            menuExpanded = false
                            onOpenSettings(sourceId)
                        },
                    )
                }
            }
        }

        val activeSource = source
        if (activeSource == null) {
            // The scoped Source is still resolving (or the source vanished) —
            // show the same skeleton the Home tab uses while home data loads.
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item { BannerCarouselSkeleton() }
                items(3) { SectionSkeleton() }
            }
        } else {
            SourceHomeContent(
                source = activeSource,
                sourceData = sourceDataMap[sourceId] ?: SourceHomeData(isLoading = true),
                listingState = listingStateMap[sourceId] ?: SourceListingState(),
                onSelectListing = { viewModel.selectListing(sourceId, it) },
                onRetryHome = { viewModel.loadSourceData(sourceId) },
                onLoadListingReset = { viewModel.loadListingPage(sourceId, reset = true) },
                onLoadListingMore = { viewModel.loadListingMore(sourceId) },
                onOpenListing = { listing -> onOpenListing(sourceId, listing) },
                onAnimeClick = onAnimeClick,
                getSourceName = { viewModel.getSourceName(it) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}