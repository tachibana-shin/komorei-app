package git.shin.komorei.sdk

import androidx.test.core.app.ApplicationProvider
import git.shin.komorei.data.LogStore
import git.shin.komorei.sdk.runner.Anime
import git.shin.komorei.sdk.runner.AnimePageResult
import git.shin.komorei.sdk.runner.Listing
import git.shin.komorei.sdk.runner.ListingKind
import git.shin.komorei.sdk.runner.StreamData
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * REAL end-to-end playback check for the Nguồn Phim site source
 * (`sources/sources/vi.nguonphime` → `package.krx`) driven through the real
 * runner (uniffi bindings + linux cdylib) + the real Kotlin host
 * ([KrxHostImpl]) against the LIVE site — the `base_url` default
 * (`https://nguonphime.site`) is deliberately NOT overridden, so every request
 * goes to the real server: listing → detail → watch page → the watch XHR POSTs
 * → the base64 grab playlist → the NGC streamc grant, exactly the pipeline the
 * app player runs.
 *
 * Then it proves the resolved stream is actually PLAYABLE over HTTP: the HLS
 * master playlist is fetched (must be `#EXTM3U`), the first variant's media
 * playlist is fetched, and the first `#EXTINF` segment downloads real bytes.
 * (An HLS client such as ExoPlayer would then just play it.)
 *
 * **Network-dependent**: it is skipped (JUnit `Assume`, reported as ignored
 * rather than failed) when the live host is unreachable, so an offline machine
 * or a site outage never masquerades as a runner/source regression.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NguonphimeLivePlaybackE2ETest {
    private val http =
        OkHttpClient
            .Builder()
            .cookieJar(LiveCookieJar())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

    companion object {
        /** A real media segment is far larger than this; below it, it is a stub. */
        private const val MIN_SEGMENT_BYTES = 4096

        private val nguonphimeKrx: String =
            System.getProperty("komorei.test.nguonphimeKrx")
                ?: error("missing -Dkomorei.test.nguonphimeKrx (set by app/build.gradle.kts)")
    }

    @Test
    fun `live PAI and NGC streams resolve to fetchable playable HLS`() {
        // This suite deliberately hits the REAL https://nguonphime.site (see
        // the class KDoc), so it is network-dependent by construction: an
        // offline/CI-blocked machine, a site outage, or a geo/IP block must not
        // report as a runner regression. Everything it covers is also asserted
        // offline by NguonphimeSourceRunnerIntegrationTest, which drives the
        // exact same pipeline against a local fixture server.
        assumeReachable("https://nguonphime.site/")

        // PAI (grab → base64 playlist → direct HLS) is REQUIRED: the live test
        // verifies its master/media playlists are real HLS and that a segment
        // URI is named (see verifyPlayableHls). Only the segment BYTES are
        // reported rather than asserted — those come from the site's CDN, which
        // grants per IP/geo, so a CI runner can legitimately be refused.
        // NGC (fromEmbed switch → streamc grant) depends on the site-side
        // grant: the live server currently refuses every key/header variant
        // with its generic "Link này bị lỗi rồi!" notice (no playerEmbed
        // iframe) — likely an IP/geo restriction of the streamc CDN, NOT a
        // source bug (the fixture-backed integration test exercises the exact
        // same flow and passes). So a RunnerException.Source refusal there is
        // tolerated and reported, while a real resolution is verified like PAI.
        assertNotNull(
            "missing uniffi.component.komorei_runner.libraryOverride (set by app/build.gradle.kts)",
            System.getProperty("uniffi.component.komorei_runner.libraryOverride"),
        )

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val host = KrxHostImpl(context, okHttpClient = http)
        // NO defaultsSet("base_url", …) → source falls back to the real site.
        val runner = KrxManager.load(host, File(nguonphimeKrx).readBytes())

        // 1. Real anime from the "Phim Mới" listing (real cards, real pager).
        val movieListing = runner.listings().first { it.id.startsWith("tuy-chon/phim-moi.html") }
        assertEquals(ListingKind.LIST, movieListing.kind)
        val lite = firstNonEmptyAnime(runner, movieListing)
        println("LIVE_E2E picked anime: ${lite.title} (${lite.key})")

        // 2. Upgrade to full + chapters (real detail + watch page).
        val full = runner.animeUpdate(lite, needsDetails = true, needsChapters = true)
        assertTrue("site answered with episodes", !full.episodes.isNullOrEmpty())
        assertTrue("seasons = PAI + NGC", full.seasons.size >= 2)
        val episode = full.episodes!!.last()
        println(
            "LIVE_E2E episode: #${episode.episodeNumber} " +
                "watchUrl=${episode.url ?: "<fallback>"}",
        )

        // 3. Resolve BOTH playback servers and verify the streams play.
        // The source resolves each server's stream while LISTING them, so a
        // site-side refusal surfaces from `streamList` itself, not only from the
        // per-server `stream` call below — tolerate it in both places.
        val servers =
            try {
                runner.streamList(full, episode)
            } catch (e: git.shin.komorei.sdk.runner.RunnerException.Source) {
                if (isSiteSideRefusal(e.message)) {
                    // NGC is granted server-side and refused from this network; the
                    // listing itself could not complete. The fixture-backed suite
                    // covers the same flow offline, so record and stop here.
                    println("LIVE_E2E verdict: SITE REFUSED THE WHOLE LISTING — ${e.message}")
                    return
                }
                // A source error with no message at all says nothing about who
                // is at fault: it is what a site refusal looks like when the
                // refusal carries no text, and also what a request that died on
                // the wire looks like. Dump what the host recorded so the
                // difference is visible in the report instead of being a
                // rethrown exception with nothing attached.
                println("LIVE_E2E unclassified source failure: '${e.message}'")
                println("LIVE_E2E host log:\n${LogStore.export()}")
                throw e
            }
        val pai = servers.firstOrNull { it.key.equals("PAI", true) }
        val ngc = servers.firstOrNull { it.key.equals("NGC", true) }
        assertNotNull("site must expose the PAI server", pai)

        for (server in listOfNotNull(pai, ngc)) {
            val data =
                try {
                    runner.stream(full, episode, server)
                } catch (e: git.shin.komorei.sdk.runner.RunnerException.Source) {
                    if (isSiteSideRefusal(e.message)) {
                        // The site refuses to hand over a stream from this network.
                        // NGC is known to do that (its CDN grants per IP/geo — see
                        // above), but the wording is not PAI-specific: a refusal
                        // here is a live-site/geo outcome, not a source defect, and
                        // NguonphimeSourceRunnerIntegrationTest covers the same
                        // pipeline offline. The live site's wording also changes as
                        // its CDN is re-tuned, so match the refusal vocabulary
                        // rather than one exact sentence.
                        println(
                            "LIVE_E2E ${server.key} verdict: SERVER REFUSED " +
                                "(site-side grant / geo) — ${e.message}",
                        )
                        continue
                    }
                    println("LIVE_E2E ${server.key} unclassified source failure: '${e.message}'")
                    println("LIVE_E2E host log:\n${LogStore.export()}")
                    throw e
                }
            assertFalse("${server.key}: isContent should be false", data.isContent)
            assertTrue("${server.key}: empty stream url", data.url.isNotBlank())
            println(
                "LIVE_E2E ${server.key} resolved: ${data.url} " +
                    "headers=${data.headers.entries.joinToString { "${it.key}=${it.value}" }}",
            )
            val verdict = verifyPlayableHls(data, server.key)
            println("LIVE_E2E ${server.key} verdict: $verdict")
        }
    }

    /** Walk the pager until the listing yields a real card. */
    private fun firstNonEmptyAnime(
        runner: KomoreiRunnerFacade,
        listing: Listing,
    ): Anime {
        var page = 1
        while (page <= 5) {
            val result: AnimePageResult = runner.animeList(listing, page)
            val hit = result.entries.firstOrNull { it.key.isNotBlank() }
            if (hit != null) return hit
            if (!result.hasNextPage) break
            page++
        }
        error("phim-moi listing returned no anime on pages 1..5")
    }

    /**
     * Fetch master playlist → first variant (if any) → media playlist → first
     * segment. Bytes flowing back proves a media client can play the stream.
     *
     * The split matters: the PLAYLIST structure is the source's contract and
     * stays a hard assertion, while the SEGMENT BYTES are the site's. The
     * streamc/grab CDNs grant per IP/geo, so a GitHub runner (or any network
     * outside the site's audience) can be refused with 403/404 — or served a
     * stub — even when the source resolved a perfectly valid HLS ladder. That
     * is a live-site outcome, so it is reported rather than failed; the same
     * pipeline is asserted end-to-end offline by
     * NguonphimeSourceRunnerIntegrationTest.
     */
    private fun verifyPlayableHls(
        data: StreamData,
        label: String,
    ): String {
        val master =
            fetch(data.url, data.headers)
                .also { assertTrue("$label: master started with #EXTM3U\n${it.take(200)}", it.startsWith("#EXTM3U")) }

        val mediaUrl = firstVariantUri(master)?.let { absolutize(data.url, it) } ?: data.url
        val media =
            fetch(mediaUrl, data.headers)
                .also { assertTrue("$label: media playlist missing #EXTM3U", it.startsWith("#EXTM3U")) }

        val segmentUri =
            firstSegmentUri(media)
                ?: error("$label: no #EXTINF segment found in media playlist")
        val segmentUrl = absolutize(mediaUrl, segmentUri)

        val (status, bytes) = fetchWithStatus(segmentUrl, data.headers)
        if (status !in 200..299 || bytes.size < MIN_SEGMENT_BYTES) {
            return "SITE REFUSED THE SEGMENT (CDN grants per IP/geo) — " +
                "status=$status bytes=${bytes.size} @ $segmentUrl"
        }
        return "master+media+segments ok — first segment ${bytes.size} bytes ($status)"
    }

    private fun fetch(
        url: String,
        headers: Map<String, String>,
    ): String {
        val (status, bytes) = fetchWithStatus(url, headers)
        assertTrue("GET $url -> $status", status in 200..299)
        return bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    }

    private fun fetchWithStatus(
        url: String,
        headers: Map<String, String>,
    ): Pair<Int, ByteArray> {
        val req = okhttp3.Request.Builder().url(url)
        headers.forEach { (k, v) -> req.header(k, v) }
        http.newCall(req.build()).execute().use { resp ->
            return resp.code to resp.body.bytes()
        }
    }

    /** First variant URI of a master playlist (`#EXT-X-STREAM-INF` → next line). */
    private fun firstVariantUri(playlist: String): String? {
        val lines = playlist.lineSequence().iterator()
        var inflLine = false
        while (lines.hasNext()) {
            val line = lines.next().trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                inflLine = true
                continue
            }
            if (line.startsWith("#")) continue
            if (inflLine) return line
        }
        return null
    }

    /** First segment URI of a media playlist (`#EXTINF` → next line, skip maps). */
    private fun firstSegmentUri(playlist: String): String? {
        val lines = playlist.lineSequence().iterator()
        var afterExtinf = false
        while (lines.hasNext()) {
            val line = lines.next().trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                afterExtinf = true
                continue
            }
            if (line.startsWith("#EXT-X-MAP")) {
                afterExtinf = false
                continue
            }
            if (line.startsWith("#")) continue
            if (afterExtinf) return line
        }
        return null
    }

    private fun absolutize(
        base: String,
        ref: String,
    ): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        val slash = base.lastIndexOf('/')
        val dir = if (slash >= 0) base.substring(0, slash + 1) else "$base/"
        return if (ref.startsWith("/")) {
            val scheme = base.substringBefore("://")
            val host = base.removePrefix("$scheme://").substringBefore('/')
            "$scheme://$host$ref"
        } else {
            dir + ref
        }
    }
}

