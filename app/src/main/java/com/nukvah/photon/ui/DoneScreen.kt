package com.nukvah.photon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nukvah.photon.R
import com.nukvah.photon.pipeline.PhotoResult
import com.nukvah.photon.pipeline.PhotoSize

@Composable
fun DoneScreen(
    state: UiState.Done,
    onClose: () -> Unit,
    onSizeChange: (PhotoSize) -> Unit,
    onShareEmail: () -> Unit,
    onSaveToFolder: () -> Unit,
    onOtherApps: () -> Unit,
    onShareOne: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().background(PhotonColors.Background).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(PhotonIcons.Close, stringResource(R.string.done_close), onClose)
            Spacer(Modifier.weight(1f))
            if (state.fitsAnyInbox) {
                Badge(stringResource(R.string.done_fits), PhotonColors.SuccessSoft, PhotonColors.SuccessText, PhotonIcons.Check)
            } else {
                Badge(stringResource(R.string.done_too_big), PhotonColors.WarningSoft, PhotonColors.Warning)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                pluralStringResource(R.plurals.done_title, state.results.size, state.results.size),
                style = PhotonType.Title,
                color = PhotonColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.done_total, formatSize(state.outputBytes), formatSize(state.inputBytes)),
                style = PhotonType.Body,
                color = PhotonColors.Muted,
            )
            if (state.failedCount > 0) {
                Text(
                    pluralStringResource(R.plurals.done_skipped, state.failedCount, state.failedCount),
                    style = PhotonType.Caption,
                    color = PhotonColors.Warning,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoSize.entries.forEach { size ->
                SizeChip(size.label(), selected = size == state.size, onClick = { onSizeChange(size) })
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(PhotonColors.Surface),
            contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            itemsIndexed(state.results) { index, result ->
                ResultRow(index, result, onShare = { onShareOne(index) })
                if (index < state.results.lastIndex) HorizontalDivider(thickness = 1.dp, color = PhotonColors.Divider)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton(stringResource(R.string.share_email), onShareEmail, icon = PhotonIcons.Mail, height = 60.dp, modifier = Modifier.fillMaxWidth())
            // With large text (the default on many phones) two buttons side by side no longer fit.
            val stacked = LocalDensity.current.fontScale > 1.15f
            if (stacked) {
                SecondaryButton(stringResource(R.string.save_to_folder), onSaveToFolder, PhotonIcons.Folder, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.other_apps), onOtherApps, PhotonIcons.OtherApps, Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(stringResource(R.string.save_to_folder), onSaveToFolder, PhotonIcons.Folder, Modifier.weight(1f))
                    SecondaryButton(stringResource(R.string.other_apps), onOtherApps, PhotonIcons.OtherApps, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ResultRow(index: Int, result: PhotoResult, onShare: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Thumb(index, Modifier.size(48.dp), key = result.file.ifEmpty { null })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(result.name, style = PhotonType.BodyStrong, color = PhotonColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildAnnotatedString {
                    append(formatDimensions(result.width, result.height))
                    append(" · ")
                    withStyle(SpanStyle(color = PhotonColors.Ink, fontWeight = FontWeight.SemiBold)) { append(formatSize(result.sizeBytes)) }
                },
                style = PhotonType.Caption,
                color = PhotonColors.Muted,
            )
        }
        RoundIconButton(PhotonIcons.Share, stringResource(R.string.done_share_one, result.name), onShare, tint = PhotonColors.InkSecondary, iconSize = 20.dp)
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit, icon: ImageVector, modifier: Modifier) {
    PillButton(label, onClick, icon = icon, filled = false, height = 52.dp, textStyle = PhotonType.ButtonSmall, modifier = modifier)
}
