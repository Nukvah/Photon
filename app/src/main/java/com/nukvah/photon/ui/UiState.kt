package com.nukvah.photon.ui

import com.nukvah.photon.pipeline.PhotoInput
import com.nukvah.photon.pipeline.PhotoResult
import com.nukvah.photon.pipeline.PhotoSize
import com.nukvah.photon.pipeline.PipelineEvent
import com.nukvah.photon.pipeline.Problem
import kotlin.math.ceil

/** Above this, some mail servers reject the message (the large providers allow 20 to 25 MB). */
const val INBOX_SAFE_BYTES: Long = 10L * 1024 * 1024

/** Everything the three screens show. Pure data, so screens and logic test on the JVM. */
sealed interface UiState {
    val size: PhotoSize

    data class Idle(
        override val size: PhotoSize,
        val lastBatch: Done? = null,
    ) : UiState

    data class Processing(
        override val size: PhotoSize,
        val inputs: List<PhotoInput>,
        val tiles: List<Tile>,
        /** Estimated seconds until done; null until the first progress arrives. */
        val secondsLeft: Int? = null,
    ) : UiState {
        val inputBytes: Long get() = inputs.sumOf { it.sizeBytes }
        val finishedCount: Int get() = tiles.count { it is Tile.Done || it is Tile.Failed }
        val outputBytes: Long get() = tiles.sumOf { (it as? Tile.Done)?.result?.sizeBytes ?: 0L }

        /** 0 to 1 across the whole batch, counting the photo in progress. */
        val overallProgress: Float
            get() = if (tiles.isEmpty()) 0f else tiles.sumOf { it.progress.toDouble() }.toFloat() / tiles.size
    }

    data class Done(
        override val size: PhotoSize,
        val inputs: List<PhotoInput>,
        val results: List<PhotoResult>,
        /** One entry per skipped photo. */
        val problems: List<Problem> = emptyList(),
        /** How long the batch took; shown on the developer screen for speed checks. */
        val elapsedMillis: Long = 0,
    ) : UiState {
        val failedCount: Int get() = problems.size
        val inputBytes: Long get() = inputs.sumOf { it.sizeBytes }
        val outputBytes: Long get() = results.sumOf { it.sizeBytes }
        val fitsAnyInbox: Boolean get() = outputBytes <= INBOX_SAFE_BYTES
    }
}

/** One photo's tile on the Processing screen. */
sealed interface Tile {
    val progress: Float

    data object Waiting : Tile {
        override val progress = 0f
    }

    data class Working(override val progress: Float) : Tile
    data class Done(val result: PhotoResult) : Tile {
        override val progress = 1f
    }

    data class Failed(val problem: Problem) : Tile {
        override val progress = 1f
    }
}

fun startProcessing(size: PhotoSize, inputs: List<PhotoInput>) =
    UiState.Processing(size, inputs, List(inputs.size) { Tile.Waiting })

/** Applies one pipeline event; `elapsedMillis` is time since the batch started. */
fun UiState.Processing.apply(event: PipelineEvent, elapsedMillis: Long): UiState.Processing {
    if (event.index !in tiles.indices) return this
    val tile = when (event) {
        is PipelineEvent.Progress -> Tile.Working(event.fraction.coerceIn(0f, 1f))
        is PipelineEvent.Finished -> Tile.Done(event.result)
        is PipelineEvent.Failed -> Tile.Failed(event.problem)
    }
    val next = copy(tiles = tiles.toMutableList().also { it[event.index] = tile })
    return next.copy(secondsLeft = estimateSecondsLeft(next.overallProgress, elapsedMillis))
}

/** Linear estimate from progress so far; rounds up so it never says 0 while work remains. */
fun estimateSecondsLeft(progress: Float, elapsedMillis: Long): Int? {
    if (progress <= 0f || elapsedMillis <= 0) return null
    if (progress >= 1f) return 0
    val remainingMillis = elapsedMillis * (1f - progress) / progress
    return ceil(remainingMillis / 1000f).toInt()
}

/** The Done state once every tile has finished or failed, in input order. */
fun UiState.Processing.toDone(elapsedMillis: Long = 0): UiState.Done = UiState.Done(
    size = size,
    inputs = inputs,
    results = tiles.mapNotNull { (it as? Tile.Done)?.result },
    problems = tiles.mapNotNull { (it as? Tile.Failed)?.problem },
    elapsedMillis = elapsedMillis,
)
