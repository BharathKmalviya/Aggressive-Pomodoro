package com.pomodoro.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSessionMonitorTest {
    @Test fun onlyConnectedInputDesktopAllowsAlerts() {
        for (connected in listOf(true, false, null)) {
            for (input in listOf(true, false, null)) {
                val probe = WindowsSessionProbe(object : WindowsSessionApi {
                    override fun isSessionActive() = connected
                    override fun isInputDesktop() = input
                })
                val expected = when {
                    connected == false || input == false -> DesktopAvailability.UNAVAILABLE
                    connected == true && input == true -> DesktopAvailability.AVAILABLE
                    else -> DesktopAvailability.UNKNOWN
                }
                assertEquals(expected, probe.query(), "connected=$connected, input=$input")
            }
        }
    }

    @Test fun sessionProbeFailureSilencesAlertsAndRecoversOnNextSample() {
        var fail = true
        val monitor = DesktopSessionMonitor(probe = {
            if (fail) error("Windows query unavailable") else DesktopAvailability.AVAILABLE
        }, initial = DesktopAvailability.UNKNOWN)
        assertFalse(monitor.canPlay())
        assertEquals(DesktopAvailability.UNKNOWN, monitor.refresh())
        assertFalse(monitor.canPlay())
        fail = false
        assertEquals(DesktopAvailability.AVAILABLE, monitor.refresh())
        assertTrue(monitor.canPlay())
    }

    @Test fun nativeLibraryFailureIsRecoverableAndDoesNotEscapeIntoTimerLoop() {
        val monitor = DesktopSessionMonitor(probe = { throw UnsatisfiedLinkError("missing DLL") },
            initial = DesktopAvailability.AVAILABLE)
        assertFalse(monitor.canPlay())
        assertEquals(DesktopAvailability.UNKNOWN, monitor.refresh())
    }

    @Test fun audioRechecksLockAfterPollAndDoesNotResumeBeforeNextAvailableSample() {
        var state = DesktopAvailability.AVAILABLE
        val monitor = DesktopSessionMonitor(probe = { state }, initial = DesktopAvailability.UNKNOWN)
        assertEquals(DesktopAvailability.AVAILABLE, monitor.refresh())
        assertTrue(monitor.canPlay())
        state = DesktopAvailability.UNAVAILABLE
        assertFalse(monitor.canPlay()) // lock after the UI's last sample
        monitor.refresh()
        state = DesktopAvailability.AVAILABLE
        assertFalse(monitor.canPlay()) // UI has yet to reconcile the return
        monitor.refresh()
        assertTrue(monitor.canPlay())
    }
}
