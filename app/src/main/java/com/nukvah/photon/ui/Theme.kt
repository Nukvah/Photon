package com.nukvah.photon.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nukvah.photon.R

/** Colours from the Photon app UI mockups. */
object PhotonColors {
    val Background = Color(0xFFF4F3EF)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF15171C)
    val InkSecondary = Color(0xFF3A3E47)
    val Muted = Color(0xFF555A64)
    val Faint = Color(0xFF6B6F78)
    val Border = Color(0xFFC9C6BC)
    val Track = Color(0xFFE6E4DD)
    val Divider = Color(0xFFEEECE6)
    val Accent = Color(0xFF2E46E6)
    val AccentDark = Color(0xFF1F33B8)
    val AccentSoft = Color(0xFFE7EAFD)
    val Success = Color(0xFF1F7A4D)
    val SuccessText = Color(0xFF17603C)
    val SuccessSoft = Color(0xFFDDF0E5)
    val Warning = Color(0xFF9A3412)
    val WarningSoft = Color(0xFFFBE7D9)

    /** Placeholder tile colours until real thumbnails arrive in Stage 4. */
    val Thumbs = listOf(
        Color(0xFFC9D3C0), Color(0xFFD8C8B8), Color(0xFFB9C7D6), Color(0xFFE0D2B4),
        Color(0xFFC7BFD9), Color(0xFFCFD9CF), Color(0xFFD9C1C1), Color(0xFFBFD0D8),
    )
}

/** Headings use Space Grotesk (bundled, OFL); body text uses the system sans serif. */
val DisplayFont = FontFamily(Font(R.font.space_grotesk_bold, FontWeight.Bold))

object PhotonType {
    val Title = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.01).em)
    val Heading = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em)
    val Brand = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.01).em)
    val Counter = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 40.sp, letterSpacing = (-0.02).em)
    val CounterSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 22.sp)
    val Button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val ButtonSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp)
    val BodyStrong = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val CaptionStrong = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val Tiny = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun PhotonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PhotonColors.Accent,
            onPrimary = Color.White,
            background = PhotonColors.Background,
            onBackground = PhotonColors.Ink,
            surface = PhotonColors.Surface,
            onSurface = PhotonColors.Ink,
            outline = PhotonColors.Border,
        ),
        typography = Typography(),
        content = content,
    )
}
