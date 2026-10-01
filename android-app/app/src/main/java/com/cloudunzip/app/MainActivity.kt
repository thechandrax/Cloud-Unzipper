package com.cloudunzip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.cloudunzip.app.ui.UnzipScreen

// 🏛️ Global Cambria / Serif Typography for the entire application
val CambriaTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Serif),
    displayMedium = TextStyle(fontFamily = FontFamily.Serif),
    displaySmall = TextStyle(fontFamily = FontFamily.Serif),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif),
    titleSmall = TextStyle(fontFamily = FontFamily.Serif),
    bodyLarge = TextStyle(fontFamily = FontFamily.Serif),
    bodyMedium = TextStyle(fontFamily = FontFamily.Serif),
    bodySmall = TextStyle(fontFamily = FontFamily.Serif),
    labelLarge = TextStyle(fontFamily = FontFamily.Serif),
    labelMedium = TextStyle(fontFamily = FontFamily.Serif),
    labelSmall = TextStyle(fontFamily = FontFamily.Serif)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = isSystemInDarkTheme()
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
                typography = CambriaTypography
            ) {
                UnzipScreen()
            }
        }
    }
}
