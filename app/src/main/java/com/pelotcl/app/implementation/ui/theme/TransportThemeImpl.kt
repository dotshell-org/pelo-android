package com.pelotcl.app.implementation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.pelotcl.app.core.ui.theme.TransportTheme

/**
 * Implémentation du thème de transport
 * Couleurs officielles du réseau de transport
 */
@Immutable
class TransportThemeImpl : TransportTheme {
    
    // Couleurs officielles
    override val primaryColor: Color = Color(0xFFE60000) // Couleur primaire
    override val secondaryColor: Color = Color(0xFF000000) // Couleur secondaire
    override val accentColor: Color = Color(0xFFFFFFFF) // Couleur d'accent
    
    // Couleurs par type de transport
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
                primary = primaryColor,
                secondary = secondaryColor,
                tertiary = accentColor,
                background = Color.White,
                surface = Color.White,
                onPrimary = Color.White,
                onSecondary = Color.White,
                onBackground = Color.Black,
                onSurface = Color.Black,
                error = errorColor,
                onError = Color.White
            ),
            typography = androidx.compose.material3.Typography(
                displayLarge = androidx.compose.material3.Typography().displayLarge.copy(color = primaryColor),
                displayMedium = androidx.compose.material3.Typography().displayMedium.copy(color = primaryColor),
                displaySmall = androidx.compose.material3.Typography().displaySmall.copy(color = primaryColor),
                headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(color = primaryColor),
                headlineMedium = androidx.compose.material3.Typography().headlineMedium.copy(color = primaryColor),
                headlineSmall = androidx.compose.material3.Typography().headlineSmall.copy(color = primaryColor),
                titleLarge = androidx.compose.material3.Typography().titleLarge.copy(color = primaryColor),
                titleMedium = androidx.compose.material3.Typography().titleMedium.copy(color = primaryColor),
                titleSmall = androidx.compose.material3.Typography().titleSmall.copy(color = primaryColor),
                bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(color = Color.Black),
                bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(color = Color.Black),
                bodySmall = androidx.compose.material3.Typography().bodySmall.copy(color = Color.Black),
                labelLarge = androidx.compose.material3.Typography().labelLarge.copy(color = Color.Black),
                labelMedium = androidx.compose.material3.Typography().labelMedium.copy(color = Color.Black),
                labelSmall = androidx.compose.material3.Typography().labelSmall.copy(color = Color.Black)
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
            else -> primaryColor
        }
    }
    
    /**
     * Obtient la couleur pour une ligne spécifique par son code
     * Gère les cas spécifiques des lignes de transport
     */
    @Composable
    fun getLineColorByCode(lineCode: String): Color {
        // Lignes de métro (A, B, C, D)
        if (lineCode.matches(Regex("^[ABCD]$"))) return metroLineColor
        
        // Lignes de tramway (T1-T6, Rhônexpress)
        if (lineCode.matches(Regex("^T[1-6]$"))) return tramLineColor
        if (lineCode.equals("RX", ignoreCase = true)) return Color(0xFF00A0B0)
        
        // Lignes de bus spéciales
        if (lineCode.matches(Regex("^C[1-9][0-9]?$"))) return Color(0xFFE57300) // Chrono
        if (lineCode.matches(Regex("^TB[1-9][0-9]?$"))) return Color(0xFFF57C00) // Trambus
        
        // Bus normaux
        return busLineColor
    }
}