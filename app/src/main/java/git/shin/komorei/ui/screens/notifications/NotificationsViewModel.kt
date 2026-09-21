package git.shin.komorei.ui.screens.notifications

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import git.shin.komorei.data.NotificationStore
import git.shin.komorei.model.AppNotification
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin wrapper over [NotificationStore]; the store owns the (fake) inbox so
 * unread state is shared app-wide and survives this ViewModel being recreated.
 */
@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val store: NotificationStore,
) : ViewModel() {

    val notifications: StateFlow<List<AppNotification>> = store.notifications

    val unreadCount: Int get() = store.unreadCount

    fun markRead(id: String) = store.markRead(id)

    fun markAllRead() = store.markAllRead()

    fun clearAll() = store.clearAll()
}
