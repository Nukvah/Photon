package com.nukvah.photon.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeTest {
    @Test
    fun parsesIndexAndSkipsBrokenLines() {
        val tsv = "jpegli\t\tBSD-3-Clause\t1a2b.txt\nstarfall\t0.9.3\tMIT OR Apache-2.0\t3c4d.txt\nbroken line\n\n"
        assertEquals(
            listOf(
                Notice("jpegli", "", "BSD-3-Clause", "1a2b.txt"),
                Notice("starfall", "0.9.3", "MIT OR Apache-2.0", "3c4d.txt"),
            ),
            Notice.parse(tsv),
        )
    }
}
