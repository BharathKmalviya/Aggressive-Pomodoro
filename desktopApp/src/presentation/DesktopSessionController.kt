package com.pomodoro.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pomodoro.data.SnapshotStore
import com.pomodoro.domain.Completion
import com.pomodoro.domain.ProductCommand
import com.pomodoro.domain.ProductEngine
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionStatus
import com.pomodoro.domain.TaskCommand
import com.pomodoro.domain.TimeMark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.swing.SwingUtilities

class DesktopSessionController(
    private val store: SnapshotStore,
    saved: ProductState,
    private val clock: () -> TimeMark,
    private val localDate: () -> String,
    private val onCompletion: (Completion, Boolean) -> Unit,
    scope: CoroutineScope,
    initialDesktopAvailable: Boolean = true,
) {
    var desktopAvailable = initialDesktopAvailable
        private set
    var now by mutableStateOf(clock())
        private set
    var product by mutableStateOf(applyDesktopPolicy(ProductEngine.recover(saved, now, localDate())))
        private set
    var persistenceWarning by mutableStateOf<String?>(null)
        private set
    val state get() = product.session
    private var lastCheckpointMs = now.monotonicMs
    private var lastAlertMs: Long? = null
    private var closing = false
    private var closed = false
    private val closeMutex = Mutex()
    private data class SaveRequest(val snapshot: ProductState, val completion: CompletableDeferred<Unit>? = null)
    private val writes = Channel<SaveRequest>(Channel.CONFLATED)
    private val writer = scope.launch(Dispatchers.IO) {
        for (request in writes) {
            try {
                store.save(request.snapshot)
                SwingUtilities.invokeLater { persistenceWarning = null }
                request.completion?.complete(Unit)
            }
            catch (error: Exception) {
                System.err.println("Session save failed: ${error.message}")
                SwingUtilities.invokeLater { persistenceWarning = "Changes could not be saved. Check storage access before closing." }
                request.completion?.completeExceptionally(error)
            }
        }
    }

    init {
        if (product != saved) writes.trySend(SaveRequest(product))
        val recoveredCompletion = product.session.pending.firstOrNull { event ->
            saved.session.pending.none { it.phaseId == event.phaseId }
        }
        (recoveredCompletion ?: product.session.pending.firstOrNull())?.let(::alert)
    }

    fun tick() = dispatch(ProductCommand.Session(SessionCommand.Tick))

    /** Reconcile while silent, then deliver at most one retained event when the desktop returns. */
    fun updateDesktopAvailability(available: Boolean): Boolean {
        if (closed || closing || available == desktopAvailable) return false
        desktopAvailable = false
        tick()
        desktopAvailable = available
        if (available) tick()
        if (!available || state.pending.isEmpty()) return false
        alert(state.pending.first())
        return true
    }

    fun dispatchSession(command: SessionCommand) = dispatch(ProductCommand.Session(command))
    fun dispatchTask(command: TaskCommand) = dispatch(ProductCommand.Task(command))

    private fun dispatch(command: ProductCommand) {
        if (closed || closing) return
        now = clock()
        val before = product
        val updated = applyDesktopPolicy(ProductEngine.reduce(before, command, now, localDate()))
        if (updated != before) {
            product = updated
            val important = command != ProductCommand.Session(SessionCommand.Tick) ||
                updated.session.phaseId != before.session.phaseId ||
                updated.session.status != before.session.status || updated.session.pending != before.session.pending
            if (important || now.monotonicMs - lastCheckpointMs >= 15_000L) {
                writes.trySend(SaveRequest(updated))
                lastCheckpointMs = now.monotonicMs
            }
        }
        val newCompletions = updated.session.pending.filter { event ->
            before.session.pending.none { it.phaseId == event.phaseId }
        }
        when {
            updated.session.pending.isEmpty() -> lastAlertMs = null
            newCompletions.isNotEmpty() -> newCompletions.forEach(::alert)
            before.session.pending != updated.session.pending ||
                before.session.settings.aggressiveAlertsEnabled != updated.session.settings.aggressiveAlertsEnabled ->
                lastAlertMs = now.monotonicMs
            updated.session.settings.aggressiveAlertsEnabled &&
                now.monotonicMs - (lastAlertMs ?: now.monotonicMs) >= 10_000L ->
                alert(updated.session.pending.first())
        }
    }

    private fun applyDesktopPolicy(snapshot: ProductState): ProductState {
        val session = snapshot.session
        val command = when {
            !desktopAvailable && session.settings.pauseWhenLocked && session.status == SessionStatus.RUNNING ->
                SessionCommand.PauseForLock
            (desktopAvailable || !session.settings.pauseWhenLocked) && session.pausedForLock ->
                SessionCommand.ResumeAfterLock
            else -> return snapshot
        }
        return ProductEngine.reduce(snapshot, ProductCommand.Session(command), now, localDate())
    }

    private fun alert(completion: Completion) {
        // Each attempt resets the interval, even if delivery fails. Never replay missed intervals.
        lastAlertMs = now.monotonicMs
        if (!desktopAvailable) return
        try {
            onCompletion(completion, product.session.settings.soundEnabled)
        } catch (error: Exception) {
            System.err.println("Completion alert unavailable: ${error.message}")
        }
    }

    suspend fun close(beforeClose: suspend () -> Unit = {}) {
        closeMutex.withLock {
            if (closed) return
            closing = true
            try {
                val saved = CompletableDeferred<Unit>()
                writes.send(SaveRequest(product, saved))
                // Dispatch is frozen, so nothing can replace this final conflated request.
                // The final save follows any in-flight write. Keep the writer alive on failure
                // so dismissing the exit error and continuing to use the app remains safe.
                saved.await()
                // Installation preparation/launch may fail. Keep the writer alive until it succeeds.
                beforeClose()
                writes.close()
                writer.join()
                closed = true
            } finally {
                closing = false
            }
        }
    }
}
