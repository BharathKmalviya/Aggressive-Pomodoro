package com.pomodoro.platform

import com.pomodoro.domain.AppRelease
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubUpdateServiceTest {
    private val repository = GitHubUpdateService.REPOSITORY_URL
    private val latest = GitHubUpdateService.LATEST_URL
    private val installerBytes = "A verified test installer payload".toByteArray()

    private fun release(version: String = "0.2.0", size: Long = installerBytes.size.toLong()): AppRelease = AppRelease(
        version, "$repository/releases/tag/v$version", "AggressivePomodoro-$version.msi",
        "$repository/releases/download/v$version/AggressivePomodoro-$version.msi", size,
        "$repository/releases/download/v$version/SHA256SUMS.txt")

    private fun metadata(release: AppRelease = release()): String = """
        {"tag_name":"v${release.version}","draft":false,"prerelease":false,
         "html_url":"${release.releaseUrl}","assets":[
         {"name":"${release.installerName}","state":"uploaded","size":${release.installerSize},"browser_download_url":"${release.installerUrl}"},
         {"name":"SHA256SUMS.txt","state":"uploaded","size":100,"browser_download_url":"${release.checksumUrl}"}]}
    """.trimIndent()

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private class FakeTransport : UpdateTransport {
        val routes = mutableMapOf<String, () -> UpdateResponse>()
        val requests = mutableListOf<String>()
        fun text(uri: String, content: String) = bytes(uri, content.toByteArray())
        fun bytes(uri: String, content: ByteArray, length: Long? = content.size.toLong()) {
            routes[uri] = { UpdateResponse(200, ByteArrayInputStream(content), length) }
        }
        override fun open(uri: URI): UpdateResponse {
            requests += uri.toString()
            return routes[uri.toString()]?.invoke() ?: error("Unexpected request: $uri")
        }
    }

    private fun transport(release: AppRelease = release(), bytes: ByteArray = installerBytes): FakeTransport =
        FakeTransport().apply {
            text(latest, metadata(release))
            text(release.checksumUrl, "${hash(bytes)}  ${release.installerName}\n")
            bytes(release.installerUrl, bytes)
        }

    private fun temporaryDirectory(block: (Path) -> Unit) {
        val directory = Files.createTempDirectory("pomodoro-update-test")
        try { block(directory) }
        finally { directory.toFile().deleteRecursively() }
    }

    @Test fun stableVersionsUseNumericComponentsAndOnlyOfferNewerReleases() {
        val candidate = release("0.10.0")
        val service = GitHubUpdateService(transport(candidate))
        assertEquals(candidate, service.check("0.9.9"))
        assertNull(service.check("0.10.0"))
        assertNull(service.check("1.0.0"))
        assertNotNull(GitHubUpdateService(transport(release("12345678901234567890.0.0"))).check("9999999999999999999.0.0"))
    }

    @Test fun draftPrereleaseMalformedVersionAndInvalidJsonAreRejected() {
        for (invalid in listOf(
            metadata().replace("\"draft\":false", "\"draft\":true"),
            metadata().replace("\"prerelease\":false", "\"prerelease\":true"),
            metadata().replace("v0.2.0", "v0.2.0-beta.1"),
            metadata().replace("v0.2.0", "v00.2.0"),
            metadata().replace("\"draft\":false", "\"draft\":\"false\""),
            "{bad json",
        )) {
            val transport = transport().apply { text(latest, invalid) }
            assertFailsWith<IOException> { GitHubUpdateService(transport).check("0.1.0") }
        }
        assertFailsWith<IOException> { GitHubUpdateService(transport()).check("0.1") }
    }

    @Test fun releaseAssetsMustMatchExactRepositoryVersionNamesAndLimits() {
        for (candidate in listOf(
            release().copy(installerUrl = "https://example.com/setup.msi"),
            release().copy(installerUrl = release().installerUrl.replace("/v0.2.0/", "/v0.1.0/")),
            release().copy(releaseUrl = "$repository/../../other"),
            release().copy(installerName = "../setup.msi"),
            release().copy(installerSize = GitHubUpdateService.MAX_INSTALLER_BYTES + 1),
            release().copy(checksumUrl = release().checksumUrl + "?redirect=outside"),
        )) {
            assertFailsWith<IOException> { GitHubUpdateService(transport(candidate)).check("0.1.0") }
        }
        val missing = transport().apply { text(latest, metadata().replace("SHA256SUMS.txt", "OTHER.txt")) }
        assertFailsWith<IOException> { GitHubUpdateService(missing).check("0.1.0") }
    }

    @Test fun verifiedDownloadReportsProgressUsesUniqueDirectoryAndCanBeReverified() = temporaryDirectory { directory ->
        val release = release()
        val service = GitHubUpdateService(transport())
        val progress = mutableListOf<Pair<Long, Long>>()
        val path = service.download(release, directory, { received, total -> progress += received to total }, { false })
        assertEquals(release.installerName, path.fileName.toString())
        assertEquals(directory.toRealPath(), path.parent.parent)
        assertContentEquals(installerBytes, Files.readAllBytes(path))
        assertEquals(0L to release.installerSize, progress.first())
        assertEquals(release.installerSize to release.installerSize, progress.last())
        service.verifyBeforeInstall(path, release)
        val second = service.download(release, directory, { _, _ -> }, { false })
        assertNotEquals(path, second)
        service.verifyBeforeInstall(path, release)
        service.verifyBeforeInstall(second, release)
    }

    @Test fun modifiedInstallerAndModifiedSidecarCannotPassVerification() = temporaryDirectory { directory ->
        val release = release()
        val service = GitHubUpdateService(transport())
        val path = service.download(release, directory, { _, _ -> }, { false })
        val changed = installerBytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
        Files.write(path, changed)
        Files.writeString(path.parent.resolve("SHA256SUMS.txt"), "${hash(changed)}  ${release.installerName}\n")
        assertFailsWith<IOException> { service.verifyBeforeInstall(path, release) }
        assertFailsWith<IOException> { GitHubUpdateService(transport()).verifyBeforeInstall(path, release) }
        assertFailsWith<IOException> { service.verifyBeforeInstall(path, release("0.3.0")) }
    }

    @Test fun checksumMustNameTheExactInstallerOnlyOnce() = temporaryDirectory { directory ->
        val release = release()
        val correct = "${hash(installerBytes)}  ${release.installerName}\n"
        for (sums in listOf(
            "${hash(installerBytes)}  other.msi\n",
            "${hash(installerBytes)}  ../${release.installerName}\n",
            correct + correct,
            "not-a-hash  ${release.installerName}",
        )) {
            val transport = transport().apply { text(release.checksumUrl, sums) }
            assertFailsWith<IOException> { GitHubUpdateService(transport).download(release, directory, { _, _ -> }, { false }) }
            assertTrue(transport.requests.none { it == release.installerUrl })
        }
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @Test fun checksumMismatchAndIncompleteOrOversizedDownloadsAreDiscarded() = temporaryDirectory { directory ->
        val release = release()
        val damaged = installerBytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
        for (payload in listOf(damaged, installerBytes.dropLast(1).toByteArray(), installerBytes + 1.toByte())) {
            val transport = transport().apply { bytes(release.installerUrl, payload, length = null) }
            assertFailsWith<IOException> { GitHubUpdateService(transport).download(release, directory, { _, _ -> }, { false }) }
            assertEquals(0L, Files.list(directory).use { it.count() })
        }
    }

    @Test fun cancellationDuringDownloadDeletesPartialAndVerifiedFiles() = temporaryDirectory { directory ->
        val bytes = ByteArray(100_000) { (it % 127).toByte() }
        val release = release(size = bytes.size.toLong())
        var canceled = false
        val service = GitHubUpdateService(transport(release, bytes))
        assertFailsWith<CancellationException> {
            service.download(release, directory, { received, _ -> if (received > 0) canceled = true }, { canceled })
        }
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @Test fun cancellationBeforeDownloadDoesNotOpenANetworkConnection() = temporaryDirectory { directory ->
        val transport = transport()
        assertFailsWith<CancellationException> {
            GitHubUpdateService(transport).download(release(), directory, { _, _ -> }, { true })
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun redirectsOnlyReachApprovedHttpsReleaseCdnHosts() = temporaryDirectory { directory ->
        val release = release()
        val approved = "https://release-assets.githubusercontent.com/github-production-release-asset/file?signature=abc"
        val transport = transport().apply {
            routes[release.installerUrl] = { UpdateResponse(302, ByteArrayInputStream(byteArrayOf()), location = approved) }
            bytes(approved, installerBytes)
        }
        val service = GitHubUpdateService(transport)
        val path = service.download(release, directory, { _, _ -> }, { false })
        service.verifyBeforeInstall(path, release)
        assertTrue(approved in transport.requests)
        for (destination in listOf("http://release-assets.githubusercontent.com/file", "https://evil.example/file",
            "https://release-assets.githubusercontent.com.evil.example/file", "https://user@objects.githubusercontent.com/file",
            "https://github.com/other/repository/releases/download/v0.2.0/setup.msi")) {
            val rejected = transport().apply {
                routes[release.installerUrl] = { UpdateResponse(302, ByteArrayInputStream(byteArrayOf()), location = destination) }
            }
            assertFailsWith<IOException> { GitHubUpdateService(rejected).download(release, directory, { _, _ -> }, { false }) }
            assertTrue(destination !in rejected.requests)
        }
    }

    @Test fun oversizedMetadataChecksumAndIncorrectDeclaredInstallerSizeAreRejected() = temporaryDirectory { directory ->
        val metadataTooLarge = transport().apply {
            routes[latest] = { UpdateResponse(200, ByteArrayInputStream(byteArrayOf()), 1_024L * 1_024 + 1) }
        }
        assertFailsWith<IOException> { GitHubUpdateService(metadataTooLarge).check("0.1.0") }
        val checksumTooLarge = transport().apply { bytes(release().checksumUrl, ByteArray(16 * 1_024 + 1), null) }
        assertFailsWith<IOException> { GitHubUpdateService(checksumTooLarge).download(release(), directory, { _, _ -> }, { false }) }
        val incorrectSize = transport().apply { bytes(release().installerUrl, installerBytes, installerBytes.size + 1L) }
        assertFailsWith<IOException> { GitHubUpdateService(incorrectSize).download(release(), directory, { _, _ -> }, { false }) }
        assertEquals(0L, Files.list(directory).use { it.count() })
    }

    @Test fun networkErrorsAndRateLimitsProvideRecoverableFailures() {
        val limited = transport().apply { routes[latest] = { UpdateResponse(429, ByteArrayInputStream(byteArrayOf())) } }
        val error = assertFailsWith<IOException> { GitHubUpdateService(limited).check("0.1.0") }
        assertTrue(error.message!!.contains("Try again later"))
        val offline = GitHubUpdateService(UpdateTransport { throw IOException("Offline") })
        assertFailsWith<IOException> { offline.check("0.1.0") }
    }
}
