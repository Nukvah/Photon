package com.nukvah.photon.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class ProblemsTest {
    @Test
    fun sortsFailuresIntoWhatThePersonCanDo() {
        assertEquals(Problem.TOO_LARGE, classify(OutOfMemoryError()))
        assertEquals(Problem.UNSUPPORTED, classify(UnsupportedPhotoException(IOException("not a photo"))))
        assertEquals(Problem.UNREADABLE, classify(SecurityException("Permission Denial")))
        assertEquals(Problem.UNREADABLE, classify(FileNotFoundException("content://…")))
        assertEquals(Problem.NO_SPACE, classify(IOException("write failed: ENOSPC (No space left on device)")))
        // Android wraps the errno in a cause.
        assertEquals(Problem.NO_SPACE, classify(IOException("write failed", RuntimeException("ENOSPC"))))
        assertEquals(Problem.UNSUPPORTED, classify(IllegalStateException("odd")))
    }
}
