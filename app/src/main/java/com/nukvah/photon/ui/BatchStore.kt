package com.nukvah.photon.ui

import com.nukvah.photon.pipeline.PhotoInput
import com.nukvah.photon.pipeline.PhotoResult
import com.nukvah.photon.pipeline.PhotoSize

/** What Photon remembers between launches: the size last used and the last finished batch. */
data class Remembered(val size: PhotoSize = PhotoSize.STANDARD, val lastBatch: UiState.Done? = null)

/** Keeps [Remembered] across launches. Calls are small and fast; the view model makes them on the main thread. */
interface BatchStore {
    fun load(): Remembered
    fun saveSize(size: PhotoSize)

    /** Remembers `batch` (or nothing) and deletes output files of every other batch. */
    fun saveBatch(batch: UiState.Done?)

    /** Remembers nothing; for previews and tests. */
    object None : BatchStore {
        override fun load() = Remembered()
        override fun saveSize(size: PhotoSize) = Unit
        override fun saveBatch(batch: UiState.Done?) = Unit
    }
}

/**
 * Plain-text form of a finished batch, one record per line, fields split by tabs.
 * Hand-written so it is easy to read in a bug report and needs no Android classes.
 */
object BatchCodec {
    private const val HEADER = "photon-batch\t1"

    fun encode(batch: UiState.Done): String = buildString {
        appendLine(HEADER)
        appendLine(line("size", batch.size.name))
        appendLine(line("failed", batch.failedCount))
        batch.inputs.forEach { appendLine(line("in", it.id, it.name, it.sizeBytes)) }
        batch.results.forEach { appendLine(line("out", it.name, it.width, it.height, it.sizeBytes, it.file)) }
    }

    /** Null for anything this version did not write; a damaged file must never crash the app. */
    fun decode(text: String): UiState.Done? = runCatching {
        val lines = text.lines().filter { it.isNotEmpty() }
        if (lines.firstOrNull() != HEADER) return null
        var size: PhotoSize? = null
        var failed = 0
        val inputs = mutableListOf<PhotoInput>()
        val results = mutableListOf<PhotoResult>()
        for (fields in lines.drop(1).map { it.split('\t') }) {
            when (fields[0]) {
                "size" -> size = PhotoSize.valueOf(fields[1])
                "failed" -> failed = fields[1].toInt()
                "in" -> inputs += PhotoInput(fields[1], fields[2], fields[3].toLong())
                "out" -> results += PhotoResult(fields[1], fields[2].toInt(), fields[3].toInt(), fields[4].toLong(), fields[5])
            }
        }
        if (size == null || results.isEmpty()) null else UiState.Done(size, inputs, results, failed)
    }.getOrNull()

    private fun line(vararg fields: Any): String =
        fields.joinToString("\t") { it.toString().replace('\t', ' ').replace('\n', ' ') }
}
