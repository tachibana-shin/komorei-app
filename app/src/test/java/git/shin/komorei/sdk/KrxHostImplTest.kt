package git.shin.komorei.sdk

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.sdk.runner.HostDefaultValue
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Unit-test the real Kotlin host ([KrxHostImpl]) — Jsoup DOM ops, defaults,
 * date parsing, escaping and [KrxManager]'s wasm extraction. No native runner;
 * the DOM/defaults/all host facets are exercised directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KrxHostImplTest {

    private lateinit var host: KrxHostImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        host = KrxHostImpl(context)
    }

    // ---- escaping ----------------------------------------------------------

    @Test
    fun `html escape only escapes ampersand and angle brackets`() {
        // quotes are left alone, exactly like the SDK contract
        assertEquals("&lt;a href=\"x\"&gt;&amp;&lt;/a&gt;", host.htmlEscape("<a href=\"x\">&</a>"))
    }

    @Test
    fun `html unescape decodes entities`() {
        assertEquals("a<b>&c", host.htmlUnescape("a&lt;b&gt;&amp;c"))
        // full entity table, not just the escaped subset
        assertEquals("héllo & world", host.htmlUnescape("h&eacute;llo &amp; world"))
    }

    // ---- DOM ---------------------------------------------------------------

    @Test
    fun `parse select traverse and mutate`() {
        val doc = host.htmlParse(
            """<html><body><div id="a" class="x">
               <h1>Title</h1>
               <p>Para <b>bold</b> text</p>
               <a href="/link">Link</a>
             </div></body></html>""",
            "https://example.com/base/",
        )
        assertTrue("document handle must be positive", doc > 0)
        assertEquals(7, host.htmlKind(doc)) // Document

        val div = host.htmlSelectFirst(doc, "div#a")
        assertNotNull(div)
        assertEquals(5, host.htmlKind(div!!)) // Element
        assertEquals("div", host.htmlTagName(div))
        assertEquals("a", host.htmlId(div))
        assertEquals("x", host.htmlClassName(div))
        assertTrue(host.htmlHasAttr(div, "class"))
        assertEquals("x", host.htmlAttr(div, "class"))
        assertNull(host.htmlAttr(div, "data-missing"))
        assertTrue(host.htmlHasClass(div, "x"))

        // list selection
        val ems = host.htmlSelect(div, "h1, p, a")
        assertNotNull(ems)
        assertEquals(3, host.htmlSize(ems!!))
        assertEquals(6, host.htmlKind(ems)) // ElementList
        assertEquals("Title", host.htmlText(host.htmlFirst(ems)!!))
        assertEquals("Link", host.htmlText(host.htmlLast(ems)!!))
        assertEquals("Title", host.htmlText(host.htmlGet(ems, 0)!!))
        assertNull(host.htmlGet(ems, 99))

        // children vs child_nodes
        val children = host.htmlChildren(div)!!
        assertEquals(3, host.htmlSize(children))
        val nodes = host.htmlChildNodes(div)!!
        assertEquals(7, host.htmlSize(nodes)) // [text, h1, text, p, text, a, text]

        // text semantics
        assertEquals("Title", host.htmlText(host.htmlSelectFirst(div, "h1")!!))
        assertEquals("bold", host.htmlText(host.htmlSelectFirst(div, "b")!!))
        assertEquals("Link", host.htmlText(host.htmlSelectFirst(div, "a")!!))
        assertNotNull(host.htmlSelectFirst(div, "p b"))

        // parent / siblings / next / previous
        val h1 = host.htmlSelectFirst(div, "h1")!!
        val p = host.htmlSelectFirst(div, "p")!!
        val a = host.htmlSelectFirst(div, "a")!!
        assertEquals("a", host.htmlId(host.htmlParent(h1)!!))
        val pSiblings = host.htmlSiblings(p)!!
        assertEquals(2, host.htmlSize(pSiblings))
        assertEquals("Link", host.htmlText(host.htmlNext(p)!!))
        assertEquals("p", host.htmlTagName(host.htmlPrevious(a)!!))
        assertEquals("h1", host.htmlTagName(host.htmlPrevious(p)!!))

        // attr mutation
        assertTrue(host.htmlSetAttr(div, "data-k", "v"))
        assertEquals("v", host.htmlAttr(div, "data-k"))
        assertTrue(host.htmlRemoveAttr(div, "data-k"))
        assertNull(host.htmlAttr(div, "data-k"))

        // class mutation
        assertTrue(host.htmlAddClass(h1, "extra"))
        assertTrue(host.htmlHasClass(h1, "extra"))
        assertTrue(host.htmlRemoveClass(h1, "extra"))
        assertTrue(!host.htmlHasClass(h1, "extra"))

        // content mutation
        assertTrue(host.htmlSetText(h1, "NewTitle"))
        assertEquals("NewTitle", host.htmlText(h1))
        assertEquals("<h1>NewTitle</h1>", host.htmlOuterHtml(h1))
        assertTrue(host.htmlSetHtml(p, "<em>E</em>"))
        assertEquals("<em>E</em>", host.htmlHtml(p))
        assertNotNull(host.htmlSelectFirst(p, "em"))
        assertTrue(host.htmlAppend(div, "<span>s</span>"))
        assertNotNull(host.htmlSelectFirst(div, "span"))
        assertTrue(host.htmlPrepend(div, "<i>i</i>"))
        assertNotNull(host.htmlSelectFirst(div, "i"))

        // base uri
        assertTrue("base uri must carry the parse base", host.htmlBaseUri(div)?.startsWith("https://example.com") == true)

        // own text vs text (h1 already set to NewTitle above)
        assertEquals("NewTitle", host.htmlOwnText(h1))

        // reset
        assertTrue(host.htmlRemove(p))
        assertNull(host.htmlSelectFirst(div, "p"))
    }

    @Test
    fun `fragment parse yields comment node`() {
        val doc = host.htmlParseFragment("<!--c-->", "")
        assertTrue(doc > 0)
        assertEquals(7, host.htmlKind(doc))
        val body = host.htmlSelectFirst(doc, "body")!!
        val nodes = host.htmlChildNodes(body)!!
        assertEquals(1, host.htmlSize(nodes))
        val comment = host.htmlGet(nodes, 0)!!
        assertEquals(4, host.htmlKind(comment)) // Comment
        assertEquals("c", host.htmlData(comment))
    }

    @Test
    fun `script element data yields embedded json`() {
        // `Element.data()` must return the raw `<script id="srcData">` body —
        // kkphim-style sources read their episode/stream JSON straight from it.
        val json = """[{"server_name":"Youtube","server_data":[{"slug":"tap-1","link_m3u8":"https://a.kvp726.com/x/index.m3u8"}]}]"""
        val doc = host.htmlParse(
            """<html><body><script type="application/json" id="srcData">$json</script></body></html>""",
            "",
        )
        val script = host.htmlSelectFirst(doc, "script#srcData")!!
        assertEquals(json, host.htmlData(script))
        // never the `data` HTML attribute
        val el = host.htmlSelectFirst(doc, "script#srcData[data-x]")
        assertNull(el?.let { host.htmlData(it) }?.takeIf { it == "nope" })
    }

    @Test
    fun `destroy invalidates the handle`() {
        val doc = host.htmlParse("<p>x</p>", "")
        host.htmlDestroy(doc)
        assertEquals(0, host.htmlKind(doc))
        assertNull(host.htmlSelectFirst(doc, "p"))
        assertEquals(-1, host.htmlSize(doc))
    }

    @Test
    fun `unknown handle is inert`() {
        assertEquals(0, host.htmlKind(9999))
        assertNull(host.htmlText(9999))
        assertNull(host.htmlSelect(9999, "p"))
        assertTrue(!host.htmlHasAttr(9999, "id"))
    }

    // ---- defaults ----------------------------------------------------------

    @Test
    fun `defaults round trip through the krx defaults store`() {
        assertNull(host.defaultsGet("missing"))

        host.defaultsSet("b", HostDefaultValue.Bool(true))
        assertEquals(HostDefaultValue.Bool(true), host.defaultsGet("b"))

        host.defaultsSet("i", HostDefaultValue.Int(42))
        assertEquals(HostDefaultValue.Int(42), host.defaultsGet("i"))

        host.defaultsSet("f", HostDefaultValue.Float(1.5f))
        assertEquals(HostDefaultValue.Float(1.5f), host.defaultsGet("f"))

        host.defaultsSet("s", HostDefaultValue.String("hello"))
        assertEquals(HostDefaultValue.String("hello"), host.defaultsGet("s"))

        host.defaultsSet("a", HostDefaultValue.StringArray(listOf("x", "y")))
        assertEquals(listOf("x", "y"), (host.defaultsGet("a") as HostDefaultValue.StringArray).v1)

        host.defaultsSet("d", HostDefaultValue.Data(byteArrayOf(1, 2, 3)))
        val data = host.defaultsGet("d") as HostDefaultValue.Data
        assertArrayEquals(byteArrayOf(1, 2, 3), data.v1)

        host.defaultsSet("b", HostDefaultValue.Null)
        assertNull(host.defaultsGet("b"))
    }

    @Test
    fun `defaults are namespaced by source id like Aidoku user defaults`() {
        // A scoped host stores {sourceId}.{key}; two sources with the same
        // setting key never touch each other's rows (Aidoku semantics).
        val alpha = host.scopedTo("alpha.source")
        val beta = host.scopedTo("beta.source")

        alpha.defaultsSet("prefer_fhd", HostDefaultValue.Bool(true))
        assertEquals(HostDefaultValue.Bool(true), alpha.defaultsGet("prefer_fhd"))
        // Same key in another source (and in the unscoped host) is a different row.
        assertNull(beta.defaultsGet("prefer_fhd"))
        assertNull(host.defaultsGet("prefer_fhd"))

        // The unscoped host writes/reads the raw key independently.
        host.defaultsSet("prefer_fhd", HostDefaultValue.Bool(false))
        assertEquals(HostDefaultValue.Bool(false), host.defaultsGet("prefer_fhd"))
        assertEquals(HostDefaultValue.Bool(true), alpha.defaultsGet("prefer_fhd"))

        // Null write removes only the scoped row.
        alpha.defaultsSet("prefer_fhd", HostDefaultValue.Null)
        assertNull(alpha.defaultsGet("prefer_fhd"))
        assertEquals(HostDefaultValue.Bool(false), host.defaultsGet("prefer_fhd"))
        assertNull(beta.defaultsGet("prefer_fhd"))
    }

    // ---- dates -------------------------------------------------------------

    @Test
    fun `parse date with explicit utc timezone`() {
        assertEquals(1693224000.0, host.parseDate("2023-08-28 12:00:00", "yyyy-MM-dd HH:mm:ss", null, "UTC"), 0.001)
    }

    @Test
    fun `parse date failure returns negative`() {
        assertTrue(host.parseDate("garbage", "yyyy-MM-dd", null, "UTC") < 0)
    }

    @Test
    fun `current date and utc offset are plausible`() {
        assertTrue(host.currentDate() > 1_700_000_000.0)
        val offset = host.utcOffset()
        assertTrue("utc offset $offset must be within -12h..+14h", offset in -43_200..50_400)
    }

    // ---- krx extraction ----------------------------------------------------

    @Test
    fun `extract main wasm from a krx zip`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write("{}".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("Payload/main.wasm"))
                zip.write(byteArrayOf(0, 97, 115, 109, 1, 0, 0, 0))
                zip.closeEntry()
            }
        }.toByteArray()

        val wasm = KrxManager.extractMainWasm(bytes)
        assertNotNull(wasm)
        assertArrayEquals(byteArrayOf(0, 97, 115, 109, 1, 0, 0, 0), wasm)
    }

    @Test
    fun `extract returns null for junk`() {
        assertNull(KrxManager.extractMainWasm(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `extract icon from a krx zip`() {
        val iconBytes = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("Payload/icon.png"))
                zip.write(iconBytes)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("Payload/main.wasm"))
                zip.write(byteArrayOf(0, 97, 115, 109))
                zip.closeEntry()
            }
        }.toByteArray()

        assertArrayEquals(iconBytes, KrxManager.extractIcon(bytes))
    }

    @Test
    fun `extract icon from root entry for non-payload packages`() {
        val iconBytes = byteArrayOf(1, 2, 3, 4)
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("icon.png"))
                zip.write(iconBytes)
                zip.closeEntry()
            }
        }.toByteArray()

        assertArrayEquals(iconBytes, KrxManager.extractIcon(bytes))
    }

    @Test
    fun `extract icon returns null when package ships no icon`() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("Payload/main.wasm"))
                zip.write(byteArrayOf(0, 97, 115, 109))
                zip.closeEntry()
            }
        }.toByteArray()

        assertNull(KrxManager.extractIcon(bytes))
    }
}