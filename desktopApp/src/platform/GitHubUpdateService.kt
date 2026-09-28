package com.pomodoro.platform

import com.pomodoro.domain.AppRelease
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.net.URI
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

/** Blocking desktop IO. Checking, downloading and installation are separate user actions. */
interface UpdateService {
    fun check(currentVersion: String): AppRelease?
    fun download(release: AppRelease, targetDirectory: Path,
        onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean): Path
    fun verifyBeforeInstall(path: Path, release: AppRelease)
}

class GitHubUpdateService internal constructor(private val transport: UpdateTransport) : UpdateService {
    constructor() : this(HttpsUpdateTransport())

    private data class VerifiedDownload(val release: AppRelease, val sha256: String)
    // An editable checksum sidecar cannot authorize installation. Only this process's successful
    // download can populate this registry; restarting requires another explicit download.
    private val verified = ConcurrentHashMap<Path, VerifiedDownload>()

    override fun check(currentVersion: String): AppRelease? {
        val current = versionParts(currentVersion)
        val text = fetchSmall(URI(LATEST_URL), MAX_METADATA_BYTES)
        val metadata = try { Json.parseToJsonElement(text) as? JsonObject }
        catch (error: Exception) { throw IOException("GitHub returned invalid release information.", error) }
            ?: throw IOException("GitHub returned invalid release information.")
        if (metadata.flag("draft") || metadata.flag("prerelease")) {
            throw IOException("GitHub did not return a stable published release.")
        }
        val tag = metadata.text("tag_name")
        if (!tag.startsWith("v")) throw IOException("The release version is not supported.")
        val version = tag.removePrefix("v")
        val available = versionParts(version)
        if (compareVersions(available, current) <= 0) return null
        val installerName = "AggressivePomodoro-$version.msi"
        val assets = metadata["assets"] as? JsonArray ?: throw IOException("The release has no downloadable files.")
        fun asset(name: String): JsonObject {
            val matches = assets.mapNotNull { it as? JsonObject }.filter { it.text("name") == name }
            return matches.singleOrNull() ?: throw IOException("The release must contain exactly one $name file.")
        }
        val installer = asset(installerName)
        val checksum = asset(CHECKSUM_NAME)
        if (installer.text("state") != "uploaded" || checksum.text("state") != "uploaded") {
            throw IOException("The release download is not ready yet. Try again later.")
        }
        if (checksum.number("size") !in 1..MAX_CHECKSUM_BYTES) throw IOException("The release checksum file is invalid.")
        return AppRelease(version, metadata.text("html_url"), installerName,
            installer.text("browser_download_url"), installer.number("size"),
            checksum.text("browser_download_url")).also(::validateRelease)
    }