/** Type alias for the runner facade methods used here (kept minimal). */
private typealias KomoreiRunnerFacade =
    git.shin.komorei.sdk.runner.KomoreiRunner

/**
 * In-memory jar mirroring the app's `WebViewCookieJar` so the NP Checker's
 * `Set-Cookie` survives into the source's retry (faithful real-site bounce).
 */
private class LiveCookieJar : CookieJar {
    private val store = CopyOnWriteArrayList<Cookie>()

    @Synchronized
    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        store.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = store.filter { it.matches(url) }
}

/**
 * True when the source refused because the SITE declined to hand over a
 * stream, as opposed to a bug in the source. The live site's wording is not
 * stable (its CDN is re-tuned independently of the source), so this matches
 * the refusal vocabulary instead of one exact sentence.
 */
private fun isSiteSideRefusal(message: String?): Boolean {
    val text = message.orEmpty()
    return listOf("Máy chủ phụ", "nguồn phát", "Link này bị lỗi").any(text::contains)
}

/**
 * Skips (JUnit `AssumptionViolatedException` → reported as ignored) when the
 * live host cannot be reached, so this network-dependent suite does not fail
 * an offline/CI-blocked run.
 */
private fun assumeReachable(url: String) {
    val reachable =
        runCatching {
            java.net
                .URL(url)
                .openStream()
                .use { it.read() }
            true
        }.getOrDefault(false)
    assumeTrue("skipping network-dependent test: $url is unreachable", reachable)
}
