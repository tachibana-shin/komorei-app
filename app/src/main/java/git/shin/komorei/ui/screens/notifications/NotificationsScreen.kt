package git.shin.komorei.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import git.shin.komorei.R
import git.shin.komorei.model.AppNotification
import git.shin.komorei.model.NotificationType
import git.shin.komorei.ui.theme.AnimeBlue
import git.shin.komorei.ui.theme.AnimeRed
import git.shin.komorei.ui.theme.BackgroundDark
import git.shin.komorei.ui.theme.CardDark
import git.shin.komorei.ui.theme.TextMuted
import git.shin.komorei.ui.theme.TextPrimary
import git.shin.komorei.ui.tv.tvFocus

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(BackgroundDark)
                .statusBarsPadding()
                .testTag("notifications_screen"),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier
                        // TV focus highlight (no-op on phones).
                        .tvFocus(shape = CircleShape, scale = 1.15f)
                        .testTag("notifications_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = TextPrimary,
                )
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = 16.dp),
            ) {
                Text(
                    text = stringResource(R.string.notifications_title),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text =
                        if (unreadCount > 0) {
                            stringResource(R.string.notifications_unread_count, unreadCount)
                        } else {
                            stringResource(R.string.notifications_subtitle)
                        },
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        if (notifications.isNotEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { viewModel.markAllRead() },
                    modifier =
                        Modifier
                            // TV focus highlight (no-op on phones).
                            .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)
                            .testTag("notifications_mark_all_read"),
                ) {
                    Text(
                        text = stringResource(R.string.notifications_mark_all_read),
                        color = if (unreadCount > 0) AnimeRed else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(
                    onClick = { viewModel.clearAll() },
                    modifier =
                        Modifier
                            .tvFocus(shape = RoundedCornerShape(8.dp), scale = 1.05f)
                            .testTag("notifications_clear_all"),
                ) {
                    Text(
                        text = stringResource(R.string.notifications_clear_all),
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (notifications.isEmpty()) {
            NotificationsEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = notifications,
                    key = { it.id },
                ) { notification ->
                    NotificationRow(
                        notification = notification,
                        onClick = { viewModel.markRead(notification.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    onClick: () -> Unit,
) {
    val accent = notification.type.accentColor()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                // TV focus highlight (no-op on phones) — full-width row, ring only.
                .tvFocus(shape = RoundedCornerShape(12.dp), scale = 1.0f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (notification.isRead) CardDark else accent.copy(alpha = 0.14f))
                .clickable(onClick = onClick)
                .padding(12.dp)
                .testTag("notification_${notification.id}"),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = notification.type.icon(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!notification.isRead) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(AnimeRed),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notification.body,
                color = TextMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = timeAgoLabel(notification.minutesAgo),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NotificationsEmptyState() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.notifications_empty_title),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.notifications_empty_subtitle),
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun timeAgoLabel(minutesAgo: Int): String =
    when {
        minutesAgo < 1 -> stringResource(R.string.time_just_now)
        minutesAgo < 60 -> stringResource(R.string.time_minutes_ago, minutesAgo)
        minutesAgo < 1_440 -> stringResource(R.string.time_hours_ago, minutesAgo / 60)
        else -> stringResource(R.string.time_days_ago, minutesAgo / 1_440)
    }

private fun NotificationType.icon(): ImageVector =
    when (this) {
        NotificationType.NEW_EPISODE -> Icons.Default.PlayCircleOutline
        NotificationType.SOURCE_UPDATE -> Icons.Default.SystemUpdateAlt
        NotificationType.SYSTEM -> Icons.Default.Info
    }

private fun NotificationType.accentColor(): Color =
    when (this) {
        NotificationType.NEW_EPISODE -> AnimeRed
        NotificationType.SOURCE_UPDATE -> AnimeBlue
        NotificationType.SYSTEM -> Color(0xFF8B85FF)
    }
