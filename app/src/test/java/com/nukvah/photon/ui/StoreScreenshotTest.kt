package com.nukvah.photon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nukvah.photon.pipeline.PhotoSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Google Play phone screenshots: 1200 × 2400 px (Play allows at most 2:1). Thumbnails are
 * painted landscapes, so no real photo is needed. Written to src/test/screenshots/store/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xxhdpi")
class StoreScreenshotTest {

    @Test
    fun idle() = shot("1-share-to-photon") {
        IdleScreen(UiState.Idle(PhotoSize.STANDARD, lastBatch = ScreenshotTest.doneBatch()), {}, {}, {}, {})
    }

    @Test
    fun processing() = shot("2-shrinking") {
        ProcessingScreen(ScreenshotTest.processingBatch(), onCancel = {})
    }

    @Test
    fun done() = shot("3-ready-to-send") {
        DoneScreen(ScreenshotTest.doneBatch(), {}, {}, {}, {}, {}, {})
    }

    @Test
    fun emailApp() = shot("4-your-email-app") {
        Box(Modifier.fillMaxSize().background(PhotonColors.Background)) {
            EmailAppList(listOf(AppChoice("a", "Email"), AppChoice("b", "Larkmail"), AppChoice("c", "Postfly")), current = "b", onPick = {})
        }
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        captureRoboImage("src/test/screenshots/store/$name.png") {
            PhotonTheme {
                CompositionLocalProvider(LocalThumbnails provides PaintedThumbnails) {
                    Box(Modifier.fillMaxSize().background(PhotonColors.Background)) { content() }
                }
            }
        }
    }

    /** A sky, a sun and two hills, in a different palette for each key. */
    private object PaintedThumbnails : Thumbnails {
        private val palettes = listOf(
            listOf(0xFF9CC7E8, 0xFFF6E3B4, 0xFF6E9E6A, 0xFF4C7A50),
            listOf(0xFFF2B58C, 0xFFFBE6C8, 0xFF8C6A8E, 0xFF5E4A6E),
            listOf(0xFFB7D8E8, 0xFFFFFFFF, 0xFF8FB4C9, 0xFF5D869E),
            listOf(0xFFE8C9A0, 0xFFFFF3D6, 0xFFB98B5A, 0xFF8A6238),
            listOf(0xFFA8C8F0, 0xFFFFF0B0, 0xFF7FB07A, 0xFF3F6E4A),
            listOf(0xFFD7C2E8, 0xFFFFE4F0, 0xFF9C88B8, 0xFF6C5A8A),
        ).map { p -> p.map { Color(it) } }

        override suspend fun load(key: String): ImageBitmap {
            val (sky, sun, far, near) = palettes[Math.floorMod(key.hashCode(), palettes.size)]
            val w = 240f
            val h = 240f
            val image = ImageBitmap(w.toInt(), h.toInt())
            val canvas = Canvas(image)
            val paint = Paint()
            paint.color = sky
            canvas.drawRect(0f, 0f, w, h, paint)
            paint.color = sun
            canvas.drawCircle(Offset(w * 0.7f, h * 0.32f), w * 0.11f, paint)
            paint.color = far
            canvas.drawPath(hill(w, h, 0.55f, 0.35f), paint)
            paint.color = near
            canvas.drawPath(hill(w, h, 0.72f, 0.75f), paint)
            return image
        }

        private fun hill(w: Float, h: Float, top: Float, peak: Float) = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * (top + 0.12f))
            quadraticTo(w * peak, h * (top - 0.18f), w, h * (top + 0.08f))
            lineTo(w, h)
            close()
        }
    }
}
