package com.pelotcl.app.specific.utils.orphans

/**
 * Sorts lines for display in the bottom sheet.
 * Family order:
 *  1) Metro: A, B, C, D (fixed order)
 *  2) Funiculars: F1, F2 (then F3+ if existed) sorted numerically
 *  3) Tram: T1..Tn sorted numerically
 *  4) Buses with letter prefix (ex: C1, JD12, S3, ZI2...) sorted by prefix then number
 *  5) Purely numeric buses (ex: 2, 12, 79) sorted numerically
 *  6) Others/undetermined: case-insensitive lexicographic order
 */
fun sortLines(lines: List<String>): List<String> {
    data class Key(
        val family: Int,
        val subFamily: String = "",
        val number: Int = Int.MAX_VALUE,
        val raw: String = ""
    )

    fun keyFor(lineRaw: String): Key {
        val line = lineRaw.trim()
        val up = line.uppercase()

        // 1) Metro A-D fixed order
        when (up) {
            "A" -> return Key(1000, number = 0, raw = up)
            "B" -> return Key(1001, number = 0, raw = up)
            "C" -> return Key(1002, number = 0, raw = up)
            "D" -> return Key(1003, number = 0, raw = up)
        }

        // 2) Funiculaire F1..Fn
        if (up.startsWith("F")) {
            val num = up.drop(1).toIntOrNull()
            if (num != null) return Key(2000, number = num, raw = up)
        }

        // 3) Tram T1..Tn
        if (up.startsWith("T")) {
            val num = up.drop(1).toIntOrNull()
            if (num != null) return Key(3000, number = num, raw = up)
        }

        // 4) Buses with letter prefix + number (C1, JD12, S3, etc.)
        // Regex: letters (at least 1) + number (at least 1) + optional letter suffix
        val regex = Regex("^([A-Z]+)(\\d+)([A-Z]*)$")
        val m = regex.matchEntire(up)
        if (m != null) {
            val prefix = m.groupValues[1]
            val num = m.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE
            // Some adjustments to keep consistent order of frequent TCL families
            // Force sub-order for certain prefixes known to avoid, for example, JD passing before C if desired.
            // Here we're satisfied with alphabetical sort of prefix, which gives: C, JD, S, ...
            return Key(4000, subFamily = prefix, number = num, raw = up)
        }

        // 5) Pure numeric
        val pureNum = up.toIntOrNull()
        if (pureNum != null) {
            return Key(5000, number = pureNum, raw = up)
        }

        // 6) Fallback lexicographique
        return Key(9000, subFamily = up, number = Int.MAX_VALUE, raw = up)
    }

    return lines
        .filter { !it.equals("T36", ignoreCase = true) }
        .sortedWith(Comparator { a, b ->
            val ka = keyFor(a)
            val kb = keyFor(b)
            // Compare by family, then prefix/subFamily, then numeric part, finally raw label
            when {
                ka.family != kb.family -> ka.family - kb.family
                ka.subFamily != kb.subFamily -> ka.subFamily.compareTo(kb.subFamily)
                ka.number != kb.number -> ka.number - kb.number
                else -> ka.raw.compareTo(kb.raw)
            }
        })
}
