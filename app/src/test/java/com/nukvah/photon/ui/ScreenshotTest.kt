package com.nukvah.photon.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nukvah.photon.pipeline.FakePipeline
import com.nukvah.photon.pipeline.PhotoSize
import com.nukvah.photon.pipeline.PipelineEvent
import com.nukvah.photon.pipeline.Problem
import com.nukvah.photon.pipeline.SamplePhotos
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders each screen at the mockups' size (390 × 844 dp) with sample data.
 * `./gradlew recordRoborazziDebug` writes the PNGs to app/src/test/screenshots/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenshotTest {

    @Test
    fun idle() = shot("1-idle") {
        IdleScreen(UiState.Idle(PhotoSize.STANDARD), {}, {}, {}, {})
    }

    @Test
    fun idleWithLastBatch() = shot("1-idle-last-batch") {
        IdleScreen(UiState.Idle(PhotoSize.STANDARD, lastBatch = doneBatch()), {}, {}, {}, {})
    }

    @Test
    fun processing() = shot("2-processing") {
        ProcessingScreen(processingBatch(), onCancel = {})
    }

    @Test
    fun done() = shot("3-done") {
        DoneScreen(doneBatch(), {}, {}, {}, {}, {}, {})
    }

    @Test
    fun doneOverTenMegabytes() = shot("3-done-over-10mb") {
        val big = doneBatch(PhotoSize.SHARP).let { d -> d.copy(results = d.results.map { it.copy(sizeBytes = 1_600_000) }) }
        DoneScreen(big, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun doneNothingReadable() = shot("3-done-nothing") {
        val none = doneBatch().copy(results = emptyList(), problems = listOf(Problem.UNSUPPORTED, Problem.UNSUPPORTED, Problem.UNREADABLE))
        DoneScreen(none, {}, {}, {}, {}, {}, {})
    }

    @Test
    fun doneWithSkipped() = shot("3-done-skipped") {
        val some = doneBatch().let { it.copy(results = it.results.take(6), problems = listOf(Problem.UNSUPPORTED, Problem.TOO_LARGE)) }
        DoneScreen(some, {}, {}, {}, {}, {}, {})
    }

    @Test
    @Config(fontScale = 1.3f)
    fun idleLargeText() = shot("1-idle-large-text") {
        IdleScreen(UiState.Idle(PhotoSize.STANDARD, lastBatch = doneBatch()), {}, {}, {}, {})
    }

    @Test
    @Config(fontScale = 2.0f)
    fun idleLargestText() = shot("1-idle-largest-text") {
        IdleScreen(UiState.Idle(PhotoSize.STANDARD, lastBatch = doneBatch()), {}, {}, {}, {})
    }

    @Test
    @Config(fontScale = 1.3f)
    fun processingLargeText() = shot("2-processing-large-text") {
        ProcessingScreen(processingBatch(), onCancel = {})
    }

    /** Many phones run with larger text; the bottom buttons stack instead of wrapping. */
    @Test
    @Config(fontScale = 1.3f)
    fun doneLargeText() = shot("3-done-large-text") {
        DoneScreen(doneBatch(), {}, {}, {}, {}, {}, {})
    }

    @Test
    fun emailAppPicker() = shot("4-email-app") {
        val apps = listOf(AppChoice("a", "Email"), AppChoice("b", "Larkmail"), AppChoice("c", "Postfly"))
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(PhotonColors.Surface)) {
            EmailAppList(apps, current = "b", onPick = {})
        }
    }

    @Test
    fun settings() = shot("5-settings") {
        SettingsScreen("Larkmail", {}, {}, androidx.compose.ui.Modifier.background(PhotonColors.Background), version = "1.0.0")
    }

    @Test
    fun licenses() = shot("5-licenses") {
        val notices = listOf(
            Notice("jpegli", "", "BSD-3-Clause", "a.txt"),
            Notice("moonforge-codec", "2.1.0", "MIT OR Apache-2.0", "b.txt"),
            Notice("starfall", "0.9.3", "MPL-2.0", "c.txt"),
        )
        LicensesScreen(notices, { "" }, {}, androidx.compose.ui.Modifier.background(PhotonColors.Background))
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/$name.png") { PhotonTheme(content) }
    }

    companion object {
        private val inputs = SamplePhotos.batch

        /** 5 of 8 finished, the 6th at 50 %, as in the mockup. */
        fun processingBatch(): UiState.Processing {
            var s = startProcessing(PhotoSize.STANDARD, inputs)
            for (i in 0 until 5) {
                s = s.apply(PipelineEvent.Finished(i, FakePipeline.fakeResult(inputs[i], PhotoSize.STANDARD)), 1_000L * (i + 1))
            }
            return s.apply(PipelineEvent.Progress(5, 0.5f), 5_400)
        }

        fun doneBatch(size: PhotoSize = PhotoSize.STANDARD): UiState.Done {
            var s = startProcessing(size, inputs)
            inputs.indices.forEach { i ->
                s = s.apply(PipelineEvent.Finished(i, FakePipeline.fakeResult(inputs[i], size)), 100L * (i + 1))
            }
            return s.toDone()
        }
    }
}
