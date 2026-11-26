package com.nukvah.photon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nukvah.photon.R

/**
 * Settings: the email app, the privacy policy and licences, a donate link when `onDonate` is set,
 * then developer tools passed in as `developer` (debug builds only).
 */
@Composable
fun SettingsScreen(
    emailApp: String?,
    onChangeEmailApp: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPrivacyPolicy: () -> Unit = {},
    onLicenses: () -> Unit = {},
    onDonate: (() -> Unit)? = null,
    version: String = "",
    developer: @Composable () -> Unit = {},
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoundIconButton(PhotonIcons.Close, stringResource(R.string.back), onBack)
        Text(stringResource(R.string.settings), style = PhotonType.Heading, color = PhotonColors.Ink, modifier = Modifier.semantics { heading() })

        SettingRow(stringResource(R.string.email_app_setting), emailApp ?: stringResource(R.string.email_app_not_chosen), stringResource(R.string.change), onChangeEmailApp)
        SettingRow(stringResource(R.string.privacy_policy), stringResource(R.string.privacy_summary), null, onPrivacyPolicy)
        SettingRow(stringResource(R.string.licenses), null, null, onLicenses)
        if (onDonate != null) {
            SettingRow(stringResource(R.string.donate), stringResource(R.string.donate_summary), null, onDonate)
        }
        if (version.isNotEmpty()) {
            Text(stringResource(R.string.version, version), style = PhotonType.Caption, color = PhotonColors.Faint)
        }

        developer()
    }
}

@Composable
private fun SettingRow(title: String, detail: String?, action: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PhotonColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = PhotonType.BodyStrong, color = PhotonColors.Ink)
            if (detail != null) Text(detail, style = PhotonType.Caption, color = PhotonColors.Muted)
        }
        if (action != null) Text(action, style = PhotonType.ButtonSmall, color = PhotonColors.Accent)
    }
}
