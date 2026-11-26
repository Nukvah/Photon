package com.nukvah.photon.pipeline

import com.nukvah.photon.core.PhotonException
import com.nukvah.photon.core.ProcessedPhoto
import com.nukvah.photon.core.ProgressListener
import com.nukvah.photon.core.SizePreset
import com.nukvah.photon.core.outputFileNames
import com.nukvah.photon.core.processPhoto
import com.nukvah.photon.core.processPixels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock

/** Where photos come from; Android's content resolver in the app, something simpler in tests. */
interface PhotoReader {
    /** The file's bytes, exactly as shared. */
    fun read(id: String): ByteArray

    /**
     * Upright sRGB pixels for formats the Rust core cannot read (AVIF, HEIC, GIF, BMP…)
     * and for very large photos; at most about 16 MP, shrunk while decoding when bigger.
     */
    fun decodePixels(bytes: ByteArray): Pixels

    /** Width × height from the file header, or null if it cannot be read cheaply. */
    fun pixelCount(bytes: ByteArray): Long?

    /** True when [decodePixels] shrinks big photos while decoding (cheap on memory). */
    val shrinksWhileDecoding: Boolean
}

/** RGBA, 4 bytes per pixel, not premultiplied, rows top to bottom. */
class Pixels(val rgba: ByteArray, val width: Int, val height: Int)

/** Formats the Rust core decodes itself, keeping its own colour and orientation handling. */
object CoreFormats {
    fun canRead(bytes: ByteArray): Boolean = isJpeg(bytes) || isPng(bytes) || isWebp(bytes)

    private fun isJpeg(b: ByteArray) = b.startsWith(0xFF, 0xD8, 0xFF)
    private fun isPng(b: ByteArray) = b.startsWith(0x89, 'P'.code, 'N'.code, 'G'.code)
    private fun isWebp(b: ByteArray) =
        b.startsWith('R'.code, 'I'.code, 'F'.code, 'F'.code) && b.size >= 12 &&
            String(b, 8, 4, Charsets.US_ASCII) == "WEBP"

    private fun ByteArray.startsWith(vararg prefix: Int) =
        size >= prefix.size && prefix.indices.all { this[it].toInt() and 0xFF == prefix[it] }
}

/**
 * The real pipeline: each photo goes through the Rust core on a background thread,
 * a few at a time, in input order. Finished JPEGs are written to a new folder from
 * [newBatchDir]. Cancelling the collector stops the Rust work at its next step.
 */
class RustPipeline(
    private val reader: PhotoReader,
    private val newBatchDir: () -> File,
    private val parallelism: Int = defaultParallelism(),
) : PhotoPipeline {

    override fun process(inputs: List<PhotoInput>, size: PhotoSize): Flow<PipelineEvent> = channelFlow {
        if (inputs.isEmpty()) return@channelFlow
        val dir = newBatchDir()
        val names = outputFileNames(inputs.map { it.name })
        val next = AtomicInteger(0)
        repeat(parallelism.coerceIn(1, inputs.size)) {
            launch(Dispatchers.Default) {
                while (true) {
                    val index = next.getAndIncrement()
                    if (index >= inputs.size) break
                    send(processOne(index, inputs[index], size, File(dir, names[index])))
                }
            }
        }
    }

    private suspend fun ProducerScope<PipelineEvent>.processOne(
        index: Int,
        input: PhotoInput,
        size: PhotoSize,
        out: File,
    ): PipelineEvent {
        val job = currentCoroutineContext().job
        val listener = object : ProgressListener {
            override fun onProgress(fraction: Float): Boolean {
                trySend(PipelineEvent.Progress(index, fraction))
                return job.isActive
            }
        }
        send(PipelineEvent.Progress(index, 0.05f))
        return try {
            val photo = encode(input, size.toPreset(), listener)
            out.writeBytes(photo.jpeg)
            PipelineEvent.Finished(
                index,
                PhotoResult(out.name, photo.width.toInt(), photo.height.toInt(), photo.jpeg.size.toLong(), out.absolutePath),
            )
        } catch (e: PhotonException.Cancelled) {
            throw CancellationException("cancelled")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Not a photo, revoked permission, full disk, out of memory: skip this photo, keep the batch.
            PipelineEvent.Failed(index, classify(e), e.message ?: e.javaClass.simpleName)
        }
    }

    private fun encode(input: PhotoInput, preset: SizePreset, listener: ProgressListener): ProcessedPhoto {
        val bytes = reader.read(input.id)
        val pixels = reader.pixelCount(bytes)
        // Big photos run alone, so two of them never need their peak memory at once.
        val large = pixels == null || pixels > LARGE_PIXELS
        val lock = if (large) memory.writeLock() else memory.readLock()
        lock.lock()
        try {
            val huge = pixels != null && pixels > HUGE_PIXELS && reader.shrinksWhileDecoding
            if (CoreFormats.canRead(bytes) && !huge) {
                try {
                    return processPhoto(bytes, preset, listener)
                } catch (e: PhotonException.Decode) {
                    // Unusual variant of a known format (CMYK JPEG, say): let Android try.
                }
            }
            val decoded = try {
                reader.decodePixels(bytes)
            } catch (e: OutOfMemoryError) {
                throw e
            } catch (e: Exception) {
                throw UnsupportedPhotoException(e)
            }
            return processPixels(decoded.rgba, decoded.width.toUInt(), decoded.height.toUInt(), preset, listener)
        } finally {
            lock.unlock()
        }
    }

    /** Readers: ordinary photos side by side. Writer: one big photo with the memory to itself. */
    private val memory = ReentrantReadWriteLock(true)

    companion object {
        /** Above this, a photo runs alone (a 24 MP photo needs about 75 MB just for its pixels). */
        const val LARGE_PIXELS = 24_000_000L

        /** Above this, Android decodes at a reduced size first (50 MP and 200 MP phone cameras). */
        const val HUGE_PIXELS = 40_000_000L

        /** Each photo needs one core and up to about 150 MB at its peak, so stay modest. */
        fun defaultParallelism(): Int = (Runtime.getRuntime().availableProcessors() / 3).coerceIn(1, 3)
    }
}

fun PhotoSize.toPreset(): SizePreset = when (this) {
    PhotoSize.SMALL -> SizePreset.SMALL
    PhotoSize.STANDARD -> SizePreset.STANDARD
    PhotoSize.SHARP -> SizePreset.SHARP
}
