package com.smallcloud.codify.modes.diff

import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.codify.settings.AppSettingsState

class DiffIntentProvider {
    val defaultIntents: List<String>
        get(): List<String> {
            return listOf(
                "Add type hints",
                "Remove type hints",
                "Convert to list comprehension",
                "Add docstrings"
            )
        }
    val defaultThirdPartyFunctions: List<String>
        get(): List<String> {
            return listOf(
                "Explain code",
                "Fix bugs",
                "Complete selected code",
                "Explain error",
                "Add console logs",
                "Make code shorter"
            )
        }

    val thirdPartyFunctionsToId: Map<String, String> = hashMapOf(
        defaultThirdPartyFunctions[0] to "explain-code",
        defaultThirdPartyFunctions[1] to "fix-bug",
        defaultThirdPartyFunctions[2] to "complete-selected-code",
        defaultThirdPartyFunctions[3] to "explain-error",
        defaultThirdPartyFunctions[4] to "add-console-logs",
        defaultThirdPartyFunctions[5] to "make-code-shorter",
    )

    var historyIntents
        set(newVal) {
            AppSettingsState.instance.diffIntentsHistory = newVal
        }
        get() = AppSettingsState.instance.diffIntentsHistory

    fun pushFrontHistoryIntent(newStr: String) {
        var srcHints = AppSettingsState.instance.diffIntentsHistory.filter { it != newStr }
        srcHints = srcHints.subList(0, minOf(srcHints.size, 12))
        AppSettingsState.instance.diffIntentsHistory = listOf(newStr) + srcHints
    }

    fun lastHistoryIntent(): String? {
        return AppSettingsState.instance.diffIntentsHistory.firstOrNull()
    }

    companion object {
        @JvmStatic
        val instance: DiffIntentProvider
            get() = ApplicationManager.getApplication().getService(DiffIntentProvider::class.java)
    }
}