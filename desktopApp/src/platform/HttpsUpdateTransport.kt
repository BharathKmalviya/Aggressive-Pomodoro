package com.pomodoro.platform

import java.io.FilterInputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import okhttp3.OkHttpClient
import okhttp3.Request

/** System DNS and normal TLS trust, with alternate-address fallback and cancellable blocking IO. */
internal class HttpsUpdateTransport(private val client: OkHttpClient = defaultClient) : UpdateTransport {
    override fun open(uri: URI): UpdateResponse = open(uri) { false }

    override fun open(uri: URI, isCancelled: () -> Boolean): UpdateResponse {
        val owner = Thread.currentThread()
        fun cancelled() = isCancelled() || owner.isInterrupted
        if (cancelled()) throw CancellationException("Update download canceled.")
        val call = client.newCall(Request.Builder().url(uri.toString())
            .header("User-Agent", "AggressivePomodoro-UpdateChecker")
            .header("Accept", if (uri.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
            .header("X-GitHub-Api-Version", "2026-03-10").build())
        // The controller's token must interrupt connect and read, not just the next copy iteration.
        val watcher = cancellationWatcher.scheduleWithFixedDelay({
            if (cancelled()) call.cancel()
        }, 0, 100, TimeUnit.MILLISECONDS)
        fun failure(error: IOException): Exception = if (cancelled()) {
            CancellationException("Update download canceled.").also { it.initCause(error) }
        } else IOException(connectionMessage(error), error)
        try {
            val response = call.execute()
            val body = object : FilterInputStream(response.body.byteStream()) {
                override fun read(): Int = try { super.read() } catch (error: IOException) { throw failure(error) }
                override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                    try { super.read(bytes, offset, length) } catch (error: IOException) { throw failure(error) }
            }
            return UpdateResponse(response.code, body,
                response.body.contentLength().takeIf { it >= 0 }, response.header("Location")) {
                watcher.cancel(false)
                response.close()
            }
        } catch (error: IOException) {
            watcher.cancel(false)
            call.cancel()
            throw failure(error)
        } catch (error: Exception) {
            watcher.cancel(false)
            call.cancel()
            throw error
        }
    }

    companion object {
        internal val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .fastFallback(true)
            // GitHubUpdateService validates every redirect before opening it.
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        private val cancellationWatcher = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "update-cancellation").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }

        internal fun connectionMessage(error: IOException): String = when (error) {
            is UnknownHostException -> "Could not find GitHub's download server. Check your connection and try again."
            is SSLException -> "Could not establish a secure connection to GitHub. Check your system clock and network, then try again."
            is InterruptedIOException -> "The GitHub connection timed out. Try the download again."
            else -> "Could not connect to GitHub's download server. Try again; if it continues, check your network."
        }
    }
}
