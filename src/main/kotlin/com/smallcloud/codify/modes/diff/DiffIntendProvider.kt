package com.smallcloud.codify.modes.diff

import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.codify.settings.AppSettingsState

class DiffIntendProvider {
    val defaultIntends: List<String>
        get(): List<String> {
            return listOf(
                "Add type hints",
                "Remove type hints",
                "Convert to list comprehension",
                "Add docstrings"
            )
        }
    var historyIntends
        set(newVal) {
            AppSettingsState.instance.diffIntendsHistory = newVal
        }
        get() = AppSettingsState.instance.diffIntendsHistory

    fun pushFrontHistoryIntend(newStr: String) {
        var srcHints = AppSettingsState.instance.diffIntendsHistory.filter { it != newStr }
        srcHints = srcHints.subList(0, minOf(srcHints.size, 12))
        AppSettingsState.instance.diffIntendsHistory = listOf(newStr) + srcHints
    }

    fun lastHistoryIntend(): String? {
        return AppSettingsState.instance.diffIntendsHistory.firstOrNull()
    }

    companion object {
        @JvmStatic
        val instance: DiffIntendProvider
            get() = ApplicationManager.getApplication().getService(DiffIntendProvider::class.java)
    }
}