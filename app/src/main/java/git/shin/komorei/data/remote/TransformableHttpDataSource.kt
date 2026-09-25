package git.shin.komorei.data.remote

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import git.shin.komorei.model.StreamData
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Delegating [HttpDataSource] that hooks into every media request so a source can:
 *  - rewrite request URLs via [SegmentUrlInterceptor] (session tokens, signatures…)
 *  - transform response bytes via [SegmentDataInterceptor] (de-obfuscate/decrypt segments)
 *
 * When a [dataInterceptor] is present the whole body is read and transformed upfront, which
 * trades memory for the ability to fully control what the media engine sees.
 *
 * Inspired by git.shin.animevsub.data.remote.TransformableHttpDataSource.
 */
@UnstableApi
class TransformableHttpDataSource(
    private val delegate: HttpDataSource,
    private val streamData: StreamData?,
    private val urlInterceptor: SegmentUrlInterceptor?,
    private val dataInterceptor: SegmentDataInterceptor?
) : HttpDataSource by delegate {

    private var dataStream: ByteArrayInputStream? = null
    private var isDelegateOpened = false

    override fun open(dataSpec: DataSpec): Long {
        var uri = dataSpec.uri.toString()
        if (urlInterceptor != null) {
            uri = urlInterceptor.intercept(streamData, uri)
        }

        val resolvedSpec = dataSpec.buildUpon()
            .setUri(Uri.parse(uri))
            .build()

        val delegateLength = delegate.open(resolvedSpec)
        isDelegateOpened = true

        if (dataInterceptor != null) {
            val fullData = readAllFromDelegate()
            val transformed = dataInterceptor.intercept(streamData, uri, fullData)
            dataStream = ByteArrayInputStream(transformed)
            return transformed.size.toLong()
        }

        return delegateLength
    }

    private fun readAllFromDelegate(): ByteArray {
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        output.use { os ->
            while (true) {
                val bytesRead = delegate.read(buffer, 0, buffer.size)
                if (bytesRead == -1) break
                os.write(buffer, 0, bytesRead)
            }
            return os.toByteArray()
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = if (dataStream != null) {
        dataStream!!.read(buffer, offset, length)
    } else {
        delegate.read(buffer, offset, length)
    }

    override fun close() {
        try {
            dataStream?.close()
        } catch (_: IOException) {
            // A half-consumed response often throws on close; there is nothing
            // left to salvage and close() must not propagate, so the error is
            // deliberately swallowed here.
        }
        dataStream = null
        if (isDelegateOpened) {
            isDelegateOpened = false
            delegate.close()
        }
    }
}