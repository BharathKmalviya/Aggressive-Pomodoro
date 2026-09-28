package com.pomodoro.presentation

import com.pomodoro.domain.AppRelease
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
    private val release = AppRelease("0.3.0", "https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/tag/v0.3.0",
        "AggressivePomodoro-0.3.0.msi", "https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/download/v0.3.0/AggressivePomodoro-0.3.0.msi",
        100, "https://github.com/BharathKmalviya/Aggressive-Pomodoro/releases/download/v0.3.0/SHA256SUMS.txt")
    private val file = Path.of("verified-installer.msi")
    private open inner class FakeService : UpdateService {
        var checked = 0
        var downloaded = 0
        var verified = 0
        var latest: AppRelease? = release
        var failure: String? = null
        override fun check(currentVersion: String): AppRelease? {
            checked++
            failure?.let { throw IOException(it) }
            return latest
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
        service.failure = "You are offline."
        controller.check()!!.join()
        assertEquals(UpdateStatus.ERROR, controller.state.status)
        assertEquals("You are offline.", controller.state.message)
        service.failure = null
        service.latest = release
        controller.check()!!.join()
        assertEquals(UpdateStatus.AVAILABLE, controller.state.status)
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
            assertNull(controller.download())
            proceed.countDown()
            download.join()
            assertEquals(UpdateStatus.AVAILABLE, controller.state.status)
            shouldBlock = false
            controller.download()!!.join()
            assertEquals(UpdateStatus.READY, controller.state.status)
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
        service.failure = null
        controller.download()!!.join()
        controller.install { _, _ -> throw IOException("Windows Installer could not start.") }
        assertEquals(UpdateStatus.ERROR, controller.state.status)
        assertNotNull(controller.state.latestVersion)
        controller.download()!!.join()
        controller.install { _, _ -> launched = true }
        assertTrue(launched)
    }

    @Test fun nonWindowsDoesNotRequestAnInstaller() = runBlocking {
        val service = FakeService()
        val controller = DesktopUpdateController("0.2.0", Path.of("updates"), service, this, windows = false)
        assertNull(controller.check())
        assertEquals(0, service.checked)
        assertEquals(UpdateStatus.ERROR, controller.state.status)
    }
}
