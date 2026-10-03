package git.shin.komorei.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.Anime
import git.shin.komorei.ui.components.AnimeSection
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary

@Composable
fun LibraryScreen(
    onAnimeClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val followingTitle = stringResource(R.string.library_tab_following)
    val historyTitle = stringResource(R.string.library_tab_history)
    val tabTitles =
        remember(followingTitle, historyTitle) {
            listOf(followingTitle, historyTitle)
        }

    val bookmarkedAnimes by viewModel.bookmarkedAnimes.collectAsState()
    val historyAnimes by viewModel.historyAnimes.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .statusBarsPadding()
                .testTag("library_screen"),
    ) {
        // Title Bar
        Text(
            text = stringResource(R.string.library_title),
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.library_tab_following) + " & " + stringResource(R.string.library_tab_history),
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Segmented Sub-Tab (Following vs History)
        SecondaryTabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = BackgroundDark,
            contentColor = TextPrimary,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedTabIndex = selectedSubTab, matchContentSize = true),
                    color = AnimeRed,
                    height = 3.dp,
                )
            },
            divider = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
        ) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = selectedSubTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedSubTab = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (index == 0) Icons.Default.Bookmark else Icons.Default.History,
                                contentDescription = title,
                                tint = if (isSelected) AnimeRed else TextMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = title,
                                color = if (isSelected) AnimeRed else TextMuted,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    },
                    modifier = Modifier.testTag("library_subtab_$index"),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val displayList = if (selectedSubTab == 0) bookmarkedAnimes else historyAnimes

        if (displayList.isEmpty()) {
            // Empty State
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (selectedSubTab == 0) Icons.Default.Bookmark else Icons.Default.History,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text =
                            if (selectedSubTab == 0) {
                                stringResource(R.string.empty_following_title)
                            } else {
                                stringResource(R.string.empty_history_title)
                            },
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text =
                            if (selectedSubTab == 0) {
                                stringResource(R.string.empty_following_subtitle)
                            } else {
                                stringResource(R.string.empty_history_subtitle)
                            },
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            AnimeSection(
                title = tabTitles[selectedSubTab],
                animeList = displayList,
                onAnimeClick = onAnimeClick,
                isGrid = true,
                icon = if (selectedSubTab == 0) Icons.Default.Bookmark else Icons.Default.History,
                getSourceName = { viewModel.getSourceName(it) },
                rightContent = {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier =
                            Modifier
                                .background(
                                    color = TextMuted.copy(alpha = 0.15f),
                                    shape =
                                        androidx.compose.foundation.shape
                                            .RoundedCornerShape(4.dp),
                                ).padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "${displayList.size}",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                modifier = Modifier.weight(1f).padding(bottom = 114.dp),
            )
        }
    }
}
