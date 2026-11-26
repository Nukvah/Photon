package com.nukvah.photon.ui

import com.nukvah.photon.pipeline.FakePipeline
import com.nukvah.photon.pipeline.PhotoSize
import com.nukvah.photon.pipeline.PipelineEvent
import com.nukvah.photon.pipeline.SamplePhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class UiStateTest {
    private val inputs = SamplePhotos.batch.take(4)
    private fun result(i: Int) = FakePipeline.fakeResult(inputs[i], PhotoSize.STANDARD)

    @Test
    fun progressCountsThePhotoInWork() {
        var s = startProcessing(PhotoSize.STANDARD, inputs)
        s = s.apply(PipelineEvent.Finished(0, result(0)), 1000)
        s = s.apply(PipelineEvent.Progress(1, 0.5f), 1500)
        assertEquals(1, s.finishedCount)
        assertEquals(1.5f / 4, s.overallProgress, 1e-6f)
        assertEquals(result(0).sizeBytes, s.outputBytes)
        // 1.5 s for 37.5 % → 2.5 s left.
        assertEquals(3, s.secondsLeft)
    }

    @Test
    fun ignoresEventsForUnknownPhotos() {
        val s = startProcessing(PhotoSize.STANDARD, inputs)
        assertEquals(s, s.apply(PipelineEvent.Progress(9, 0.5f), 100))
    }

    @Test
    fun doneKeepsInputOrderAndCountsFailures() {
        var s = startProcessing(PhotoSize.SMALL, inputs)
        s = s.apply(PipelineEvent.Finished(2, result(2)), 10)
        s = s.apply(PipelineEvent.Failed(1, "not a photo"), 20)
        s = s.apply(PipelineEvent.Finished(0, result(0)), 30)
        s = s.apply(PipelineEvent.Finished(3, result(3)), 40)
        val done = s.toDone()
        assertEquals(listOf(result(0), result(2), result(3)), done.results)
        assertEquals(1, done.failedCount)
        assertEquals(PhotoSize.SMALL, done.size)
    }

    @Test
    fun estimateNeedsProgress() {
        assertNull(estimateSecondsLeft(0f, 1000))
        assertNull(estimateSecondsLeft(0.5f, 0))
        assertEquals(0, estimateSecondsLeft(1f, 1000))
        assertEquals(1, estimateSecondsLeft(0.9f, 1000))
    }

    @Test
    fun inboxLimitIsTenMegabytes() {
        val small = UiState.Done(PhotoSize.STANDARD, inputs, listOf(result(0).copy(sizeBytes = INBOX_SAFE_BYTES)), 0)
        val big = small.copy(results = listOf(result(0).copy(sizeBytes = INBOX_SAFE_BYTES + 1)))
        assertTrue(small.fitsAnyInbox)
        assertFalse(big.fitsAnyInbox)
    }

    @Test
    fun formatsSizes() {
        assertEquals("0 KB", formatSize(0, Locale.ROOT))
        assertEquals("1 KB", formatSize(10, Locale.ROOT))
        assertEquals("184 KB", formatSize(188_416, Locale.ROOT))
        assertEquals("1023 KB", formatSize(1_048_000, Locale.ROOT))
        assertEquals("1.0 MB", formatSize(1_048_576, Locale.ROOT))
        assertEquals("31.4 MB", formatSize(SamplePhotos.batch.sumOf { it.sizeBytes }, Locale.ROOT))
        assertEquals("1,5 MB", formatSize(1_572_864, Locale.forLanguageTag("sk")))
        assertEquals("1224 × 816", formatDimensions(1224, 816))
    }
}
