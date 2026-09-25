package git.shin.komorei

import android.net.Uri
import git.shin.komorei.data.deeplink.ExternalDeepLinkParser
import git.shin.komorei.data.deeplink.ExternalDeepLinkRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExternalDeepLinkParserTest {
    @Test
    fun parsesKomoreiRepositoryLink() {
        val encoded = Uri.encode("https://repo.example/index.min.json")
        val parsed = ExternalDeepLinkParser.parse(
            Uri.parse("komorei://addSourceList?url=$encoded"),
        )

        assertEquals(
            ExternalDeepLinkRequest.AddRepository("https://repo.example/index.min.json"),
            parsed,
        )
    }

    @Test
    fun parsesKomoreiSourceInstallLink() {
        val encoded = Uri.encode("https://repo.example/sources/demo.krx")
        val parsed = ExternalDeepLinkParser.parse(
            Uri.parse("komorei://addSource?url=$encoded"),
        )

        assertEquals(
            ExternalDeepLinkRequest.InstallSource("https://repo.example/sources/demo.krx"),
            parsed,
        )
    }

    @Test
    fun leavesContentLinksForTheSourceResolver() {
        assertNull(
            ExternalDeepLinkParser.parse(
                Uri.parse("komorei://komorei.example/anime/frieren_journey"),
            ),
        )
    }

    @Test
    fun rejectsOtherOuterSchemes() {
        assertNull(
            ExternalDeepLinkParser.parse(
                Uri.parse("aidoku://addSourceList?url=https%3A%2F%2Frepo.example%2Findex.json"),
            ),
        )
        assertNull(ExternalDeepLinkParser.parse(Uri.parse("https://repo.example/source.krx")))
    }

    @Test
    fun rejectsMalformedRepositoryLink() {
        assertEquals(
            ExternalDeepLinkRequest.Invalid,
            ExternalDeepLinkParser.parse(Uri.parse("komorei://addSourceList?url=file:///tmp/repo")),
        )
        assertEquals(
            ExternalDeepLinkRequest.Invalid,
            ExternalDeepLinkParser.parse(Uri.parse("komorei://addSourceList")),
        )
    }
}
