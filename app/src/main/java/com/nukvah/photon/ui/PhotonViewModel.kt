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
import kotlinx.coroutines.launch

/** Drives Idle → Processing → Done. All screen state lives in [state]. */
class PhotonViewModel(
    private val pipeline: PhotoPipeline,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState>(UiState.Idle(PhotoSize.STANDARD))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    private var lastBatch: UiState.Done? = null

    /** Size switch on the Idle screen; used for the next batch. */
    fun selectSize(size: PhotoSize) {
        val current = _state.value
        if (current is UiState.Idle) _state.value = current.copy(size = size)
    }

    /** New photos arrived (share sheet or picker). */
    fun start(inputs: List<PhotoInput>) {
        if (inputs.isEmpty()) return
        run(inputs, _state.value.size)
    }

    /** Size chips on the Done screen: redo the same photos at the new size. */
    fun changeSize(size: PhotoSize) {
        val current = _state.value as? UiState.Done ?: return
        if (size != current.size) run(current.inputs, size)
    }

    /** Cancel while processing: stop at once and go back to Idle; nothing is kept. */
    fun cancel() {
        job?.cancel()
        job = null
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

    private fun run(inputs: List<PhotoInput>, size: PhotoSize) {
        job?.cancel()
        var processing = startProcessing(size, inputs)
        _state.value = processing
        val startedAt = clock()
        job = viewModelScope.launch {
            pipeline.process(inputs, size).collect { event ->
                processing = processing.apply(event, clock() - startedAt)
                _state.value = processing
            }
            val done = processing.toDone()
            lastBatch = done
            _state.value = done
        }
    }
}
