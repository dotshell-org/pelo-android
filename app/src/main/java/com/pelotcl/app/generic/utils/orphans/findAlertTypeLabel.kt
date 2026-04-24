package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.ui.screens.plan.AlertType

fun findAlertTypeLabel(alertTypeId: String): String {
    return AlertType.entries.firstOrNull { it.id == alertTypeId }?.label ?: "Cette alerte"
}
