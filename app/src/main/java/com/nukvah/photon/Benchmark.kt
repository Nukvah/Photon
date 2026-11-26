package com.nukvah.photon

import java.util.Locale

/** One benchmark run: a preset-sized synthetic image at a given jpegli distance. */
data class BenchmarkCase(val name: String, val width: UInt, val height: UInt, val distance: Float)

val BenchmarkCases = listOf(
    BenchmarkCase("Small", 866u, 577u, 1.5f),
    BenchmarkCase("Standard", 1224u, 816u, 1.2f),
    BenchmarkCase("Sharp", 1732u, 1155u, 1.0f),
)

fun formatBenchmark(
    name: String,
    width: UInt,
    height: UInt,
    distance: Float,
    bytes: ULong,
    encodeMs: Double,
    fingerprint: String,
): String {
    val kb = bytes.toDouble() / 1024.0
    return String.format(
        Locale.ROOT,
        "%s %dx%d d=%.1f: %.1f KB in %.1f ms [%s]",
        name, width.toInt(), height.toInt(), distance, kb, encodeMs, fingerprint,
    )
}
