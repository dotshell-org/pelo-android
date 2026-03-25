package com.pelotcl.app.generic.data.network

/**
 * Rules for normalizing and matching transport line names.
 *
 * This is intentionally implemented in `specific` because aliasing rules are
 * network/city-specific (e.g. Rhônexpress -> RX, NAVI1 -> NAV1, regex patterns, etc.).
 */
interface TransportLineRules {
    /**
     * Normalizes a raw token coming from traffic alerts text/fields so it can be compared
     * against line names used in schedules / routing.
     */
    fun normalizeAlertToken(raw: String): String

    /**
     * Returns whether a token is likely to represent a line name.
     */
    fun isLikelyLineToken(token: String): Boolean

    /**
     * Canonical route name used to match schedules route names.
     */
    fun canonicalRouteName(raw: String): String

    /**
     * Returns equivalent route names to match against schedule data.
     * Example: "NAVI1" might match both "NAVI1" and "NAV1".
     */
    fun equivalentRouteNames(raw: String): List<String>

    /**
     * Normalizes a line name for comparison (e.g. live vehicle stream filtering).
     */
    fun normalizeForComparison(raw: String): String
}

