package com.nukvah.photon.pipeline

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stand-in for the Rust pipeline until Stage 4: emits believable progress and
 * results with no image work, so the screens can be built and tested.
 * Output depends only on the inputs and size, never on the clock.
 */
class FakePipeline(private val stepMillis: Long = 70) : PhotoPipeline {

    override fun process(inputs: List<PhotoInput>, size: PhotoSize): Flow<PipelineEvent> = flow {
        inputs.forEachIndexed { index, input ->
            for (step in 1..STEPS) {
                delay(stepMillis)
                if (step < STEPS) emit(PipelineEvent.Progress(index, step.toFloat() / STEPS))
            }
            emit(PipelineEvent.Finished(index, fakeResult(input, size)))
        }
    }

    companion object {
        private const val STEPS = 4

        /** Same dimensions the Rust core picks for a 12 MP 4:3 photo; size varies ±40 % by name. */
        fun fakeResult(input: PhotoInput, size: PhotoSize): PhotoResult {
            val hash = input.name.fold(7) { h, c -> h * 31 + c.code } and 0x7fffffff
            val (long, short) = when (size) {
                PhotoSize.SMALL -> 816 to 612
                PhotoSize.STANDARD -> 1155 to 866
                PhotoSize.SHARP -> 1633 to 1225
            }
            val portrait = hash % 3 == 0
            val factor = 0.6 + (hash % 80) / 100.0
            return PhotoResult(
                name = input.name.substringBeforeLast('.') + ".jpg",
                width = if (portrait) short else long,
                height = if (portrait) long else short,
                sizeBytes = (size.typicalKb * 1024 * factor).toLong(),
            )
        }
    }
}

/** A typical batch of 8 phone photos (31.4 MB in total), for the demo flow and tests. */
object SamplePhotos {
    val batch: List<PhotoInput> = listOf(
        3_812_000L, 4_105_000L, 3_650_000L, 4_420_000L, 3_980_000L, 3_710_000L, 4_190_000L, 5_058_400L,
    ).mapIndexed { i, bytes ->
        val name = "IMG_${2041 + i * 2}.jpg"
        PhotoInput(id = "sample:$name", name = name, sizeBytes = bytes)
    }
}
