package com.pelotcl.app.specific.utils.orphans

fun isTemporaryBus(lineName: String): Boolean {
    return !isMetroTramOrFunicular(lineName)
}
