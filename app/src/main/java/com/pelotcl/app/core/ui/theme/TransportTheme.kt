package com.pelotcl.app.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Interface pour le thème de transport
 * Chaque ville doit fournir sa propre implémentation
 */
interface TransportTheme {
    
    /**
     * Couleur principale du réseau
     */
    val primaryColor: Color
    
    /**
     * Couleur secondaire du réseau
     */
    val secondaryColor: Color
    
    /**
     * Couleur d'accentuation
     */
    val accentColor: Color
    
    /**
     * Couleur pour les lignes de métro
     */
    val metroLineColor: Color
    
    /**
     * Couleur pour les lignes de tramway
     */
    val tramLineColor: Color
    
    /**
     * Couleur pour les lignes de bus
     */
    val busLineColor: Color
    
    /**
     * Couleur d'erreur
     */
    val errorColor: Color
    
    /**
     * Couleur de succès
     */
    val successColor: Color
    
    /**
     * Couleur d'avertissement
     */
    val warningColor: Color
    
    /**
     * Couleur pour les perturbations
     */
    val disruptionColor: Color
    
    /**
     * Applique le thème à la composition
     */
    @Composable
    fun ApplyTheme(content: @Composable () -> Unit)
    
    /**
     * Obtient la couleur pour un type de ligne spécifique
     */
    @Composable
    fun getLineTypeColor(lineType: String): Color
}

/**
 * Fournisseur de thème - permet de changer dynamiquement
 */
object TransportThemeProvider {
    private var currentTheme: TransportTheme = DefaultTransportTheme()
    
    fun setTheme(theme: TransportTheme) {
        currentTheme = theme
    }
    
    fun getTheme(): TransportTheme = currentTheme
}

/**
 * Implémentation par défaut (peut être remplacée par chaque ville)
 */
@Immutable
class DefaultTransportTheme : TransportTheme {
    override val primaryColor: Color = Color(0xFF2196F3) // Bleu par défaut
    override val secondaryColor: Color = Color(0xFF9C27B0) // Violet par défaut
    override val accentColor: Color = Color(0xFFFFC107) // Jaune par défaut
    override val metroLineColor: Color = Color(0xFFE91E63) // Rose
    override val tramLineColor: Color = Color(0xFF4CAF50) // Vert
    override val busLineColor: Color = Color(0xFF9C27B0) // Violet
    override val errorColor: Color = Color(0xFFF44336) // Rouge
    override val successColor: Color = Color(0xFF4CAF50) // Vert
    override val warningColor: Color = Color(0xFFFF9800) // Orange
    override val disruptionColor: Color = Color(0xFFF44336) // Rouge
    
    @Composable
    override fun ApplyTheme(content: @Composable () -> Unit) {
        // Appliquer le thème Material par défaut
        androidx.compose.material3.MaterialTheme(
            colorScheme = androidx.compose.material3.lightColorScheme(
                primary = primaryColor,
                secondary = secondaryColor,
                tertiary = accentColor,
                error = errorColor
            ),
            content = content
        )
    }
    
    @Composable
    override fun getLineTypeColor(lineType: String): Color {
        return when (lineType.lowercase()) {
            "metro", "funicular" -> metroLineColor
            "tram", "tramway" -> tramLineColor
            "bus" -> busLineColor
            else -> primaryColor
        }
    }
}