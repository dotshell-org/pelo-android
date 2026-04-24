package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.navigation.NavigationAlertPrompt
import com.pelotcl.app.generic.data.models.navigation.NavigationAlertPromptKind
import com.pelotcl.app.generic.data.models.realtime.alerts.community.UserStopAlertsResponse
import com.pelotcl.app.generic.ui.screens.plan.AlertType

fun buildNavigationAlertPrompt(
    alerts: UserStopAlertsResponse,
    stopId: String?
): NavigationAlertPrompt? {
    val status = stopId?.let(alerts::get) ?: return null

    val highKarmaAlert = status.karmaAtOrAboveThreshold.maxByOrNull { it.karma }
    if (highKarmaAlert != null) {
        return NavigationAlertPrompt(
            kind = NavigationAlertPromptKind.HIGH_KARMA_STILL_THERE,
            alertTypeId = highKarmaAlert.type.ifBlank { AlertType.CROWDING.id }
        )
    }

    val lowKarmaAlert = status.karmaBelowThreshold.maxByOrNull { it.karma }
    if (lowKarmaAlert != null) {
        return NavigationAlertPrompt(
            kind = NavigationAlertPromptKind.LOW_KARMA_CONFIRM,
            alertTypeId = lowKarmaAlert.type.ifBlank { AlertType.CROWDING.id }
        )
    }

    return null
}
