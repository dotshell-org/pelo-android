package com.pelotcl.app.specific.data.rules

import com.pelotcl.app.generic.data.network.transport.TransportLineRules

class LyonTransportLineRules : TransportLineRules {
    override fun normalizeAlertToken(raw: String): String {
        val token = raw.uppercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("LIGNES", "")
            .replace("LIGNE", "")
            .replace("TRAM", "")
            .replace("METRO", "")
            .trim()

        return when {
            token.contains("RHONEXPRESS") -> "RX"
            else -> token
        }
    }

    override fun isLikelyLineToken(token: String): Boolean {
        if (token.isBlank()) return false
        return token in setOf("A", "B", "C", "D", "F1", "F2", "RX") ||
            token.matches(Regex("^TB\\d{1,2}[A-Z]?$")) ||
            token.matches(Regex("^T\\d{1,2}[A-Z]?$")) ||
            token.matches(Regex("^C\\d{1,2}[A-Z]?$")) ||
            token.matches(Regex("^NAVI\\d{1,2}$")) ||
            token.matches(Regex("^JD\\d{1,3}$")) ||
            token.matches(Regex("^GE\\d{1,2}$")) ||
            token.matches(Regex("^PL\\d{1,2}$")) ||
            token.matches(Regex("^ZI\\d{1,2}$")) ||
            token.matches(Regex("^S\\d{1,2}$")) ||
            token.matches(Regex("^N\\d{1,2}$")) ||
            token.matches(Regex("^\\d{1,3}[A-Z]?$"))
    }

    override fun canonicalRouteName(raw: String): String {
        val token = raw.trim().uppercase()
        return when (token) {
            "NAVI1" -> "NAV1"
            else -> token
        }
    }

    override fun equivalentRouteNames(raw: String): List<String> {
        val token = raw.trim().uppercase()
        return when (token) {
            "NAVI1", "NAV1" -> listOf("NAVI1", "NAV1")
            else -> listOf(token)
        }
    }

    override fun normalizeForComparison(raw: String): String {
        val token = raw.trim().uppercase()
        return if (token.contains("RHONEXPRESS")) {
            "RX"
        } else {
            // Keep canonical naming so that "NAVI1" is handled as "NAV1" too.
            canonicalRouteName(token)
        }
    }
}

