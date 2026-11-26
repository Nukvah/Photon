package com.nukvah.photon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nukvah.photon.R

/** One library bundled in the app, from assets/licenses/index.tsv (written by scripts/gen-licenses.py). */
data class Notice(val name: String, val version: String, val license: String, val file: String) {
    companion object {
        /** Parses index.tsv: name, version, licence, text file, separated by tabs. */
        fun parse(tsv: String): List<Notice> = tsv.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val f = line.split('\t')
                if (f.size == 4) Notice(f[0], f[1], f[2], f[3]) else null
            }
            .toList()
    }
}

/**
 * Settings > Open source licenses: every library in the app, and its licence text
 * when one is tapped. Back goes from a text to the list, then out.
 */
@Composable
fun LicensesScreen(
    notices: List<Notice>,
    loadText: (String) -> String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openIndex by rememberSaveable { mutableStateOf(-1) }
    val open = notices.getOrNull(openIndex)
    if (open != null) {
        BackHandler { openIndex = -1 }
        LicenseText(open, loadText, onBack = { openIndex = -1 }, modifier = modifier)
        return
    }

    LazyColumn(
        modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                RoundIconButton(PhotonIcons.Close, stringResource(R.string.back), onBack)
                Text(stringResource(R.string.licenses), style = PhotonType.Heading, color = PhotonColors.Ink, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.licenses_intro), style = PhotonType.Body, color = PhotonColors.Muted)
            }
        }
        items(notices.size) { i ->
            val n = notices[i]
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PhotonColors.Surface)
                    .clickable(role = Role.Button) { openIndex = i }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (n.version.isEmpty()) n.name else "${n.name} ${n.version}", style = PhotonType.BodyStrong, color = PhotonColors.Ink)
                    Text(n.license, style = PhotonType.Caption, color = PhotonColors.Muted)
                }
            }
        }
    }
}

@Composable
private fun LicenseText(notice: Notice, loadText: (String) -> String, onBack: () -> Unit, modifier: Modifier) {
    val text = remember(notice.file) { runCatching { loadText(notice.file) }.getOrDefault("") }
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RoundIconButton(PhotonIcons.Close, stringResource(R.string.back), onBack)
        Text(notice.name, style = PhotonType.Heading, color = PhotonColors.Ink, modifier = Modifier.semantics { heading() })
        Text(notice.license, style = PhotonType.CaptionStrong, color = PhotonColors.Muted)
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, color = PhotonColors.InkSecondary)
    }
}
