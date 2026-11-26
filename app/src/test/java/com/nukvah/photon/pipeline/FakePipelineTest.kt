package com.nukvah.photon.pipeline

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePipelineTest {
    @Test
    fun reportsProgressThenOneResultPerPhoto() = runTest {
        val events = FakePipeline(stepMillis = 10).process(SamplePhotos.batch, PhotoSize.STANDARD).toList()
        val finished = events.filterIsInstance<PipelineEvent.Finished>()
        assertEquals(SamplePhotos.batch.indices.toList(), finished.map { it.index })
        assertEquals(SamplePhotos.batch.size * 3, events.count { it is PipelineEvent.Progress })
        assertEquals("IMG_2041.jpg", finished.first().result.name)
    }

    @Test
    fun sameInputSameResult() {
        val input = SamplePhotos.batch[3]
        assertEquals(
            FakePipeline.fakeResult(input, PhotoSize.SHARP),
            FakePipeline.fakeResult(input, PhotoSize.SHARP),
        )
    }

    @Test
    fun resultsFollowPresetSizes() {
        for (size in PhotoSize.entries) {
            val r = FakePipeline.fakeResult(SamplePhotos.batch[0], size)
            val kb = r.sizeBytes / 1024.0
            assertTrue("$size: $kb KB", kb in size.typicalKb * 0.6..size.typicalKb * 1.4)
        }
        val standard = FakePipeline.fakeResult(SamplePhotos.batch[0], PhotoSize.STANDARD)
        assertEquals(setOf(1155, 866), setOf(standard.width, standard.height))
    }
}
