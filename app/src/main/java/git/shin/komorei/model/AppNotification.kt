package git.shin.komorei.model

/** Category of an in-app notification, used to pick an icon/colour. */
enum class NotificationType {
    NEW_EPISODE,
    SOURCE_UPDATE,
    SYSTEM,
}

/**
 * One entry in the notification tab.
 *
 * [title]/[body] are already-localized strings (built from resources by the
 * store) because they can carry dynamic data and are persisted as-is.
 */
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    /** Age of the notification, in minutes. Drives the relative time label. */
    val minutesAgo: Int,
    val isRead: Boolean,
)
