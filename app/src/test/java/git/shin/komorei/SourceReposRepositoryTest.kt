package git.shin.komorei

import git.shin.komorei.data.absolutizeUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests for comic repo manifest URL resolution. */
class SourceReposRepositoryTest {

    @Test
    fun `absolute urls pass through untouched`() {
        assertEquals(
            "https://cdn.example.com/pkg/krx.krx",
            absolutizeUrl("https://host/repo/index.min.json", "https://cdn.example.com/pkg/krx.krx"),
        )
    }

    @Test
    fun `relative urls resolve against the manifest directory`() {
        assertEquals(
            "https://host/repo/sources/vi.ophim-v1.krx",
            absolutizeUrl("https://host/repo/index.min.json", "sources/vi.ophim-v1.krx"),
        )
        assertEquals(
            "https://host/repo/icons/vi.ophim-v1.png",
            absolutizeUrl("https://host/repo/index.min.json", "icons/vi.ophim-v1.png"),
        )
    }

    @Test
    fun `root-relative urls resolve against the host root`() {
        assertEquals(
            "https://host/sources/vi.ophim-v1.krx",
            absolutizeUrl("https://host/repo/index.min.json", "/sources/vi.ophim-v1.krx"),
        )
    }

    @Test
    fun `manifest without filename resolves the same way`() {
        assertEquals(
            "https://host/repo/sources/vi.ophim-v1.krx",
            absolutizeUrl("https://host/repo", "sources/vi.ophim-v1.krx"),
        )
    }

    @Test
    fun `invalid base url leaves the value unchanged`() {
        assertEquals("sources/vi.ophim-v1.krx", absolutizeUrl("not a url", "sources/vi.ophim-v1.krx"))
    }
}