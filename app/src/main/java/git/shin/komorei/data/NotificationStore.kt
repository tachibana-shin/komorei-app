package git.shin.komorei.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import git.shin.komorei.R
import git.shin.komorei.model.AppNotification
import git.shin.komorei.model.NotificationType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory (fake) notification inbox.
 *
 * A singleton so unread state survives tab switches and process-wide recreation
 * of the ViewModel — later this can be backed by Room or a real push endpoint
 * without changing the screen. Content mirrors what a real source would emit
 * through the Aidoku `NotificationHandler` hook
 * ([AnimeRepository.handleNotification]).
 */
@Singleton
class NotificationStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _notifications = MutableStateFlow(seed())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    val unreadCount: Int
        get() = _notifications.value.count { !it.isRead }

    fun markRead(id: String) {
        _notifications.value =
            _notifications.value.map { notification ->
                if (notification.id == id) notification.copy(isRead = true) else notification
            }
    }

    fun markAllRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
    }

    fun clearAll() {
        _notifications.value = emptyList()
    }

    /** Re-seeds the fake inbox (tests). */
    internal fun reseed() {
        _notifications.value = seed()
    }

    private fun seed(): List<AppNotification> =
        listOf(
            AppNotification(
                id = "n1",
                type = NotificationType.NEW_EPISODE,
                title = context.getString(R.string.notification_new_episode_title),
                body = context.getString(R.string.notification_new_episode_body),
                minutesAgo = 8,
                isRead = false,
            ),
            AppNotification(
                id = "n2",
                type = NotificationType.NEW_EPISODE,
                title = context.getString(R.string.notification_new_episode_title),
                body = context.getString(R.string.notification_new_episode_body_2),
                minutesAgo = 95,
                isRead = false,
            ),
            AppNotification(
                id = "n3",
                type = NotificationType.SOURCE_UPDATE,
                title = context.getString(R.string.notification_source_update_title),
                body = context.getString(R.string.notification_source_update_body),
                minutesAgo = 480,
                isRead = false,
            ),
            AppNotification(
                id = "n4",
                type = NotificationType.SYSTEM,
                title = context.getString(R.string.notification_system_title),
                body = context.getString(R.string.notification_system_body),
                minutesAgo = 1_500,
                isRead = true,
            ),
            AppNotification(
                id = "n5",
                type = NotificationType.NEW_EPISODE,
                title = context.getString(R.string.notification_new_episode_title),
                body = context.getString(R.string.notification_new_episode_body_3),
                minutesAgo = 4_320,
                isRead = true,
            ),
        )
}
