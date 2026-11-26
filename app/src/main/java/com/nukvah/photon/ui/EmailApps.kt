package com.nukvah.photon.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nukvah.photon.R

/** One installed app that can send email with photo attachments. */
data class AppChoice(val id: String, val label: String, val icon: ImageBitmap? = null)

/**
 * Photon's own list of email apps. Android's share sheet differs by phone maker:
 * some open a default email app the person never uses, some bury email apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailAppSheet(apps: List<AppChoice>, current: String?, onPick: (AppChoice) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PhotonColors.Surface,
    ) {
        EmailAppList(apps, current, onPick, Modifier.navigationBarsPadding())
    }
}

@Composable
fun EmailAppList(apps: List<AppChoice>, current: String?, onPick: (AppChoice) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
        Text(
            stringResource(R.string.email_app_title),
            style = PhotonType.Heading,
            color = PhotonColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.email_app_hint),
            style = PhotonType.Body,
            color = PhotonColors.Muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
        )
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            apps.forEach { app ->
                val selected = app.id == current
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (selected) PhotonColors.AccentSoft else PhotonColors.Background)
                        .clickable(role = Role.Button) { onPick(app) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (app.icon != null) {
                            Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                        } else {
                            Icon(PhotonIcons.Mail, contentDescription = null, tint = PhotonColors.Muted)
                        }
                    }
                    Text(app.label, style = PhotonType.BodyStrong, color = PhotonColors.Ink, modifier = Modifier.weight(1f))
                    if (selected) {
                        Icon(PhotonIcons.Check, contentDescription = null, tint = PhotonColors.Accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
