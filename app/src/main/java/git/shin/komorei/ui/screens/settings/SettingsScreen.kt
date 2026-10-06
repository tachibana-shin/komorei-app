package git.shin.komorei.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import git.shin.komorei.R
import git.shin.komorei.data.update.UpdateUiState
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.theme.TextSecondary
import git.shin.komorei.ui.tv.tvFocus

@Composable
fun SettingsScreen(
    onOpenSourceRepos: () -> Unit,
    onOpenAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenInsights: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onOpenBackups: () -> Unit = {},
) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val updateSubtitle =
        when (val state = updateState) {
            UpdateUiState.Idle -> stringResource(R.string.settings_update_check)
            UpdateUiState.Checking -> stringResource(R.string.settings_update_checking)
            is UpdateUiState.Available ->
                stringResource(
                    R.string.settings_update_available,
                    state.info.version,
                )
            is UpdateUiState.Downloading ->
                stringResource(
                    R.string.settings_update_downloading_progress,
                    state.progress,
                )
        }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { messageRes ->
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .statusBarsPadding()
                .testTag("settings_screen"),
    ) {
        // Fixed header — stays put while the settings list below scrolls.
        Text(
            text = stringResource(R.string.settings_title),
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.settings_subtitle),
            color = TextMuted,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            SettingsSection(title = stringResource(R.string.settings_section_sources)) {
                SettingsRow(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.settings_manage_repos),
                    subtitle = stringResource(R.string.settings_manage_repos_subtitle),
                    onClick = onOpenSourceRepos,
                    testTag = "settings_manage_repos",
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_data)) {
                SettingsRow(
                    icon = Icons.Default.CleaningServices,
                    title = stringResource(R.string.settings_clear_image_cache),
                    subtitle = stringResource(R.string.settings_clear_image_cache_subtitle),
                    onClick = viewModel::clearImageCache,
                    testTag = "settings_clear_image_cache",
                )
                SettingsRow(
                    icon = Icons.Default.Cookie,
                    title = stringResource(R.string.settings_clear_cookies),
                    subtitle = stringResource(R.string.settings_clear_cookies_subtitle),
                    onClick = viewModel::clearCookies,
                    testTag = "settings_clear_cookies",
                )
                SettingsRow(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.settings_clear_search_history),
                    subtitle = stringResource(R.string.settings_clear_search_history_subtitle),
                    onClick = viewModel::clearSearchHistory,
                    testTag = "settings_clear_search_history",
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_backup)) {
                SettingsRow(
                    icon = Icons.Default.CloudSync,
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    onClick = onOpenBackups,
                    testTag = "settings_backup",
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_advanced)) {
                SettingsRow(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.settings_advanced),
                    subtitle = stringResource(R.string.settings_advanced_subtitle),
                    onClick = onOpenAdvanced,
                    testTag = "settings_advanced",
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    icon = Icons.Default.SystemUpdateAlt,
                    title = stringResource(R.string.settings_update),
                    subtitle = updateSubtitle,
                    onClick =
                        if (updateState is UpdateUiState.Checking || updateState is UpdateUiState.Downloading) {
                            null
                        } else {
                            viewModel::checkForUpdate
                        },
                    testTag = "settings_update",
                )
                SettingsRow(
                    icon = Icons.Default.BarChart,
                    title = stringResource(R.string.settings_insights),
                    subtitle = stringResource(R.string.settings_insights_subtitle),
                    onClick = onOpenInsights,
                    testTag = "settings_insights",
                )
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_about_subtitle),
                    onClick = onOpenAbout,
                    testTag = "settings_about",
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = title.uppercase(),
            color = AnimeRed,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    testTag: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardDark)
                // TV focus highlight (no-op on phones); disabled rows (onClick == null)
                // simply never get focus because there is no clickable/focusable node.
                .then(if (onClick != null) Modifier.tvFocus(shape = RoundedCornerShape(12.dp), scale = 1.02f) else Modifier)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AnimeRed,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
