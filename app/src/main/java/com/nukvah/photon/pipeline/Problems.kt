package com.nukvah.photon.pipeline

import java.io.FileNotFoundException
import java.io.IOException

/** Neither the Rust core nor Android could make pixels out of the file. */
class UnsupportedPhotoException(cause: Throwable) : Exception(cause.message, cause)

/** Sorts a failure into what the person can understand and act on. */
fun classify(e: Throwable): Problem = when {
    e is OutOfMemoryError -> Problem.TOO_LARGE
    e is UnsupportedPhotoException -> Problem.UNSUPPORTED
    e is IOException && isNoSpace(e) -> Problem.NO_SPACE
    e is SecurityException || e is FileNotFoundException || e is IOException -> Problem.UNREADABLE
    else -> Problem.UNSUPPORTED
}

/** Android reports a full disk as ENOSPC somewhere in the message or its cause. */
private fun isNoSpace(e: Throwable): Boolean =
    generateSequence(e) { it.cause }.take(5).any { cause ->
        val m = cause.message.orEmpty()
        "ENOSPC" in m || "No space left" in m
    }
