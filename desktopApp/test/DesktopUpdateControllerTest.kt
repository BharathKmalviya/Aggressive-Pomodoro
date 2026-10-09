package com.pomodoro.presentation

import com.pomodoro.domain.AppRelease
import com.pomodoro.domain.UpdateCheckResult
import com.pomodoro.domain.UpdateDownloadStage
import com.pomodoro.platform.UpdateService
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopUpdateControllerTest {
    @Test fun automaticDiscoveryOffersUpdateWithoutDownloadingOrInstalling() = runBlocking {
        val service = FakeService()
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        controller.checkAutomatically()!!.join()
        assertEquals(UpdateStatus.AVAILABLE, controller.state.status)
        assertEquals(release.version, controller.state.latestVersion)
        assertEquals(service.notes, controller.state.releaseNotes)
        assertEquals(1, service.checked)
        assertEquals(0, service.downloaded)
        assertEquals(0, service.verified)
    }

    @Test fun automaticOfflineFailureIsQuietAndDiscoveryCanRecover() = runBlocking {
        val service = FakeService().apply { failure = "You are offline." }
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        controller.checkAutomatically()!!.join()
        assertEquals(UpdateStatus.IDLE, controller.state.status)
        assertNull(controller.state.message)
        assertNull(controller.state.latestVersion)
        service.failure = null
        controller.checkAutomatically()!!.join()
        assertEquals(UpdateStatus.AVAILABLE, controller.state.status)
        assertEquals(2, service.checked)
    }

    @Test fun automaticChecksPreserveOffersActiveDownloadsErrorsAndReadyInstallers() = runBlocking {
        val service = object : FakeService() {
            override fun download(release: AppRelease, targetDirectory: Path,
                onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean): Path {
                failure?.let { throw IOException(it) }
                return super.download(release, targetDirectory, onProgress, isCancelled)
            }
        }
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        val first = controller.checkAutomatically()!!
        assertNull(controller.check(), "Manual and automatic checks must not overlap")
        first.join()
        val offered = controller.state
        assertNull(controller.checkAutomatically())
        assertEquals(offered, controller.state)
        service.failure = "Connection interrupted."
        val downloading = controller.download()!!
        assertNull(controller.checkAutomatically())
        downloading.join()
        val failed = controller.state
        assertEquals(UpdateStatus.ERROR, failed.status)
        assertNull(controller.checkAutomatically())
        assertEquals(failed, controller.state)
        service.failure = null
        controller.download()!!.join()
        val ready = controller.state
        assertEquals(UpdateStatus.READY, ready.status)
        assertNull(controller.checkAutomatically())
        assertEquals(ready, controller.state)
        assertEquals(1, service.checked)
    }

    private val release = AppRelease("0.3.0", "https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.3.0",
        "AggressivePomodoro-0.3.0.msi", "https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/download/v0.3.0/AggressivePomodoro-0.3.0.msi",
        100, "https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/download/v0.3.0/SHA256SUMS.txt")
    private val file = Path.of("verified-installer.msi")
    private open inner class FakeService : UpdateService {
        var checked = 0
        var downloaded = 0
        var verified = 0
        var latest: AppRelease? = release
        var notes = "New alarms and animations."
        var failure: String? = null
        override fun check(currentVersion: String): UpdateCheckResult {
            checked++
            failure?.let { throw IOException(it) }
            return UpdateCheckResult(latest?.version ?: currentVersion, notes, latest)
        }
        override fun download(release: AppRelease, targetDirectory: Path,
            onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean): Path {
            downloaded++
            onProgress(100, 100)
            return file
        }
        override fun verifyBeforeInstall(path: Path, release: AppRelease) {
            verified++
            failure?.let { throw IOException(it) }
        }
    }

    @Test fun currentAndOfflineChecksCanBeRetriedWithoutTouchingTimer() = runBlocking {
        val service = FakeService().apply { latest = null }
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        assertEquals(0, service.checked, "Constructing the updater must not contact the network")
        controller.check()!!.join()
        assertEquals(UpdateStatus.UP_TO_DATE, controller.state.status)
        assertEquals("0.2.0", controller.state.latestVersion)
        assertEquals(service.notes, controller.state.releaseNotes)
        assertNull(controller.download(), "Current-version notes must not enable a download")
        service.failure = "You are offline."
        controller.check()!!.join()
        assertEquals(UpdateStatus.ERROR, controller.state.status)
        assertEquals("You are offline.", controller.state.message)
        assertNull(controller.state.releaseNotes, "A failed fresh check must not retain stale notes")
        assertNull(controller.state.latestVersion)
        service.failure = null
        service.latest = release
        controller.check()!!.join()
        assertEquals(UpdateStatus.AVAILABLE, controller.state.status)
        assertEquals(service.notes, controller.state.releaseNotes)
    }

    @Test fun doubleCheckIsIgnoredWhileFirstRequestIsRunning() = runBlocking {
        val service = FakeService()
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        val first = controller.check()
        assertNull(controller.check())
        first!!.join()
        assertEquals(1, service.checked)
    }

    @Test fun downloadIsExplicitAndInstallationRequiresAReverifiedFile() = runBlocking {
        val service = FakeService()
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        var installed = false
        controller.install { _, _ -> installed = true }
        assertFalse(installed)
        controller.check()!!.join()
        assertEquals(0, service.downloaded)
        controller.download()!!.join()
        assertEquals(UpdateStatus.READY, controller.state.status)
        assertNull(controller.state.downloadStage)
        assertEquals(service.notes, controller.state.releaseNotes)
        assertFalse(installed)
        controller.install { path, candidate ->
            assertEquals(1, service.verified)
            assertEquals(file, path)
            assertEquals(release, candidate)
            installed = true
        }
        assertTrue(installed)
    }

    @Test fun cancelDownloadWaitsForCleanupAndAllowsRetry() = runBlocking {
        val started = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        var shouldBlock = true
        val service = object : FakeService() {
            override fun download(release: AppRelease, targetDirectory: Path,
                onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean): Path {
                if (shouldBlock) {
                    started.countDown()
                    check(proceed.await(3, TimeUnit.SECONDS))
                    if (isCancelled()) throw CancellationException()
                }
                return super.download(release, targetDirectory, onProgress, isCancelled)
            }
        }
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        controller.check()!!.join()
        val download = controller.download()!!
        // Let the runBlocking event loop launch the IO operation before waiting on its latch.
        kotlinx.coroutines.yield()
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS))
            controller.cancel()
            assertEquals(UpdateStatus.DOWNLOADING, controller.state.status)
            assertEquals(service.notes, controller.state.releaseNotes)
            assertNull(controller.download())
            proceed.countDown()
            download.join()
            assertEquals(UpdateStatus.AVAILABLE, controller.state.status)
            assertNull(controller.state.downloadStage)
            assertEquals(service.notes, controller.state.releaseNotes)
            shouldBlock = false
            controller.download()!!.join()
            assertEquals(UpdateStatus.READY, controller.state.status)
            assertEquals(service.notes, controller.state.releaseNotes)
        } finally { proceed.countDown() }
    }

    @Test fun changedInstallerAndLaunchFailuresDoNotExitAndCanBeRetried() = runBlocking {
        val service = FakeService()
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        controller.check()!!.join()
        controller.download()!!.join()
        service.failure = "Installer checksum changed."
        var launched = false
        controller.install { _, _ -> launched = true }
        assertFalse(launched)
        assertEquals(UpdateStatus.ERROR, controller.state.status)
        assertEquals(service.notes, controller.state.releaseNotes)
        service.failure = null
        controller.download()!!.join()
        controller.install { _, _ -> throw IOException("Windows Installer could not start.") }
        assertEquals(UpdateStatus.ERROR, controller.state.status)
        assertNotNull(controller.state.latestVersion)
        assertEquals(service.notes, controller.state.releaseNotes)
        controller.download()!!.join()
        controller.install { _, _ -> launched = true }
        assertTrue(launched)
    }

    @Test fun nonWindowsDoesNotRequestAnInstaller() = runBlocking {
        val service = FakeService()
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = false)
        assertNull(controller.checkAutomatically())
        assertEquals(UpdateStatus.IDLE, controller.state.status)
        assertNull(controller.check())
        assertEquals(0, service.checked)
        assertEquals(UpdateStatus.ERROR, controller.state.status)
    }

    @Test fun downloadFailureAndRetryPreserveNotesAndClearOldError() = runBlocking {
        var failDownload = true
        val service = object : FakeService() {
            override fun download(release: AppRelease, targetDirectory: Path,
                onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean): Path {
                if (failDownload) throw IOException("Connection interrupted.")
                return super.download(release, targetDirectory, onProgress, isCancelled)
            }
        }
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        controller.check()!!.join()
        controller.download()!!.join()
        assertEquals(UpdateStatus.ERROR, controller.state.status)
        assertNull(controller.state.downloadStage)
        assertEquals(service.notes, controller.state.releaseNotes)
        failDownload = false
        controller.download()!!.join()
        assertEquals(UpdateStatus.READY, controller.state.status)
        assertEquals(service.notes, controller.state.releaseNotes)
        assertNull(controller.state.message)
        service.notes = "Replacement release notes."
        val checking = controller.check()!!
        assertNull(controller.state.releaseNotes)
        checking.join()
        assertEquals(service.notes, controller.state.releaseNotes)
    }

    @Test fun connectionStageIsVisibleWhileBlockedAndLateCallbacksCannotReplaceReady() = runBlocking {
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val service = object : FakeService() {
            override fun download(release: AppRelease, targetDirectory: Path,
                onProgress: (Long, Long) -> Unit, isCancelled: () -> Boolean,
                onStage: (UpdateDownloadStage) -> Unit): Path {
                onStage(UpdateDownloadStage.CONNECTING)
                entered.countDown()
                check(proceed.await(5, TimeUnit.SECONDS))
                onStage(UpdateDownloadStage.VERIFYING)
                return super.download(release, targetDirectory, onProgress, isCancelled)
            }
        }
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = true)
        controller.check()!!.join()
        val downloading = controller.download()!!
        assertEquals(UpdateDownloadStage.FETCHING_CHECKSUM, controller.state.downloadStage)
        try {
            kotlinx.coroutines.yield()
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            kotlinx.coroutines.yield()
            assertEquals(UpdateDownloadStage.CONNECTING, controller.state.downloadStage)
            assertEquals(0L, controller.state.downloadedBytes)
            proceed.countDown()
            downloading.join()
            kotlinx.coroutines.yield()
            assertEquals(UpdateStatus.READY, controller.state.status)
            assertNull(controller.state.downloadStage)
        } finally { proceed.countDown() }
    }
}
