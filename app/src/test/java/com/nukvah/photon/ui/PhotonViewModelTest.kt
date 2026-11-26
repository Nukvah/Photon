package com.nukvah.photon.ui

import com.nukvah.photon.pipeline.FakePipeline
import com.nukvah.photon.pipeline.PhotoSize
import com.nukvah.photon.pipeline.SamplePhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    private fun TestScope.newViewModel() =
        PhotonViewModel(FakePipeline(stepMillis = 100), clock = { testScheduler.currentTime })

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
    fun emptySelectionDoesNothing() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.start(emptyList())
        assertTrue(vm.state.value is UiState.Idle)
    }
}
