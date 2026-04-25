package com.pelotcl.app.specific.utils

object LineNamingUtils {

    fun canonicalLineName(lineName: String): String {
        return when (val upperName = lineName.trim().uppercase()) {
            "NAVI1" -> "NAV1"
            else -> upperName
        }
    }

    fun areEquivalentLineNames(first: String, second: String): Boolean {
        return canonicalLineName(first) == canonicalLineName(second)
    }

    fun normalizeLineNameForUi(lineName: String): String {
        return if (canonicalLineName(lineName) == "NAV1") "NAVI1" else lineName
    }

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

            when (up) {
                "A" -> return Key(1000, number = 0, raw = up)
                "B" -> return Key(1001, number = 0, raw = up)
                "C" -> return Key(1002, number = 0, raw = up)
                "D" -> return Key(1003, number = 0, raw = up)
            }

            if (up.startsWith("F")) {
                val num = up.drop(1).toIntOrNull()
                if (num != null) return Key(2000, number = num, raw = up)
            }

            if (up.startsWith("T")) {
                val num = up.drop(1).toIntOrNull()
                if (num != null) return Key(3000, number = num, raw = up)
            }

            val regex = Regex("^([A-Z]+)(\\d+)([A-Z]*)$")
            val match = regex.matchEntire(up)
            if (match != null) {
                val prefix = match.groupValues[1]
                val num = match.groupValues[2].toIntOrNull() ?: Int.MAX_VALUE
                return Key(4000, subFamily = prefix, number = num, raw = up)
            }

            val pureNum = up.toIntOrNull()
            if (pureNum != null) {
                return Key(5000, number = pureNum, raw = up)
            }

            return Key(9000, subFamily = up, number = Int.MAX_VALUE, raw = up)
        }

        return lines
            .filter { !it.equals("T36", ignoreCase = true) }
            .sortedWith(Comparator { a, b ->
                val ka = keyFor(a)
                val kb = keyFor(b)
                when {
                    ka.family != kb.family -> ka.family - kb.family
                    ka.subFamily != kb.subFamily -> ka.subFamily.compareTo(kb.subFamily)
                    ka.number != kb.number -> ka.number - kb.number
                    else -> ka.raw.compareTo(kb.raw)
                }
            })
    }
}
