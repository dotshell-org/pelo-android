package com.pelotcl.app.specific.utils.orphans

fun areEquivalentLineNames(first: String, second: String): Boolean {
    return canonicalLineName(first) == canonicalLineName(second)
}
