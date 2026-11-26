package com.nukvah.photon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nukvah.photon.R
import com.nukvah.photon.pipeline.PhotoSize

@Composable
fun IdleScreen(
    state: UiState.Idle,
    onSelectPhotos: () -> Unit,
    onSizeChange: (PhotoSize) -> Unit,
    onShareAgain: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Above 130 % text the drop zone cannot share the screen, so the page scrolls instead.
    val roomy = LocalDensity.current.fontScale <= 1.3f
    Column(
        modifier.fillMaxSize().background(PhotonColors.Background)
            .then(if (roomy) Modifier else Modifier.verticalScroll(rememberScrollState()))
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Logo()
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.app_name), style = PhotonType.Brand, color = PhotonColors.Ink, modifier = Modifier.weight(1f))
            RoundIconButton(PhotonIcons.Settings, stringResource(R.string.settings), onSettings)
        }

        DropZone(onSelectPhotos, if (roomy) Modifier.weight(1f) else Modifier)

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.size_label), style = PhotonType.CaptionStrong, color = PhotonColors.Muted)
            SizeSwitch(state.size, onSizeChange)
            Text(state.size.hint(), style = PhotonType.Caption, color = PhotonColors.Muted)
        }

        state.lastBatch?.let { last ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PhotonColors.Surface)
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    pluralStringResource(R.plurals.last_batch, last.results.size, last.results.size, formatSize(last.outputBytes)),
                    style = PhotonType.BodyStrong,
                    color = PhotonColors.Ink,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.height(44.dp).clip(RoundedCornerShape(50)).background(PhotonColors.AccentSoft)
                        .clickable(role = Role.Button, onClick = onShareAgain).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.share_again), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PhotonColors.Accent)
                }
            }
        }
    }
}

@Composable
private fun Logo() {
    Canvas(Modifier.size(28.dp)) {
        drawCircle(PhotonColors.Accent, radius = size.minDimension * 13f / 28f)
        drawCircle(Color.White, radius = size.minDimension * 5f / 28f)
    }
}

@Composable
private fun DropZone(onSelectPhotos: () -> Unit, modifier: Modifier = Modifier) {
    val borderColor = PhotonColors.Border
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(PhotonColors.Surface)
            .drawBehind {
                val inset = 1.dp.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius(27.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            }
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(PhotonColors.AccentSoft), contentAlignment = Alignment.Center) {
            Icon(PhotonIcons.Share, contentDescription = null, tint = PhotonColors.Accent, modifier = Modifier.size(40.dp))
        }
        Text(
            stringResource(R.string.idle_title),
            style = PhotonType.Heading,
            color = PhotonColors.Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.idle_body),
            style = PhotonType.Body,
            color = PhotonColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Step(stringResource(R.string.idle_step_share))
            Icon(PhotonIcons.Arrow, contentDescription = null, tint = PhotonColors.Faint, modifier = Modifier.size(14.dp))
            Step(stringResource(R.string.idle_step_photon))
            Icon(PhotonIcons.Arrow, contentDescription = null, tint = PhotonColors.Faint, modifier = Modifier.size(14.dp))
            Step(stringResource(R.string.idle_step_send))
        }
        PillButton(
            text = stringResource(R.string.select_photos),
            onClick = onSelectPhotos,
            icon = PhotonIcons.Plus,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun Step(text: String) {
    Text(
        text,
        style = PhotonType.CaptionStrong,
        color = PhotonColors.InkSecondary,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(PhotonColors.Background).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Three-way segmented control: Small / Standard / Sharp. */
@Composable
fun SizeSwitch(selected: PhotoSize, onSelect: (PhotoSize) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(PhotonColors.Track).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PhotoSize.entries.forEach { size ->
            val isSelected = size == selected
            Box(
                Modifier.weight(1f).height(44.dp)
                    .then(if (isSelected) Modifier.shadow(1.dp, RoundedCornerShape(12.dp)) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) PhotonColors.Surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(size) }
                    .semantics { this.selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    size.label(),
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) PhotonColors.Ink else PhotonColors.InkSecondary,
                )
            }
        }
    }
}
