package git.shin.komorei.data.remote

import git.shin.komorei.model.StreamData

/**
 * Rewrites the URL of every request made by the media engine (playlist, segments, chunks)
 * before it is fetched. Sources use this to decorate URLs with session tokens, referers,
 * or server-side signatures that change per request.
 *
 * Inspired by git.shin.animevsub.data.remote.SegmentUrlInterceptor.
 */
fun interface SegmentUrlInterceptor {
    fun intercept(streamData: StreamData?, requestUrl: String): String
}