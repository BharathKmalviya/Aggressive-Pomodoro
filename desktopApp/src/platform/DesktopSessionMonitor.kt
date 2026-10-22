package com.pomodoro.platform

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

internal enum class DesktopAvailability { AVAILABLE, UNAVAILABLE, UNKNOWN }

/** Sample on IO before ticking; audio also rechecks just before starting a clip. */
internal class DesktopSessionMonitor(
    private val probe: () -> DesktopAvailability = if (System.getProperty("os.name").startsWith("Windows", true))
        WindowsSessionProbe()::query else { { DesktopAvailability.AVAILABLE } },
    initial: DesktopAvailability = if (System.getProperty("os.name").startsWith("Windows", true))
        DesktopAvailability.UNKNOWN else DesktopAvailability.AVAILABLE,
) {
    @Volatile var availability: DesktopAvailability = initial
        private set

    fun refresh(): DesktopAvailability = read().also { availability = it }

    // A lock during resource decoding or device opening must invalidate that late playback too.
    fun canPlay(): Boolean = availability == DesktopAvailability.AVAILABLE && read() == DesktopAvailability.AVAILABLE

    private fun read(): DesktopAvailability = try { probe() }
        catch (_: Exception) { DesktopAvailability.UNKNOWN }
        catch (_: LinkageError) { DesktopAvailability.UNKNOWN }
}

internal interface WindowsSessionApi {
    fun isSessionActive(): Boolean?
    fun isInputDesktop(): Boolean?
}

internal class WindowsSessionProbe(private val api: WindowsSessionApi = NativeWindowsSessionApi()) {
    fun query(): DesktopAvailability {
        val connected = api.isSessionActive()
        val input = api.isInputDesktop()
        return when {
            connected == false || input == false -> DesktopAvailability.UNAVAILABLE
            connected == true && input == true -> DesktopAvailability.AVAILABLE
            else -> DesktopAvailability.UNKNOWN
        }
    }
}

internal interface SessionUser32 : StdCallLibrary {
    fun GetThreadDesktop(threadId: Int): Pointer?
    fun GetUserObjectInformationW(desktop: Pointer, index: Int, value: IntByReference,
        length: Int, needed: IntByReference): Boolean
}

internal interface SessionKernel32 : StdCallLibrary {
    fun GetCurrentThreadId(): Int
}

internal interface SessionWts32 : StdCallLibrary {
    fun WTSQuerySessionInformationW(server: Pointer?, session: Int, infoClass: Int,
        buffer: PointerByReference, bytes: IntByReference): Boolean
    fun WTSFreeMemory(buffer: Pointer)
}

/** Only queries this process's session/desktop. No hooks, elevation, window manipulation or polling child process. */
private class NativeWindowsSessionApi : WindowsSessionApi {
    private val user by lazy { Native.load("user32", SessionUser32::class.java) }
    private val kernel by lazy { Native.load("kernel32", SessionKernel32::class.java) }
    private val wts by lazy { Native.load("wtsapi32", SessionWts32::class.java) }

    override fun isSessionActive(): Boolean? {
        val buffer = PointerByReference()
        val bytes = IntByReference()
        // WTS_CURRENT_SERVER_HANDLE, WTS_CURRENT_SESSION, WTSConnectState.
        val success = wts.WTSQuerySessionInformationW(null, -1, 8, buffer, bytes)
        val pointer = buffer.value
        try {
            if (!success || pointer == null || bytes.value < 4) return null
            return pointer.getInt(0) == 0 // WTSActive; a disconnected RDP desktop can still report UOI_IO.
        } finally {
            if (pointer != null) wts.WTSFreeMemory(pointer)
        }
    }

    override fun isInputDesktop(): Boolean? {
        // GetThreadDesktop returns a borrowed handle: do not close it or switch this thread's desktop.
        val desktop = user.GetThreadDesktop(kernel.GetCurrentThreadId()) ?: return null
        val receivesInput = IntByReference()
        val needed = IntByReference()
        // UOI_IO is false while Winlogon, a secure UAC screen or a screen saver owns input.
        if (!user.GetUserObjectInformationW(desktop, 6, receivesInput, 4, needed)) return null
        return receivesInput.value != 0
    }
}
