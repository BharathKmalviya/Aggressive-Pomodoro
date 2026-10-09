package com.pomodoro.platform

import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLHandshakeException
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Call
import okhttp3.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HttpsUpdateTransportTest {
    private val loopback = InetAddress.getByName("127.0.0.1")

    /** Real socket IO only: these tests never launch the desktop application. */
    private fun withServer(handler: (Socket) -> Unit, test: (URI) -> Unit) {
        val accepted = AtomicReference<Socket>()
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0, 1, loopback).use { server ->
            server.soTimeout = 5_000
            val serving = executor.submit {
                server.accept().use { socket ->
                    accepted.set(socket)
                    socket.soTimeout = 5_000
                    handler(socket)
                }
            }
            try {
                test(URI("http://update.test:${server.localPort}/installer?private=secret"))
                serving.get(5, TimeUnit.SECONDS)
            } finally {
                accepted.get()?.close()
                server.close()
                executor.shutdownNow()
                executor.awaitTermination(5, TimeUnit.SECONDS)
            }
        }
    }

    private fun readRequest(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader()
        while (!reader.readLine().isNullOrEmpty()) Unit
    }

    private fun client() = HttpsUpdateTransport.defaultClient.newBuilder()
        .dns(Dns { listOf(loopback) }).proxy(Proxy.NO_PROXY)

    @Test fun unavailableFirstAddressFallsBackToNextSystemRoute() {
        val failed = AtomicBoolean(false)
        withServer({ socket ->
            readRequest(socket)
            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray())
        }) { uri ->
            val http = client().dns(Dns { listOf(InetAddress.getByName("127.0.0.2"), loopback) })
                .eventListener(object : EventListener() {
                    override fun connectFailed(call: Call, address: java.net.InetSocketAddress,
                        proxy: Proxy, protocol: Protocol?, error: IOException) { failed.set(true) }
                }).build()
            HttpsUpdateTransport(http).open(uri).use {
                assertEquals(200, it.status)
                assertEquals("OK", it.body.readBytes().decodeToString())
            }
            assertTrue(failed.get(), "The first route must fail before the reachable route succeeds")
        }
    }

    @Test fun cancellationInterruptsWaitingForResponseHeaders() = cancellationWhileBlocked(bodyRead = false)
    @Test fun cancellationInterruptsWaitingForBodyBytes() = cancellationWhileBlocked(bodyRead = true)

    private fun cancellationWhileBlocked(bodyRead: Boolean) {
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        try {
            withServer({ socket ->
                readRequest(socket)
                if (bodyRead) {
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n".toByteArray())
                    socket.getOutputStream().flush()
                }
                blocked.countDown()
                assertTrue(release.await(5, TimeUnit.SECONDS))
            }) { uri ->
                val future = executor.submit<Unit> {
                    HttpsUpdateTransport(client().build()).open(uri, cancelled::get).use {
                        if (bodyRead) it.body.read()
                    }
                }
                try {
                    assertTrue(blocked.await(3, TimeUnit.SECONDS))
                    cancelled.set(true)
                    val failure = assertFailsWith<ExecutionException> { future.get(3, TimeUnit.SECONDS) }
                    assertIs<java.util.concurrent.CancellationException>(failure.cause)
                } finally { release.countDown() }
            }
        } finally {
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    @Test fun redirectsRemainVisibleForServiceAllowlistValidation() {
        withServer({ socket ->
            readRequest(socket)
            socket.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: https://untrusted.invalid/\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        }) { uri ->
            HttpsUpdateTransport(client().build()).open(uri).use {
                assertEquals(302, it.status)
                assertEquals("https://untrusted.invalid/", it.location)
            }
        }
    }

    @Test fun connectionErrorsAreSpecificAndExcludeSignedUrlDetails() {
        val http = client().dns(Dns { throw UnknownHostException("private=secret") }).build()
        val failure = assertFailsWith<IOException> {
            HttpsUpdateTransport(http).open(URI("https://update.test/installer?private=secret"))
        }
        assertTrue(failure.message!!.contains("find GitHub"))
        assertTrue(!failure.message!!.contains("secret"))
        assertTrue(HttpsUpdateTransport.connectionMessage(SSLHandshakeException("secret")).contains("secure connection"))
        val blocked = CountDownLatch(1)
        withServer({ socket ->
            readRequest(socket)
            assertTrue(blocked.await(3, TimeUnit.SECONDS))
        }) { uri ->
            try {
                val timeout = assertFailsWith<IOException> {
                    HttpsUpdateTransport(client().readTimeout(150, TimeUnit.MILLISECONDS).build()).open(uri)
                }
                assertTrue(timeout.message!!.contains("timed out"))
                assertTrue(!timeout.message!!.contains("secret"))
            } finally { blocked.countDown() }
        }
    }
}
