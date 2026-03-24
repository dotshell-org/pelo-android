package com.pelotcl.app.specific.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.theme.TransportTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme

/**
 * Implementation of the transport theme
 * Official colors of the transport network
 */
@Immutable
class TransportThemeImpl : TransportTheme {
    override val metroLineColor: Color = Color(0xFFE60000) // Metro line color
    override val tramLineColor: Color = Color(0xFF007AC3) // Tramway line color
    override val busLineColor: Color = Color(0xFF00A651) // Bus line color

    // Status colors
    override val errorColor: Color = Color(0xFFD32F2F) // Error red
    override val successColor: Color = Color(0xFF388E3C) // Success green
    override val warningColor: Color = Color(0xFFF57C00) // Warning orange
    override val disruptionColor: Color = Color(0xFFD32F2F) // Disruption red
    
    @Composable
    override fun ApplyTheme(content: @Composable () -> Unit) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = lightColorScheme(
                primary = PrimaryColor,
                secondary = SecondaryColor,
                tertiary = AccentColor,
                background = SecondaryColor,
                surface = SecondaryColor,
                onPrimary = SecondaryColor,
                onSecondary = SecondaryColor,
                onBackground = PrimaryColor,
                onSurface = PrimaryColor,
                error = errorColor,
                onError = SecondaryColor
            ),
            typography = Typography(
                displayLarge = Typography().displayLarge.copy(color = PrimaryColor),
                displayMedium = Typography().displayMedium.copy(color = PrimaryColor),
                displaySmall = Typography().displaySmall.copy(color = PrimaryColor),
                headlineLarge = Typography().headlineLarge.copy(color = PrimaryColor),
                headlineMedium = Typography().headlineMedium.copy(color = PrimaryColor),
                headlineSmall = Typography().headlineSmall.copy(color = PrimaryColor),
                titleLarge = Typography().titleLarge.copy(color = PrimaryColor),
                titleMedium = Typography().titleMedium.copy(color = PrimaryColor),
                titleSmall = Typography().titleSmall.copy(color = PrimaryColor),
                bodyLarge = Typography().bodyLarge.copy(color = PrimaryColor),
                bodyMedium = Typography().bodyMedium.copy(color = PrimaryColor),
                bodySmall = Typography().bodySmall.copy(color = PrimaryColor),
                labelLarge = Typography().labelLarge.copy(color = PrimaryColor),
                labelMedium = Typography().labelMedium.copy(color = PrimaryColor),
                labelSmall = Typography().labelSmall.copy(color = PrimaryColor)
            ),
            content = content
        )
    }

}