package com.nukvah.photon

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreStatusTest {
    @Test
    fun loadedSummaryNamesVersionAndArch() {
        val status = CoreStatus.Loaded("0.1.0", "aarch64", "android", "pong")
        assertEquals("photon-core 0.1.0 loaded (android, aarch64)", status.summary())
    }

    @Test
    fun failedSummaryGivesReason() {
        assertEquals("photon-core failed to load: missing .so", CoreStatus.Failed("missing .so").summary())
    }
}
