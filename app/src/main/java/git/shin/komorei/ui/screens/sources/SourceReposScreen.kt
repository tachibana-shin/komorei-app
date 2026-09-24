package git.shin.komorei.ui.screens.sources

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus

/**
 * Full-screen repo manager ("Quản lý kho nguồn"): add / remove repo URLs,
 * with per-repo fetch status (name when loaded, retry when unavailable).
 * Shares the same [SourceStateStore]/[SourceReposRepository] as the Sources
 * tab, so repo edits made here show up in the Add-source sheet immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceReposScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val repos by viewModel.repos.collectAsState()
    val repoStates by viewModel.repoStates.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    var showAddRepoDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.refreshAllRepos()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .testTag("source_repos_screen"),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    // TV focus highlight (no-op on phones).
                    .tvFocus(shape = CircleShape, scale = 1.15f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextPrimary,
                )
            }
            Text(
                text = stringResource(R.string.source_repos_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { showAddRepoDialog = true },
                modifier = Modifier
                    // TV focus highlight (no-op on phones).
                    .tvFocus(shape = CircleShape, scale = 1.15f)
                    .testTag("add_repo_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.sources_repos_add),
                    tint = AnimeRed,
                )
            }
        }

        if (repos.isEmpty()) {
            EmptyRepos(onAdd = { showAddRepoDialog = true })
        } else {
            // Pull-to-refresh re-fetches every repo in place.
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = viewModel::refreshRepos,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    items(repos, key = { it }) { url ->
                        RepoManageRow(
                            url = url,
                            state = repoStates[url],
                            onRemove = { viewModel.removeRepo(url) },
                            onRetry = { viewModel.loadRepo(url) },
                        )
                    }
                }
            }
        }
    }

    if (showAddRepoDialog) {
        AddRepoDialog(
            onDismiss = { showAddRepoDialog = false },
            onConfirm = { url ->
                if (viewModel.addRepo(url)) {
                    showAddRepoDialog = false
                }
            },
        )
    }
}

@Composable
private fun RepoManageRow(
    url: String,
    state: RepoSectionState?,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("repo_manage_row_$url"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (state is RepoSectionState.Unavailable) TextMuted.copy(alpha = 0.15f) else AnimeRed.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = if (state is RepoSectionState.Unavailable) TextMuted else AnimeRed,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = (state as? RepoSectionState.Loaded)?.name ?: url.removePrefix("https://").removePrefix("http://"),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = url,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        when (state) {
            is RepoSectionState.Loaded -> {
                Text(
                    text = stringResource(R.string.sources_repos_fetch_sources),
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            is RepoSectionState.Unavailable -> {
                Text(
                    text = stringResource(R.string.sources_repos_unavailable),
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sources_repos_retry),
                    color = AnimeRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        // TV focus highlight (no-op on phones).
                        .tvFocus(shape = RoundedCornerShape(100), scale = 1.06f)
                        .clip(RoundedCornerShape(100))
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            null, is RepoSectionState.Loading -> {
                Text(
                    text = stringResource(R.string.sources_repos_loading),
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                // TV focus highlight (no-op on phones).
                .tvFocus(shape = CircleShape, scale = 1.15f)
                .testTag("remove_repo_$url"),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.sources_repos_remove_cd),
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyRepos(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.sources_repos_empty),
            color = TextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                // TV focus highlight (no-op on phones).
                .tvFocus(shape = RoundedCornerShape(100), scale = 1.06f)
                .clip(RoundedCornerShape(100))
                .background(AnimeRed)
                .clickable(onClick = onAdd)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.sources_repos_add),
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
            )
        }
    }
}