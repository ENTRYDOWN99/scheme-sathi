package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val NotoSansFontFamily = FontFamily(
    Font(R.font.noto_sans, FontWeight.Normal),
    Font(R.font.noto_sans, FontWeight.Medium),
    Font(R.font.noto_sans, FontWeight.Bold),
    Font(R.font.noto_sans_devanagari, FontWeight.Normal),
    Font(R.font.noto_sans_devanagari, FontWeight.Medium),
    Font(R.font.noto_sans_devanagari, FontWeight.Bold)
)

fun buildSchemeSaathiTypography(largeTextMode: Boolean = false): Typography {
    val baseBodySize = if (largeTextMode) 22.sp else 18.sp
    val baseTitleSize = if (largeTextMode) 26.sp else 22.sp
    val baseHeadlineSize = if (largeTextMode) 30.sp else 26.sp
    val baseSmallSize = if (largeTextMode) 17.sp else 14.sp

    return Typography(
        headlineLarge = TextStyle(
            fontFamily = NotoSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = baseHeadlineSize,
            lineHeight = (baseHeadlineSize.value + 8).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = NotoSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = baseTitleSize,
            lineHeight = (baseTitleSize.value + 6).sp
        ),
        titleLarge = TextStyle(
            fontFamily = NotoSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = baseTitleSize,
            lineHeight = (baseTitleSize.value + 6).sp
        ),
        titleMedium = TextStyle(
            fontFamily = NotoSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = baseBodySize,
            lineHeight = (baseBodySize.value + 6).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = NotoSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = baseBodySize,
            lineHeight = (baseBodySize.value + 8).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = NotoSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (baseBodySize.value - 2).sp,
            lineHeight = (baseBodySize.value + 4).sp
        ),
        labelLarge = TextStyle(
            fontFamily = NotoSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (baseBodySize.value - 2).sp,
            lineHeight = (baseBodySize.value + 4).sp
        ),
        bodySmall = TextStyle(
            fontFamily = NotoSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = baseSmallSize,
            lineHeight = (baseSmallSize.value + 4).sp
        )
    )
}

val Typography = buildSchemeSaathiTypography(largeTextMode = false)
