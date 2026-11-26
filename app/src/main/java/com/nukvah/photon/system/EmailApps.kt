package com.nukvah.photon.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.nukvah.photon.ui.AppChoice

/**
 * Email apps installed on this device, and the one the person picked.
 * An email app is one that opens `mailto:` links and accepts several photos.
 * Visibility of these apps on Android 11+ comes from `<queries>` in the manifest.
 */
class EmailApps(private val context: Context) {
    private val prefs = context.getSharedPreferences("photon", Context.MODE_PRIVATE)

    fun installed(): List<AppChoice> {
        val pm = context.packageManager
        val mailto = pm.queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
        return mailto.map { it.activityInfo.packageName }.distinct()
            .filter { pkg ->
                val photos = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg").setPackage(pkg)
                pm.queryIntentActivities(photos, 0).isNotEmpty()
            }
            .mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    val icon = runCatching { pm.getApplicationIcon(info).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
                    AppChoice(pkg, pm.getApplicationLabel(info).toString(), icon)
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Package of the chosen app; null until the person picks one. */
    var chosen: String?
        get() = prefs.getString(KEY, null)
        set(value) = prefs.edit().putString(KEY, value).apply()

    private companion object {
        const val KEY = "email_app"
        const val ICON_PX = 120
    }
}
