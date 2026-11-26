package com.nukvah.photon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nukvah.photon.pipeline.PhotoInput
import com.nukvah.photon.pipeline.PhotoPipeline
import com.nukvah.photon.pipeline.PhotoSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Drives Idle → Processing → Done. All screen state lives in [state]. */
class PhotonViewModel(
    private val pipeline: PhotoPipeline,
    private val store: BatchStore = BatchStore.None,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val remembered = store.load()
    private var lastBatch: UiState.Done? = remembered.lastBatch

    private val _state = MutableStateFlow<UiState>(UiState.Idle(remembered.size, lastBatch))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Size switch on the Idle screen; used for the next batch and remembered. */
    fun selectSize(size: PhotoSize) {
        val current = _state.value
        if (current is UiState.Idle) {
            _state.value = current.copy(size = size)
            store.saveSize(size)
        }
    }

    /** New photos arrived (share sheet or picker). Replaces whatever is on screen. */
    fun start(inputs: List<PhotoInput>) {
        if (inputs.isEmpty()) return
        run(inputs, _state.value.size, fallback = null)
    }

    /** Size chips on the Done screen: redo the same photos at the new size. */
    fun changeSize(size: PhotoSize) {
        val current = _state.value as? UiState.Done ?: return
        if (size == current.size) return
        store.saveSize(size)
        run(current.inputs, size, fallback = current)
    }

    /** Cancel while processing: stop at once and go back to Idle; nothing is kept. */
    fun cancel() {
        job?.cancel()
        job = null
        store.saveBatch(lastBatch)
        toIdle()
    }

    /** Close on the Done screen. */
    fun close() = toIdle()

    /** "Share again" on the Idle screen reopens the last finished batch. */
    fun reopenLastBatch() {
        lastBatch?.let { _state.value = it }
    }

    private fun toIdle() {
        _state.value = UiState.Idle(_state.value.size, lastBatch)
    }

    /**
     * `fallback` is shown again if the redo produced nothing, for example when the
     * original photos can no longer be read after the app restarted.
     */
    private fun run(inputs: List<PhotoInput>, size: PhotoSize, fallback: UiState.Done?) {
        job?.cancel()
        var processing = startProcessing(size, inputs)
        _state.value = processing
        val startedAt = clock()
        job = viewModelScope.launch {
            // A failure outside any one photo (disk full, say) ends the batch with what finished.
            pipeline.process(inputs, size).catch { }.collect { event ->
                processing = processing.apply(event, clock() - startedAt)
                _state.value = processing
            }
            val done = processing.toDone(elapsedMillis = clock() - startedAt)
            val shown = if (done.results.isEmpty() && fallback != null) fallback else done
            if (shown.results.isNotEmpty()) {
                lastBatch = shown
                store.saveBatch(shown)
            }
            _state.value = shown
        }
    }
}
