package com.pelotcl.app.specific.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.pelotcl.app.generic.ui.theme.AccentColor
import com.pelotcl.app.generic.ui.theme.PrimaryColor
import com.pelotcl.app.generic.ui.theme.SecondaryColor
import com.pelotcl.app.generic.ui.theme.TransportTheme

/**
 * Implémentation du thème de transport
 * Couleurs officielles du réseau de transport
 */
@Immutable
class TransportThemeImpl : TransportTheme {
    override val metroLineColor: Color = Color(0xFFE60000) // Couleur ligne métro
    override val tramLineColor: Color = Color(0xFF007AC3) // Couleur ligne tramway
    override val busLineColor: Color = Color(0xFF00A651) // Couleur ligne bus
    
    // Couleurs d'état
    override val errorColor: Color = Color(0xFFD32F2F) // Rouge erreur
    override val successColor: Color = Color(0xFF388E3C) // Vert succès
    override val warningColor: Color = Color(0xFFF57C00) // Orange avertissement
    override val disruptionColor: Color = Color(0xFFD32F2F) // Rouge perturbation
    
    @Composable
    override fun ApplyTheme(content: @Composable () -> Unit) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.lightColorScheme(
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
            typography = androidx.compose.material3.Typography(
                displayLarge = androidx.compose.material3.Typography().displayLarge.copy(color = PrimaryColor),
                displayMedium = androidx.compose.material3.Typography().displayMedium.copy(color = PrimaryColor),
                displaySmall = androidx.compose.material3.Typography().displaySmall.copy(color = PrimaryColor),
                headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(color = PrimaryColor),
                headlineMedium = androidx.compose.material3.Typography().headlineMedium.copy(color = PrimaryColor),
                headlineSmall = androidx.compose.material3.Typography().headlineSmall.copy(color = PrimaryColor),
                titleLarge = androidx.compose.material3.Typography().titleLarge.copy(color = PrimaryColor),
                titleMedium = androidx.compose.material3.Typography().titleMedium.copy(color = PrimaryColor),
                titleSmall = androidx.compose.material3.Typography().titleSmall.copy(color = PrimaryColor),
                bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(color = PrimaryColor),
                bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(color = PrimaryColor),
                bodySmall = androidx.compose.material3.Typography().bodySmall.copy(color = PrimaryColor),
                labelLarge = androidx.compose.material3.Typography().labelLarge.copy(color = PrimaryColor),
                labelMedium = androidx.compose.material3.Typography().labelMedium.copy(color = PrimaryColor),
                labelSmall = androidx.compose.material3.Typography().labelSmall.copy(color = PrimaryColor)
            ),
            content = content
        )
    }
    
    @Composable
    override fun getLineTypeColor(lineType: String): Color {
        return when (lineType.lowercase()) {
            "metro", "funicular", "mf" -> metroLineColor
            "tram", "tramway" -> tramLineColor
            "bus" -> busLineColor
            "rhonexpress", "rx" -> Color(0xFF00A0B0) // Bleu Rhônexpress
            "navigone", "navi" -> Color(0xFF6B5B95) // Violet Navigone
            "trambus", "tb" -> Color(0xFFF57C00) // Orange Trambus
            else -> PrimaryColor
        }
    }

}