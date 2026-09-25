package git.shin.komorei

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.NotificationStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The notification inbox is in-memory (fake), so this locks the read/clear
 * semantics the Thông báo tab relies on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationStoreTest {
    private fun newStore(): NotificationStore = NotificationStore(ApplicationProvider.getApplicationContext<Context>()).also { it.reseed() }

    @Test
    fun `seed has unread notifications`() {
        val store = newStore()
        assertTrue(store.notifications.value.isNotEmpty())
        assertTrue(store.unreadCount > 0)
    }

    @Test
    fun `markRead clears a single unread flag`() {
        val store = newStore()
        val target = store.notifications.value.first { !it.isRead }
        val unreadBefore = store.unreadCount

        store.markRead(target.id)

        assertTrue(
            store.notifications.value
                .first { it.id == target.id }
                .isRead,
        )
        assertEquals(unreadBefore - 1, store.unreadCount)
    }

    @Test
    fun `markAllRead leaves no unread notifications`() {
        val store = newStore()

        store.markAllRead()

        assertEquals(0, store.unreadCount)
        assertTrue(store.notifications.value.all { it.isRead })
    }

    @Test
    fun `clearAll empties the inbox`() {
        val store = newStore()

        store.clearAll()

        assertTrue(store.notifications.value.isEmpty())
    }
}
