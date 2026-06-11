package com.mitra.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * THE ONLY file in the project permitted to touch the network (privacy invariant / lint allowlist).
 * Downloads the model once to app storage, resumably. After this, the app never uses the network.
 *
 * Pause = cancel the calling coroutine; the partial `.part` file is kept. Resume = call [download]
 * again; it continues from where it stopped via an HTTP Range request.
 */
class ModelDownloader(
    private val dest: File,
) {
    data class Progress(
        val downloaded: Long,
        val total: Long,
    )

    /** True if the fully-downloaded model already exists (skip the network entirely). */
    fun isComplete(): Boolean = dest.exists() && dest.length() > 0

    suspend fun download(url: String, onProgress: (Progress) -> Unit) =
        withContext(Dispatchers.IO) {
            val part = File(dest.parentFile, dest.name + ".part")
            var have = if (part.exists()) part.length() else 0L

            val conn =
                (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    if (have > 0) setRequestProperty("Range", "bytes=$have-")
                }
            conn.connect()
            when (conn.responseCode) {
                HttpURLConnection.HTTP_PARTIAL -> Unit // server honored resume
                HttpURLConnection.HTTP_OK -> {
                    have = 0L
                    part.delete()
                } // no resume; start over
                else -> {
                    val code = conn.responseCode
                    conn.disconnect()
                    throw IOException("Download failed: HTTP $code")
                }
            }

            val remaining = conn.contentLengthLong.coerceAtLeast(0L)
            val total = have + remaining

            try {
                conn.inputStream.use { input ->
                    RandomAccessFile(part, "rw").use { out ->
                        out.seek(have)
                        onProgress(Progress(have, total))
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive() // cooperative pause/cancel
                            val read = input.read(buf)
                            if (read < 0) break
                            out.write(buf, 0, read)
                            have += read
                            onProgress(Progress(have, total))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            onProgress(Progress(dest.length(), dest.length()))
        }
}
