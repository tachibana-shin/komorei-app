package git.shin.komorei.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Icon persistence in [KrxSourceRegistry]: the package `Payload/icon.png` is
 * cached under `filesDir/source-icons/<id>.png` at startup and fed into
 * `Source.icon`; re-installing the same source with different artwork must
 * OVERWRITE that cache (a stale first-extract must not mask a new icon).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KrxSourceRegistryIconTest {

    private lateinit var context: Context

    private val krxFileName = "vi.iconsrc.krx"
    private val iconPath: String get() = File(
        context.filesDir, "source-icons/vi.iconsrc.png",
    ).absolutePath

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun installedKrx(): File =
        File(File(context.filesDir, "sources").apply { mkdirs() }, krxFileName)

    private fun krxBytes(icon: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("source.json"))
            zip.write(
                """
                {"info":{"id":"vi.iconsrc","name":"Icon Test","version":1,
                 "url":"https://example.com","languages":["vi"],"contentRating":0}}
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Payload/icon.png"))
            zip.write(icon)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Payload/main.wasm"))
            zip.write(byteArrayOf(0, 97, 115, 109))
            zip.closeEntry()
        }
    }.toByteArray()

    private fun newRegistry(): KrxSourceRegistry =
        KrxSourceRegistry(context, KrxHostImpl(context))

    @Test
    fun `first scan persists the package icon and wires it into Source icon`() {
        installedKrx().writeBytes(krxBytes(byteArrayOf(1, 2, 3, 4)))

        val source = newRegistry().sourceAppList().first { it.id == "vi.iconsrc" }

        assertEquals(iconPath, source.icon)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), java.io.File(iconPath).readBytes())
    }

    @Test
    fun `a re-installed krx with new artwork overwrites the cached icon`() {
        val first = krxBytes(byteArrayOf(1, 2, 3, 4))
        installedKrx().writeBytes(first)
        newRegistry().sourceAppList() // first scan caches the icon [1,2,3,4]

        // Re-install the same source with a different package icon, then scan again
        // through a fresh registry (a fresh startup scan reads the new file).
        val second = krxBytes(byteArrayOf(9, 9, 9, 9))
        installedKrx().writeBytes(second)
        val source = newRegistry().sourceAppList().first { it.id == "vi.iconsrc" }

        assertEquals(iconPath, source.icon)
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), java.io.File(iconPath).readBytes())
    }

    @Test
    fun `package without an icon leaves Source icon blank`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("source.json"))
                zip.write(
                    """
                    {"info":{"id":"vi.iconsrc","name":"Icon Test","version":1,
                     "url":"https://example.com","languages":["vi"],"contentRating":0}}
                    """.trimIndent().toByteArray()
                )
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("Payload/main.wasm"))
                zip.write(byteArrayOf(0, 97, 115, 109))
                zip.closeEntry()
            }
        }.toByteArray()
        installedKrx().writeBytes(bytes)

        val source = newRegistry().sourceAppList().first { it.id == "vi.iconsrc" }

        assertEquals("", source.icon)
    }
}