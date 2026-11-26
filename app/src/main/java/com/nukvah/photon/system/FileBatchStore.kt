package com.nukvah.photon.system

import android.content.Context
import com.nukvah.photon.pipeline.PhotoSize
import com.nukvah.photon.ui.BatchCodec
import com.nukvah.photon.ui.BatchStore
import com.nukvah.photon.ui.Remembered
import com.nukvah.photon.ui.UiState
import java.io.File
import java.nio.file.Files

/**
 * Output JPEGs live in `cache/batches/<one folder per batch>/`, which FileProvider shares.
 * Only the remembered batch is kept; Android may also clear the cache, so loading
 * drops results whose files are gone.
 */
class FileBatchStore(context: Context) : BatchStore {
    private val prefs = context.getSharedPreferences("photon", Context.MODE_PRIVATE)
    private val batchFile = File(context.noBackupFilesDir, "last-batch.tsv")
    val outputRoot = File(context.cacheDir, OUTPUT_DIR)

    /** A new, empty folder for one batch's outputs. */
    fun newBatchDir(): File {
        outputRoot.mkdirs()
        return Files.createTempDirectory(outputRoot.toPath(), "batch-").toFile()
    }

    override fun load(): Remembered {
        val size = prefs.getString(KEY_SIZE, null)
            ?.let { name -> PhotoSize.entries.firstOrNull { it.name == name } } ?: PhotoSize.STANDARD
        val batch = runCatching { BatchCodec.decode(batchFile.readText()) }.getOrNull()
        val present = batch?.copy(results = batch.results.filter { File(it.file).isFile })
        return Remembered(size, present?.takeIf { it.results.isNotEmpty() })
    }

    override fun saveSize(size: PhotoSize) {
        prefs.edit().putString(KEY_SIZE, size.name).apply()
    }

    override fun saveBatch(batch: UiState.Done?) {
        if (batch == null) batchFile.delete() else batchFile.writeText(BatchCodec.encode(batch))
        val keep = batch?.results?.mapNotNull { File(it.file).parentFile }?.toSet().orEmpty()
        outputRoot.listFiles()?.filter { it !in keep }?.forEach { it.deleteRecursively() }
    }

    companion object {
        /** Must match `res/xml/file_paths.xml`. */
        const val OUTPUT_DIR = "batches"
        private const val KEY_SIZE = "size"
    }
}
