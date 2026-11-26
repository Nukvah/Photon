package com.nukvah.photon.ui

import com.nukvah.photon.pipeline.PhotoInput
import com.nukvah.photon.pipeline.PhotoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatchCodecTest {
    @Test
    fun roundTripsABatch() {
        val done = ScreenshotTest.doneBatch(PhotoSize.SHARP).let { d ->
            d.copy(
                inputs = d.inputs + PhotoInput("content://media/1", "Léto\t2024.heic", 0),
                results = d.results.mapIndexed { i, r -> r.copy(file = "/cache/batches/batch-1/$i.jpg") },
                failedCount = 1,
            )
        }
        val back = BatchCodec.decode(BatchCodec.encode(done))!!
        assertEquals(done.results, back.results)
        assertEquals(PhotoSize.SHARP, back.size)
        assertEquals(1, back.failedCount)
        // Tabs inside a name would split the record, so they become spaces.
        assertEquals("Léto 2024.heic", back.inputs.last().name)
    }

    @Test
    fun ignoresDamagedOrForeignText() {
        assertNull(BatchCodec.decode(""))
        assertNull(BatchCodec.decode("hello"))
        assertNull(BatchCodec.decode("photon-batch\t1\nsize\tHUGE\n"))
        assertNull(BatchCodec.decode("photon-batch\t1\nsize\tSMALL\nout\tbroken\n"))
        // A batch with nothing to share is not worth remembering.
        assertNull(BatchCodec.decode("photon-batch\t1\nsize\tSMALL\nfailed\t2\n"))
    }
}
