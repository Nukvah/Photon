package com.nukvah.photon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nukvah.photon.R
import com.nukvah.photon.pipeline.PhotoSize

@Composable
fun PhotoSize.label(): String = stringResource(
    when (this) {
        PhotoSize.SMALL -> R.string.size_small
        PhotoSize.STANDARD -> R.string.size_standard
        PhotoSize.SHARP -> R.string.size_sharp
    },
)

@Composable
fun PhotoSize.hint(): String = stringResource(
    when (this) {
        PhotoSize.SMALL -> R.string.size_hint_small
        PhotoSize.STANDARD -> R.string.size_hint_standard
        PhotoSize.SHARP -> R.string.size_hint_sharp
    },
)

/** Filled, pill-shaped main action. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = true,
    height: Dp = 56.dp,
    textStyle: TextStyle = PhotonType.Button,
) {
    val shape = RoundedCornerShape(50)
    val base = modifier
        .heightIn(min = height)
        .clip(shape)
        .then(
            if (filled) {
                Modifier.background(PhotonColors.Accent)
            } else {
                Modifier.background(PhotonColors.Surface).border(BorderStroke(1.5.dp, PhotonColors.Border), shape)
            },
        )
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 6.dp)
    val color = if (filled) Color.White else PhotonColors.Ink
    Row(base, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (filled) 22.dp else 18.dp))
            Spacer(Modifier.width(if (filled) 10.dp else 8.dp))
        }
        Text(text, style = textStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round 44 dp icon button with a spoken label. */
@Composable
fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = PhotonColors.Ink, iconSize: Dp = 22.dp) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Small rounded label, e.g. "Standard" or "Fits any inbox". */
@Composable
fun Badge(text: String, background: Color, color: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = if (icon != null) 12.dp else 10.dp, vertical = if (icon != null) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = PhotonType.CaptionStrong, color = color)
    }
}

/** Selectable chip used for the size choice on the Done screen. */
@Composable
fun SizeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .height(36.dp)
            .clip(shape)
            .background(if (selected) PhotonColors.AccentSoft else Color.Transparent)
            .border(1.5.dp, if (selected) PhotonColors.Accent else PhotonColors.Border, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) PhotonColors.AccentDark else PhotonColors.InkSecondary,
        )
    }
}

/** Placeholder thumbnail: the photo's colour swatch with a picture icon. */
@Composable
fun Thumb(index: Int, modifier: Modifier = Modifier, showIcon: Boolean = false, corner: Dp = 10.dp, key: String? = null) {
    val thumbnails = LocalThumbnails.current
    val picture by produceState<ImageBitmap?>(null, key, thumbnails) {
        value = if (key != null && thumbnails != null) thumbnails.load(key) else null
    }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(PhotonColors.Thumbs[index % PhotonColors.Thumbs.size]),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = picture
        if (loaded != null) {
            Image(loaded, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else if (showIcon) {
            Icon(PhotonIcons.Photo, contentDescription = null, tint = Color(0x5915171C), modifier = Modifier.size(28.dp))
        }
    }
}

/** Loads small previews by key: an original's content URI or an output file path. */
fun interface Thumbnails {
    suspend fun load(key: String): ImageBitmap?
}

/** Null in previews and screenshot tests, which then show coloured placeholders. */
val LocalThumbnails = staticCompositionLocalOf<Thumbnails?> { null }
