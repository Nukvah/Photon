package com.nukvah.photon

/** What the Stage 0 screen shows about the Rust core. */
sealed interface CoreStatus {
    data class Loaded(val version: String, val arch: String, val os: String, val ping: String) : CoreStatus
    data class Failed(val reason: String) : CoreStatus
}

/** One-line summary for the screen; kept pure so it can be unit tested on the JVM. */
fun CoreStatus.summary(): String = when (this) {
    is CoreStatus.Loaded -> "photon-core $version loaded ($os, $arch)"
    is CoreStatus.Failed -> "photon-core failed to load: $reason"
}
