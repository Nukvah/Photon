package com.nukvah.photon.system

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import com.nukvah.photon.MainActivity
import com.nukvah.photon.pipeline.PhotoResult
import java.io.File

/** Photos another app shared to Photon, or nothing if this launch was not a share. */
fun Intent.sharedImageUris(): List<Uri> {
    val fromExtras = when (action) {
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java))
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        else -> return emptyList()
    }
    if (fromExtras.isNotEmpty()) return fromExtras.distinct()
    val clip = clipData ?: return emptyList()
    return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }.distinct()
}

/** Sends finished photos to other apps through FileProvider, read-only. `context` is the activity. */
class Sharing(private val context: Context) {
    private val authority = "${context.packageName}.files"

    /** Straight to the email app (the chooser lists only email apps when there are several). */
    fun email(results: List<PhotoResult>) {
        val send = sendIntent(results).apply { selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")) }
        try {
            context.startActivity(send)
        } catch (e: ActivityNotFoundException) {
            // No email app (or one that ignores the selector): offer every app instead.
            otherApps(results)
        }
    }

    /** The system share sheet, without Photon itself in it. */
    fun otherApps(results: List<PhotoResult>) {
        val chooser = Intent.createChooser(sendIntent(results), null).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf<Parcelable>(ComponentName(context, MainActivity::class.java)))
        }
        context.startActivity(chooser)
    }

    private fun sendIntent(results: List<PhotoResult>): Intent {
        val uris = results.map { FileProvider.getUriForFile(context, authority, File(it.file)) }
        return Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            // ClipData carries the read grant for every photo, not only the first.
            clipData = ClipData.newRawUri(null, uris[0]).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

/** Copies the photos into a folder the person picked; returns how many were saved. */
fun ContentResolver.saveToTree(tree: Uri, results: List<PhotoResult>): Int {
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    return results.count { result ->
        runCatching {
            // The folder's app renames on a clash, e.g. "IMG_2041 (1).jpg".
            val doc = DocumentsContract.createDocument(this, parent, "image/jpeg", result.name) ?: return@runCatching false
            openOutputStream(doc)?.use { out -> File(result.file).inputStream().use { it.copyTo(out) } } != null
        }.getOrDefault(false)
    }
}
