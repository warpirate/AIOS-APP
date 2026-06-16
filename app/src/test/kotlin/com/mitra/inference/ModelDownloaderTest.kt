package com.mitra.inference

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ModelDownloaderTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `sha256 hashes known content to known hex`() = runBlocking {
        // Well-known SHA-256 of the ASCII string "hello".
        val f = tmp.newFile("hello.bin").apply { writeText("hello") }
        val hex = ModelDownloader.sha256(f)
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", hex)
    }

    @Test
    fun `sha256 of empty file is the empty-input digest`() = runBlocking {
        val f = tmp.newFile("empty.bin")
        val hex = ModelDownloader.sha256(f)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex)
    }

    @Test
    fun `download verifies size and hash on success and leaves dest intact`() = runBlocking {
        val payload = "hello".toByteArray()
        val dest = File(tmp.root, "model.bin")
        val server = oneShotHttpServer(payload)
        try {
            ModelDownloader(
                dest = dest,
                expectedSizeBytes = payload.size.toLong(),
                expectedSha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            ).download(server.url) { /* progress ignored */ }
            assertTrue(dest.exists())
            assertEquals(payload.size.toLong(), dest.length())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `download deletes dest and throws when sha mismatches`() = runBlocking {
        val payload = "hello".toByteArray()
        val dest = File(tmp.root, "model.bin")
        val server = oneShotHttpServer(payload)
        try {
            val ex = assertThrows(IOException::class.java) {
                runBlocking {
                    ModelDownloader(
                        dest = dest,
                        expectedSizeBytes = payload.size.toLong(),
                        expectedSha256 = "0".repeat(64),
                    ).download(server.url) { /* progress ignored */ }
                }
            }
            assertTrue(ex.message!!.contains("hash mismatch", ignoreCase = true))
            assertFalse("verifier must delete corrupt dest", dest.exists())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `download deletes dest and throws when size mismatches`() = runBlocking {
        val payload = "hello".toByteArray()
        val dest = File(tmp.root, "model.bin")
        val server = oneShotHttpServer(payload)
        try {
            val ex = assertThrows(IOException::class.java) {
                runBlocking {
                    ModelDownloader(
                        dest = dest,
                        expectedSizeBytes = 9999L,
                        expectedSha256 = "",
                    ).download(server.url) { /* progress ignored */ }
                }
            }
            assertTrue(ex.message!!.contains("size mismatch", ignoreCase = true))
            assertFalse("verifier must delete wrong-sized dest", dest.exists())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `download with unset pins skips verification`() = runBlocking {
        val payload = "anything goes".toByteArray()
        val dest = File(tmp.root, "model.bin")
        val server = oneShotHttpServer(payload)
        try {
            ModelDownloader(
                dest = dest,
                expectedSizeBytes = 0L,
                expectedSha256 = "",
            ).download(server.url) { /* progress ignored */ }
            assertTrue(dest.exists())
            assertEquals(payload.size.toLong(), dest.length())
        } finally {
            server.stop()
        }
    }

    // Minimal stdlib-only HTTP server: serves a single static body once on a random localhost port.
    // Avoids pulling MockWebServer into unit-test deps. Closes the socket after one request.
    private class OneShotServer(val url: String, private val thread: Thread, private val socket: java.net.ServerSocket) {
        fun stop() {
            try { socket.close() } catch (_: Throwable) {}
            thread.interrupt()
        }
    }

    private fun oneShotHttpServer(body: ByteArray): OneShotServer {
        val socket = java.net.ServerSocket(0)
        val port = socket.localPort
        val thread = Thread {
            try {
                val client = socket.accept()
                client.getInputStream().bufferedReader().let { reader ->
                    // Drain request line + headers
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isEmpty()) break
                    }
                }
                val out = client.getOutputStream()
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Length: ${body.size}\r\n")
                    append("Content-Type: application/octet-stream\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(headers.toByteArray(Charsets.US_ASCII))
                out.write(body)
                out.flush()
                client.close()
            } catch (_: Throwable) {
                // socket closed or thread interrupted — fine
            }
        }
        thread.isDaemon = true
        thread.start()
        return OneShotServer("http://127.0.0.1:$port/model", thread, socket)
    }
}
