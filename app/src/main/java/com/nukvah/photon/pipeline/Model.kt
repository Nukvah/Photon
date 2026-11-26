package com.nukvah.photon.pipeline

import kotlinx.coroutines.flow.Flow

/** Output size choice. Typical sizes were measured on real 12 MP phone photos (tech spec, Stage 2). */
enum class PhotoSize(val typicalKb: Int) {
    SMALL(80),
    STANDARD(150),
    SHARP(250),
}

/** One photo handed to Photon, before processing. */
data class PhotoInput(
    /** Stable id, e.g. the content URI. */
    val id: String,
    /** Original file name, used to name the output. */
    val name: String,
    val sizeBytes: Long,
)

/** One finished, email-safe JPEG. */
data class PhotoResult(
    val name: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    /** Absolute path of the JPEG in the app's cache; empty for sample data. */
    val file: String = "",
)

/** What a running batch reports; `index` points into the input list. */
sealed interface PipelineEvent {
    val index: Int

    /** `fraction` of this photo's work done, 0 to 1. */
    data class Progress(override val index: Int, val fraction: Float) : PipelineEvent
    data class Finished(override val index: Int, val result: PhotoResult) : PipelineEvent
    data class Failed(override val index: Int, val reason: String) : PipelineEvent
}

/**
 * Turns photos into email-safe JPEGs. The flow completes when every photo has
 * finished or failed; cancelling the collector stops the work.
 */
fun interface PhotoPipeline {
    fun process(inputs: List<PhotoInput>, size: PhotoSize): Flow<PipelineEvent>
}
