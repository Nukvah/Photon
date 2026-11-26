package com.nukvah.photon.ui

import com.nukvah.photon.pipeline.FakePipeline
import com.nukvah.photon.pipeline.PhotoPipeline
import com.nukvah.photon.pipeline.PhotoSize
import com.nukvah.photon.pipeline.PipelineEvent
import com.nukvah.photon.pipeline.Problem
import com.nukvah.photon.pipeline.SamplePhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotonViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newViewModel(store: BatchStore = BatchStore.None, pipeline: PhotoPipeline = FakePipeline(stepMillis = 100)) =
        PhotonViewModel(pipeline, store, clock = { testScheduler.currentTime })

    /** Remembers in memory, like FileBatchStore does on disk. */
    private class MemoryStore(var remembered: Remembered = Remembered()) : BatchStore {
        override fun load() = remembered
        override fun saveSize(size: PhotoSize) {
            remembered = remembered.copy(size = size)
        }
        override fun saveBatch(batch: UiState.Done?) {
            remembered = remembered.copy(lastBatch = batch)
        }
    }

    @Test
    fun runsBatchToDoneAndRemembersIt() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.selectSize(PhotoSize.SMALL)
        vm.start(SamplePhotos.batch)
        assertTrue(vm.state.value is UiState.Processing)

        advanceTimeBy(1_000)
        val mid = vm.state.value as UiState.Processing
        assertEquals(2, mid.finishedCount)
        assertTrue(mid.secondsLeft != null)

        advanceUntilIdle()
        val done = vm.state.value as UiState.Done
        assertEquals(SamplePhotos.batch.size, done.results.size)
        // 8 photos × 4 steps × 100 ms, all on the test clock.
        assertEquals(3_200L, done.elapsedMillis)
        assertEquals(PhotoSize.SMALL, done.size)

        vm.close()
        val idle = vm.state.value as UiState.Idle
        assertEquals(done, idle.lastBatch)
        assertEquals(PhotoSize.SMALL, idle.size)

        vm.reopenLastBatch()
        assertEquals(done, vm.state.value)
    }

    @Test
    fun cancelStopsWorkAndKeepsNothing() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.start(SamplePhotos.batch)
        advanceTimeBy(500)
        vm.cancel()
        advanceUntilIdle()
        val idle = vm.state.value as UiState.Idle
        assertEquals(null, idle.lastBatch)
    }

    @Test
    fun changingSizeOnDoneRedoesTheBatch() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.start(SamplePhotos.batch.take(2))
        advanceUntilIdle()
        vm.changeSize(PhotoSize.SHARP)
        assertEquals(PhotoSize.SHARP, (vm.state.value as UiState.Processing).size)
        advanceUntilIdle()
        val done = vm.state.value as UiState.Done
        assertEquals(PhotoSize.SHARP, done.size)
        assertEquals(1633, maxOf(done.results[0].width, done.results[0].height))
    }

    @Test
    fun remembersSizeAndLastBatchAcrossLaunches() = runTest(dispatcher) {
        val store = MemoryStore()
        val first = newViewModel(store)
        first.selectSize(PhotoSize.SHARP)
        first.start(SamplePhotos.batch.take(3))
        advanceUntilIdle()
        val done = first.state.value as UiState.Done

        val second = newViewModel(store)
        assertEquals(UiState.Idle(PhotoSize.SHARP, done), second.state.value)
    }

    @Test
    fun cancelForgetsThePartialBatchButKeepsTheLastOne() = runTest(dispatcher) {
        val store = MemoryStore()
        val vm = newViewModel(store)
        vm.start(SamplePhotos.batch.take(2))
        advanceUntilIdle()
        val done = vm.state.value as UiState.Done
        vm.close()
        vm.start(SamplePhotos.batch)
        advanceTimeBy(500)
        vm.cancel()
        assertEquals(done, store.remembered.lastBatch)
        assertEquals(UiState.Idle(PhotoSize.STANDARD, done), vm.state.value)
    }

    @Test
    fun redoThatReadsNothingKeepsThePreviousResult() = runTest(dispatcher) {
        val store = MemoryStore()
        val good = newViewModel(store)
        good.start(SamplePhotos.batch.take(2))
        advanceUntilIdle()
        val done = good.state.value as UiState.Done

        // After a restart the shared photos can no longer be read.
        val unreadable = PhotoPipeline { inputs, _ ->
            flowOf(*inputs.indices.map { PipelineEvent.Failed(it, Problem.UNREADABLE) }.toTypedArray())
        }
        val vm = newViewModel(store, unreadable)
        vm.reopenLastBatch()
        vm.changeSize(PhotoSize.SMALL)
        advanceUntilIdle()
        assertEquals(done, vm.state.value)
        assertEquals(done, store.remembered.lastBatch)
    }

    @Test
    fun emptySelectionDoesNothing() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.start(emptyList())
        assertTrue(vm.state.value is UiState.Idle)
    }
}
