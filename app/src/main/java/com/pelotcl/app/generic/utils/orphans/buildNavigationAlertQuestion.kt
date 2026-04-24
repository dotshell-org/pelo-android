package com.pelotcl.app.generic.utils.orphans

import com.pelotcl.app.generic.data.models.navigation.NavigationAlertPrompt
import com.pelotcl.app.generic.data.models.navigation.NavigationAlertPromptKind

fun buildNavigationAlertQuestion(prompt: NavigationAlertPrompt): String {
    val alertLabel = findAlertTypeLabel(prompt.alertTypeId)
    return when (prompt.kind) {
        NavigationAlertPromptKind.LOW_KARMA_CONFIRM -> "$alertLabel bien là ?"
        NavigationAlertPromptKind.HIGH_KARMA_STILL_THERE -> "$alertLabel toujours là ?"
    }
}