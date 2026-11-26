package com.nukvah.photon.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * A column where child `fillIndex` takes the space the others leave, but never less
 * than its own content needs. When everything does not fit (short phones, large text),
 * the column scrolls instead of cutting off the bottom of that child.
 */
@Composable
fun FillOrScroll(fillIndex: Int, spacing: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val viewport = constraints.maxHeight
        Layout(content, Modifier.verticalScroll(rememberScrollState())) { measurables, outer ->
            val gap = spacing.roundToPx()
            val width = outer.maxWidth
            val loose = Constraints(minWidth = 0, maxWidth = width)
            val others = measurables.mapIndexed { i, m -> if (i == fillIndex) null else m.measure(loose) }
            val used = others.sumOf { it?.height ?: 0 } + gap * (measurables.size - 1).coerceAtLeast(0)
            val filler = measurables.getOrNull(fillIndex)?.let { m ->
                val needs = m.minIntrinsicHeight(width)
                val h = maxOf(needs, viewport - used)
                m.measure(Constraints(minWidth = 0, maxWidth = width, minHeight = h, maxHeight = h))
            }
            val placeables = others.mapIndexed { i, p -> if (i == fillIndex) filler else p }
            val total = maxOf(viewport, used + (filler?.height ?: 0))
            layout(width, total) {
                var y = 0
                placeables.forEach { p ->
                    if (p == null) return@forEach
                    p.placeRelative(0, y)
                    y += p.height + gap
                }
            }
        }
    }
}
