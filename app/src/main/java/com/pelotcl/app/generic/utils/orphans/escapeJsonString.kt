package com.pelotcl.app.generic.utils.orphans

/**
 * Escapes special characters in a string for safe JSON embedding.
 */
fun escapeJsonString(s: String): String {
    if (s.isEmpty()) return s
    // Fast path: most strings don't need escaping
    var needsEscape = false
    for (c in s) {
        if (c == '"' || c == '\\' || c == '\n' || c == '\r' || c == '\t') {
            needsEscape = true
            break
        }
    }
    if (!needsEscape) return s

    val sb = StringBuilder(s.length + 8)
    for (c in s) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
