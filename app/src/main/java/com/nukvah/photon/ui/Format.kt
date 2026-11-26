package com.nukvah.photon.ui

import java.util.Locale
import kotlin.math.roundToLong

/** "184 KB" below 1 MB, else "1.5 MB" (binary units, as phones and mail apps show them). */
fun formatSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
    val kb = (bytes / 1024.0).roundToLong()
    return if (kb < 1024) {
        String.format(locale, "%d KB", if (bytes > 0) kb.coerceAtLeast(1) else 0L)
    } else {
        String.format(locale, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

fun formatDimensions(width: Int, height: Int): String = "$width \u00D7 $height"
