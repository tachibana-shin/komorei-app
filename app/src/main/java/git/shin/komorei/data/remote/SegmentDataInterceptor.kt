package git.shin.komorei.data.remote

import git.shin.komorei.model.StreamData

/**
 * Transforms the raw bytes of every response body fetched by the media engine
 * (e.g. de-obfuscate / decrypt HLS segments or rewrite a mangled playlist).
 *
 * Inspired by git.shin.animevsub.data.remote.SegmentDataInterceptor.
 */
fun interface SegmentDataInterceptor {
    fun intercept(streamData: StreamData?, segmentUrl: String, data: ByteArray): ByteArray
}