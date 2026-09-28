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
import com.pomodoro.domain.TaskCommand
import com.pomodoro.domain.TimeMark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.SwingUtilities

class DesktopSessionController(
    private val store: SnapshotStore,
    private val saved: ProductState,
    private val clock: () -> TimeMark,
    private val localDate: () -> String,
    private val onCompletion: (Completion, Boolean) -> Unit,
    scope: CoroutineScope,
) {
    var product by mutableStateOf(ProductEngine.recover(saved, clock(), localDate()))
        private set
    var persistenceWarning by mutableStateOf<String?>(null)
        private set
    val state get() = product.session
    var now by mutableStateOf(clock())
        private set
    private var lastCheckpointWallMs = now.wallMs
    private val writes = Channel<ProductState>(Channel.CONFLATED)
    private val writer = scope.launch(Dispatchers.IO) {
        for (snapshot in writes) {
            try {
                store.save(snapshot)
                SwingUtilities.invokeLater { persistenceWarning = null }
            }
            catch (error: Exception) {
                System.err.println("Session save failed: ${error.message}")
                SwingUtilities.invokeLater { persistenceWarning = "Changes could not be saved. Check storage access before closing." }
            }
        }
    }

    init {
        if (product != saved) writes.trySend(product)
        product.session.pending.filter { event -> saved.session.pending.none { it.phaseId == event.phaseId } }
            .forEach { onCompletion(it, product.session.settings.soundEnabled) }
    }

    fun tick() {
        now = clock()
        dispatch(ProductCommand.Session(SessionCommand.Tick))
    }

    fun dispatchSession(command: SessionCommand) = dispatch(ProductCommand.Session(command))
    fun dispatchTask(command: TaskCommand) = dispatch(ProductCommand.Task(command))

    private fun dispatch(command: ProductCommand) {
        val before = product
        val updated = ProductEngine.reduce(before, command, now, localDate())
        if (updated == before) return
        product = updated
        updated.session.pending.filter { event -> before.session.pending.none { it.phaseId == event.phaseId } }
            .forEach { onCompletion(it, updated.session.settings.soundEnabled) }
        val important = command != ProductCommand.Session(SessionCommand.Tick) ||
            updated.session.phaseId != before.session.phaseId ||
            updated.session.status != before.session.status || updated.session.pending != before.session.pending
        if (important || now.wallMs - lastCheckpointWallMs >= 15_000L) {
            writes.trySend(updated)
            lastCheckpointWallMs = now.wallMs
        }
    }

    suspend fun close() {
        writes.close()
        writer.join()
        withContext(Dispatchers.IO) { store.save(product) }
    }
}
