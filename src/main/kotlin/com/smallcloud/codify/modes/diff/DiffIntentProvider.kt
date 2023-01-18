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