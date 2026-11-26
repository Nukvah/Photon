package com.nukvah.photon.system

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nukvah.photon.pipeline.PhotoInput
import com.nukvah.photon.pipeline.PhotoReader
import com.nukvah.photon.pipeline.Pixels
import com.nukvah.photon.ui.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer

/** Largest image handed to Rust as pixels; bigger ones are halved while decoding. */
private const val MAX_DECODED_PIXELS = 50_000_000L

/** Reads shared photos through the content resolver; decodes AVIF/HEIC with Android. */
class AndroidPhotoReader(private val resolver: ContentResolver) : PhotoReader {

    override fun read(id: String): ByteArray =
        resolver.openInputStream(Uri.parse(id))?.use { it.readBytes() } ?: throw FileNotFoundException(id)

    override fun decodePixels(bytes: ByteArray): Pixels {
        val bitmap = if (Build.VERSION.SDK_INT >= 28) decodeModern(bytes) else decodeLegacy(bytes)
        try {
            return bitmap.toRgba()
        } finally {
            bitmap.recycle()
        }
    }

    @RequiresApi(28)
    private fun decodeModern(bytes: ByteArray): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            // Software memory so the pixels can be read; sRGB because the core assumes it.
            // ImageDecoder already applies the EXIF orientation.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            val sample = sampleSize(info.size.width.toLong() * info.size.height)
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }

    private fun decodeLegacy(bytes: ByteArray): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize(bounds.outWidth.toLong() * bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("not a photo Android can read")
    }

    private fun sampleSize(pixels: Long): Int {
        var sample = 1
        while (pixels / (sample.toLong() * sample) > MAX_DECODED_PIXELS) sample *= 2
        return sample
    }
}

/** Row by row, so only one row of ints is extra; getPixels returns unpremultiplied sRGB. */
private fun Bitmap.toRgba(): Pixels {
    val out = ByteArray(width * height * 4)
    val row = IntArray(width)
    var o = 0
    for (y in 0 until height) {
        getPixels(row, 0, width, 0, y, width, 1)
        for (argb in row) {
            out[o++] = (argb shr 16).toByte()
            out[o++] = (argb shr 8).toByte()
            out[o++] = argb.toByte()
            out[o++] = (argb ushr 24).toByte()
        }
    }
    return Pixels(out, width, height)
}

/** Name and size of a shared photo, as the sharing app reports them. */
fun ContentResolver.describe(uri: Uri): PhotoInput {
    var name: String? = null
    var size = 0L
    runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val s = c.getColumnIndex(OpenableColumns.SIZE)
                if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
            }
        }
    }
    return PhotoInput(uri.toString(), name ?: uri.lastPathSegment ?: "photo", size)
}

/** Small previews for tiles and rows: content URIs (the originals) or output file paths. */
class AndroidThumbnails(private val resolver: ContentResolver) : Thumbnails {
    private val cache = LruCache<String, ImageBitmap>(120)

    override suspend fun load(key: String): ImageBitmap? {
        cache.get(key)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { runCatching { decode(key) }.getOrNull() } ?: return null
        return bitmap.asImageBitmap().also { cache.put(key, it) }
    }

    private fun decode(key: String): Bitmap? {
        val file = if (key.startsWith("/")) File(key) else null
        val uri = if (file != null) null else Uri.parse(key).takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
        if (file == null && uri == null) return null
        if (uri != null && Build.VERSION.SDK_INT >= 29) {
            return resolver.loadThumbnail(uri, Size(SIZE_PX, SIZE_PX), null)
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val source = if (file != null) ImageDecoder.createSource(file) else ImageDecoder.createSource(resolver, uri!!)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val shortSide = minOf(info.size.width, info.size.height)
                decoder.setTargetSampleSize(maxOf(1, Integer.highestOneBit(shortSide / SIZE_PX)))
            }
        }
        val open = { if (file != null) file.inputStream() else resolver.openInputStream(uri!!)!! }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val shortSide = minOf(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = maxOf(1, Integer.highestOneBit(shortSide / SIZE_PX)) }
        return open().use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        const val SIZE_PX = 256
    }
}
