package com.pelotcl.app.generic.ui.theme

import com.pelotcl.app.specific.ui.theme.TransportThemeImpl

/**
 * Theme provider - allows dynamic theme changes
 */
object TransportThemeProvider {
    private var currentTheme: TransportTheme = TransportThemeImpl()

    fun setTheme(theme: TransportTheme) {
        currentTheme = theme
    }
}