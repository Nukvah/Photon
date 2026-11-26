package com.nukvah.photon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nukvah.photon.R
import kotlin.math.roundToInt

@Composable
fun ProcessingScreen(state: UiState.Processing, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(PhotonColors.Background).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                pluralStringResource(R.plurals.processing_title, state.inputs.size, state.inputs.size),
                style = PhotonType.Heading,
                color = PhotonColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(state.size.label(), PhotonColors.AccentSoft, PhotonColors.AccentDark)
                Text(stringResource(R.string.size_each, state.size.typicalKb), fontSize = 14.sp, color = PhotonColors.Muted)
            }
        }

        OverallCard(state)

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            itemsIndexed(state.tiles) { index, tile -> TileView(index, tile) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(PhotonIcons.Lock, contentDescription = null, tint = PhotonColors.Muted, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.processing_private), style = PhotonType.Caption, color = PhotonColors.Muted)
        }
        PillButton(stringResource(R.string.cancel), onCancel, filled = false, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OverallCard(state: UiState.Processing) {
    val overallLabel = stringResource(R.string.processing_overall)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(PhotonColors.Surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            val ofTotal = stringResource(R.string.processing_of, state.tiles.size)
            Text(
                buildAnnotatedString {
                    append("${state.finishedCount} ")
                    withStyle(SpanStyle(fontSize = 22.sp, color = PhotonColors.Muted, fontWeight = FontWeight.SemiBold)) {
                        append(ofTotal)
                    }
                },
                style = PhotonType.Counter,
                color = PhotonColors.Ink,
                modifier = Modifier.weight(1f),
            )
            state.secondsLeft?.let {
                Text(stringResource(R.string.processing_seconds_left, it.coerceAtLeast(1)), fontSize = 14.sp, color = PhotonColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        val progress = state.overallProgress
        Box(
            Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(PhotonColors.Track)
                .semantics {
                    contentDescription = overallLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
        ) {
            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(PhotonColors.Accent))
        }
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.processing_in, formatSize(state.inputBytes)), fontSize = 14.sp, color = PhotonColors.InkSecondary, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.processing_out, formatSize(state.outputBytes)), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PhotonColors.InkSecondary)
        }
    }
}

@Composable
private fun TileView(index: Int, tile: Tile) {
    val waiting = tile is Tile.Waiting
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).alpha(if (waiting) 0.45f else 1f)) {
            Thumb(index, Modifier.fillMaxSize(), showIcon = true, corner = 14.dp)
            when (tile) {
                is Tile.Done -> Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(PhotonColors.Success),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PhotonIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                }
                is Tile.Working -> Box(
                    Modifier.align(Alignment.BottomCenter).padding(6.dp).fillMaxWidth().height(6.dp)
                        .clip(RoundedCornerShape(3.dp)).background(Color(0xCCFFFFFF)),
                ) {
                    Box(Modifier.fillMaxWidth(tile.progress).fillMaxHeight().background(PhotonColors.Accent))
                }
                else -> Unit
            }
        }
        val (label, color) = when (tile) {
            is Tile.Done -> formatSize(tile.result.sizeBytes) to PhotonColors.Success
            is Tile.Working -> "${(tile.progress * 100).roundToInt()}%" to PhotonColors.Ink
            is Tile.Failed -> stringResource(R.string.processing_failed) to PhotonColors.Warning
            Tile.Waiting -> stringResource(R.string.processing_waiting) to PhotonColors.Faint
        }
        Text(label, style = PhotonType.Tiny, color = color, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
