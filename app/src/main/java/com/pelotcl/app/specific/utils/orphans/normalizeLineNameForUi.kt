package com.pelotcl.app.specific.utils.orphans

fun normalizeLineNameForUi(lineName: String): String {
    return if (canonicalLineName(lineName) == "NAV1") "NAVI1" else lineName
}