    override fun download(release: AppRelease, targetDirectory: Path,
        onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean): Path {
        validateRelease(release)
        checkCancelled(isCancelled)
        val sums = fetchSmall(URI(release.checksumUrl), MAX_CHECKSUM_BYTES, isCancelled)
        val expected = expectedHash(sums, release.installerName)
        checkCancelled(isCancelled)
        Files.createDirectories(targetDirectory)
        val root = targetDirectory.toRealPath()
        val directory = Files.createTempDirectory(root, "download-v${release.version}-")
        val partial = directory.resolve("installer.part")
        val installer = directory.resolve(release.installerName)
        val checksum = directory.resolve(CHECKSUM_NAME)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            onProgress(0, release.installerSize)
            var lastProgressBytes = 0L
            var lastProgressAt = System.nanoTime()
            val total = openResponse(URI(release.installerUrl), MAX_INSTALLER_BYTES, isCancelled).use { response ->
                val declaredSize = response.contentLength
                if (declaredSize != null && declaredSize != release.installerSize) {
                    throw IOException("The installer size differs from the published release.")
                }
                Files.newOutputStream(partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                    copyBounded(response.body, output, release.installerSize, isCancelled,
                        onBytes = { bytes, count -> digest.update(bytes, 0, count) },
                        onProgress = { received ->
                            val now = System.nanoTime()
                            if (received == release.installerSize || received - lastProgressBytes >= 1_024 * 1_024 ||
                                now - lastProgressAt >= 200_000_000L) {
                                onProgress(received, release.installerSize)
                                lastProgressBytes = received
                                lastProgressAt = now
                            }
                        })
                }
            }
            if (total != release.installerSize) throw IOException("The installer download was incomplete. Download it again.")
            if (digest.digest().hex() != expected) throw IOException("Installer checksum verification failed. The file was discarded.")
            checkCancelled(isCancelled)
            Files.writeString(checksum, "$expected  ${release.installerName}\n", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
            try { Files.move(partial, installer, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: AtomicMoveNotSupportedException) { Files.move(partial, installer) }
            checkCancelled(isCancelled)
            verified[installer] = VerifiedDownload(release, expected)
            return installer
        } catch (error: Exception) {
            verified.remove(installer)
            // Every path is a fixed leaf in a newly created directory owned by this invocation.
            for (path in listOf(partial, installer, checksum, directory)) {
                try { Files.deleteIfExists(path) } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
            }
            throw error
        }
    }

    override fun verifyBeforeInstall(path: Path, release: AppRelease) {
        validateRelease(release)
        val candidate = path.toAbsolutePath().normalize()
        val proof = verified[candidate] ?: throw IOException("Download this installer again before installing it.")
        if (proof.release != release || candidate.fileName.toString() != release.installerName ||
            Files.isSymbolicLink(candidate.parent) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(candidate) != release.installerSize) {
            throw IOException("The downloaded installer changed. Download it again before installing.")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newByteChannel(candidate, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            Channels.newInputStream(channel).use { input ->
                val length = copyBounded(input, OutputStream.nullOutputStream(), release.installerSize, { false },
                    onBytes = { bytes, count -> digest.update(bytes, 0, count) })
                if (length != release.installerSize) throw IOException("The downloaded installer size changed.")
            }
        }
        if (digest.digest().hex() != proof.sha256) {
            throw IOException("Installer checksum verification failed. Download it again before installing.")
        }
    }

    private fun fetchSmall(uri: URI, maximum: Long, isCancelled: () -> Boolean = { false }): String =
        openResponse(uri, maximum, isCancelled).use { response ->
            ByteArrayOutputStream().use { output ->
                copyBounded(response.body, output, maximum, isCancelled)
                output.toString(Charsets.UTF_8)
            }
        }

    private fun openResponse(initial: URI, maximum: Long, isCancelled: () -> Boolean): UpdateResponse {
        var uri = initial
        repeat(6) { redirect ->
            checkCancelled(isCancelled)
            val response = transport.open(uri)
            if (response.status in listOf(301, 302, 303, 307, 308)) {
                val location = response.location
                response.close()
                if (redirect == 5 || location == null) throw IOException("The download redirected too many times.")
                uri = try { uri.resolve(location) } catch (error: Exception) { throw IOException("The download redirect is invalid.", error) }
                if (!allowedRedirect(uri, initial)) throw IOException("The download redirected outside the trusted GitHub release servers.")
            } else {
                if (response.status != 200) {
                    response.close()
                    throw IOException(when (response.status) {
                        403, 429 -> "GitHub is limiting update requests. Try again later."
                        404 -> "The release file is unavailable. Check for updates again later."
                        else -> "GitHub could not complete the update request (HTTP ${response.status})."
                    })
                }
                if (response.contentLength?.let { it < 0 || it > maximum } == true) {
                    response.close()
                    throw IOException("The update response exceeds the allowed size.")
                }
                return response
            }
        }
        throw IOException("The download redirected too many times.")
    }

    private fun allowedRedirect(uri: URI, initial: URI): Boolean {
        if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null || uri.port !in listOf(-1, 443)) return false
        if (initial.toString() == LATEST_URL) return uri == initial
        if (uri.host == "github.com") return uri == initial
        return uri.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")
    }

    private fun validateRelease(release: AppRelease) {
        versionParts(release.version)
        val base = "$REPOSITORY_URL/releases"
        val expectedName = "AggressivePomodoro-${release.version}.msi"
        if (release.installerName != expectedName || release.installerSize !in 1..MAX_INSTALLER_BYTES ||
            release.releaseUrl != "$base/tag/v${release.version}" ||
            release.installerUrl != "$base/download/v${release.version}/$expectedName" ||
            release.checksumUrl != "$base/download/v${release.version}/$CHECKSUM_NAME") {
            throw IOException("The release does not contain a trusted version-matched Windows installer.")
        }
    }

    private fun expectedHash(text: String, installerName: String): String {
        val hashes = text.lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val match = Regex("^([a-fA-F0-9]{64})[ \\t]+\\*?([^\\r\\n]+)$").matchEntire(line.trimEnd())
                ?: throw IOException("The release checksum file is malformed.")
            match.groupValues[1].lowercase().takeIf { match.groupValues[2] == installerName }
        }.toList()
        return hashes.singleOrNull() ?: throw IOException("The checksum must name the exact installer once.")
    }

