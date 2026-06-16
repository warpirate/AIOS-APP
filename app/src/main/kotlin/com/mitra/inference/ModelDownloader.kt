package com.mitra.inference

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * THE ONLY file in the project permitted to touch the network (privacy invariant / lint allowlist).
 * Downloads the model once to app storage, resumably. After this, the app never uses the network.
 *
 * Pause = cancel the calling coroutine; the partial `.part` file is kept. Resume = call [download]
 * again; it continues from where it stopped via an HTTP Range request.
 *
 * Post-download integrity: when [expectedSizeBytes] and [expectedSha256] are non-zero / non-blank,
 * the renamed [dest] is size- and SHA-256-verified. Mismatch deletes the file and throws so the
 * caller can surface a clean retry path instead of letting [LiteRtBrain] crash later on bad bytes.
 * The default constructor pulls pins from [ModelRegistry]; pass empty values in tests to opt out.
 */
class ModelDownloader(
    private val dest: File,
    private val expectedSizeBytes: Long = ModelRegistry.EXPECTED_SIZE_BYTES,
    private val expectedSha256: String = ModelRegistry.EXPECTED_SHA256,
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
            verifyIntegrity(dest)
            onProgress(Progress(dest.length(), dest.length()))
        }

    /** Verifies [file] matches [expectedSizeBytes] and [expectedSha256]. Deletes the file and
     *  throws [IOException] on mismatch so the next [download] call starts fresh. Skips when the
     *  pins are unset (size 0 or blank hash) to keep tests + dev-rebuilds unblocked. */
    private suspend fun verifyIntegrity(file: File) =
        withContext(Dispatchers.IO) {
            if (expectedSizeBytes <= 0 && expectedSha256.isBlank()) return@withContext

            if (expectedSizeBytes > 0 && file.length() != expectedSizeBytes) {
                val actualSize = file.length()
                file.delete()
                throw IOException("Model size mismatch: expected $expectedSizeBytes bytes, got $actualSize")
            }
            if (expectedSha256.isNotBlank()) {
                val actual = sha256(file)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    file.delete()
                    throw IOException("Model hash mismatch: expected $expectedSha256, got $actual")
                }
            }
        }

    companion object {
        /** Streams [file] through SHA-256 in 64 KiB chunks. Lowercase hex output. */
        suspend fun sha256(file: File): String =
            withContext(Dispatchers.IO) {
                val md = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buf)
                        if (read < 0) break
                        md.update(buf, 0, read)
                    }
                }
                md.digest().joinToString("") { "%02x".format(it) }
            }
    }
}
