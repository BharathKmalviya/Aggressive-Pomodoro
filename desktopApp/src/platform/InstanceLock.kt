package com.pomodoro.platform

import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** One profile owner, with a small local mailbox for subsequent launcher activations. */
class InstanceLock private constructor(
    private val directory: Path,
    private val channel: FileChannel,
    private val lock: FileLock,
    initialDelivered: String?,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private var delivered: String? = initialDelivered

    /** IO-thread only. Requests remain on disk until the owner's UI is ready to accept them. */
    fun activationRequest(): String? {
        if (closed.get()) return null
        return readToken(directory.resolve(REQUEST))?.takeUnless { it == delivered }?.also { delivered = it }
    }

    /** Call on IO after scheduling the event-thread window activation. */
    fun acknowledgeActivation(token: String) {
        if (closed.get()) return
        require(validToken(token))
        writeToken(directory.resolve(RESPONSE), token)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            // Stale activation data must never prevent a successful session shutdown.
            runCatching { clearMailbox(directory) }
        } finally {
            try { lock.release() } finally { channel.close() }
        }
    }

    companion object {
        private const val REQUEST = "activation.request"
        private const val RESPONSE = "activation.response"

        fun acquire(directory: Path): InstanceLock? {
            Files.createDirectories(directory)
            val channel = FileChannel.open(directory.resolve("instance.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            try {
                val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                if (lock == null) {
                    channel.close()
                    return null
                }
                val stale = try {
                    clearMailbox(directory)
                    null
                } catch (_: Exception) {
                    // A damaged mailbox must not block normal startup or replay an old request.
                    readToken(directory.resolve(REQUEST))
                }
                return InstanceLock(directory, channel, lock, stale)
            } catch (error: Exception) {
                channel.close()
                throw error
            }
        }

        /** Startup-thread only; never bypass ownership if delivery fails or an older owner cannot reply. */
        fun activateExisting(directory: Path, timeoutMs: Long = 5_000): Boolean {
            require(timeoutMs in 1..10_000)
            val token = UUID.randomUUID().toString()
            val started = System.nanoTime()
            var lastSent = Long.MIN_VALUE
            return try {
                while ((System.nanoTime() - started) / 1_000_000 < timeoutMs) {
                    val elapsed = (System.nanoTime() - started) / 1_000_000
                    // Retry the same token through an owner-startup cleanup or competing launches.
                    if (lastSent == Long.MIN_VALUE || elapsed - lastSent >= 500) {
                        try {
                            writeToken(directory.resolve(REQUEST), token)
                            lastSent = elapsed
                        } catch (_: IOException) {
                            // A competing write or temporary Windows sharing conflict is retryable.
                            // Keep the same token and original bounded deadline; never bypass ownership.
                        }
                    }
                    if (readToken(directory.resolve(RESPONSE)) == token) return true
                    Thread.sleep(25)
                }
                false
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                false
            } catch (_: Exception) {
                false
            }
        }

        private fun clearMailbox(directory: Path) {
            Files.deleteIfExists(directory.resolve(REQUEST))
            Files.deleteIfExists(directory.resolve(RESPONSE))
        }

        private fun readToken(path: Path): String? = try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) != 36L) null
            else Files.newInputStream(path).use { input ->
                val bytes = input.readNBytes(37)
                if (bytes.size != 36) null else bytes.toString(Charsets.US_ASCII).takeIf(::validToken)
            }
        } catch (_: Exception) { null }

        private fun validToken(token: String): Boolean = try {
            token.length == 36 && UUID.fromString(token).toString() == token
        } catch (_: IllegalArgumentException) { false }

        private fun writeToken(path: Path, token: String) {
            val temporary = Files.createTempFile(path.parent, ".activation-", ".tmp")
            try {
                Files.writeString(temporary, token, Charsets.US_ASCII)
                try {
                    Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }
}
