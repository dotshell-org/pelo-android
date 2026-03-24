package com.pelotcl.app.generic.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pelotcl.app.specific.ui.theme.TransportThemeImpl

/**
 * Interface for the transport theme
 * Each city must provide its own implementation
 */
interface TransportTheme {

    /**
     * Color for metro lines
     */
    val metroLineColor: Color

    /**
     * Color for tram lines
     */
    val tramLineColor: Color

    /**
     * Color for bus lines
     */
    val busLineColor: Color

    /**
     * Error color
     */
    val errorColor: Color

    /**
     * Success color
     */
    val successColor: Color

    /**
     * Warning color
     */
    val warningColor: Color

    /**
     * Disruption color
     */
    val disruptionColor: Color

    /**
     * Applies the theme to the composition
     */
    @Composable
    fun ApplyTheme(content: @Composable () -> Unit)
}

/**
 * Theme provider - allows dynamic theme changes
 */
object TransportThemeProvider {
    private var currentTheme: TransportTheme = TransportThemeImpl()

    fun setTheme(theme: TransportTheme) {
        currentTheme = theme
    }
}