    private fun copyBounded(input: InputStream, output: OutputStream, maximum: Long, isCancelled: () -> Boolean,
        onBytes: (ByteArray, Int) -> Unit = { _, _ -> }, onProgress: (Long) -> Unit = {}): Long {
        val started = System.nanoTime()
        val buffer = ByteArray(32 * 1_024)
        var received = 0L
        while (true) {
            checkCancelled(isCancelled)
            if (System.nanoTime() - started > 600_000_000_000L) throw IOException("The update download timed out. Try again.")
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            received += count
            if (received > maximum) throw IOException("The update response exceeds the allowed size.")
            output.write(buffer, 0, count)
            onBytes(buffer, count)
            onProgress(received)
        }
        checkCancelled(isCancelled)
        return received
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) throw CancellationException("Update download canceled.")
    }

    private fun versionParts(value: String): List<BigInteger> {
        if (value.length > 64 || !Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(value)) {
            throw IOException("The app version must use major.minor.patch numbers.")
        }
        return value.split('.').map(::BigInteger)
    }

    private fun compareVersions(left: List<BigInteger>, right: List<BigInteger>): Int {
        for (index in 0..2) left[index].compareTo(right[index]).let { if (it != 0) return it }
        return 0
    }

    private fun JsonObject.text(name: String): String = (get(name) as? JsonPrimitive)
        ?.takeIf { it.isString }?.content ?: throw IOException("GitHub release information is missing $name.")
    private fun JsonObject.flag(name: String): Boolean = (get(name) as? JsonPrimitive)
        ?.takeUnless { it.isString }?.booleanOrNull ?: throw IOException("GitHub release information is missing $name.")
    private fun JsonObject.number(name: String): Long = (get(name) as? JsonPrimitive)
        ?.takeUnless { it.isString }?.longOrNull ?: throw IOException("GitHub release information is missing $name.")
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }

    companion object {
        const val REPOSITORY_URL = "https://github.com/BharathKmalviya/Aggressive-Pomodoro"
        internal const val LATEST_URL = "https://api.github.com/repos/BharathKmalviya/Aggressive-Pomodoro/releases/latest"
        private const val CHECKSUM_NAME = "SHA256SUMS.txt"
        internal const val MAX_INSTALLER_BYTES = 512L * 1_024 * 1_024
        private const val MAX_METADATA_BYTES = 1_024L * 1_024
        private const val MAX_CHECKSUM_BYTES = 16L * 1_024
    }
}

internal fun interface UpdateTransport {
    fun open(uri: URI): UpdateResponse
}

internal class UpdateResponse(
    val status: Int,
    val body: InputStream,
    val contentLength: Long? = null,
    val location: String? = null,
    private val disconnect: () -> Unit = {},
) : AutoCloseable {
    override fun close() { try { body.close() } finally { disconnect() } }
}

private class HttpsUpdateTransport : UpdateTransport {
    override fun open(uri: URI): UpdateResponse {
        val connection = uri.toURL().openConnection() as HttpsURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "AggressivePomodoro-UpdateChecker")
        connection.setRequestProperty("Accept", if (uri.host == "api.github.com") "application/vnd.github+json" else "application/octet-stream")
        connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
        try {
            val status = connection.responseCode
            return UpdateResponse(status, if (status == 200) connection.inputStream else InputStream.nullInputStream(),
                connection.getHeaderFieldLong("Content-Length", -1).takeIf { it >= 0 },
                connection.getHeaderField("Location"), connection::disconnect)
        } catch (error: Exception) {
            connection.disconnect()
            throw IOException("Could not reach GitHub. Check your connection and try again.", error)
        }
    }
}
