package com.smallcloud.codify.modes.diff

import com.google.gson.annotations.SerializedName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.xmlb.annotations.OptionTag
import com.smallcloud.codify.settings.AppSettingsState

data class DiffIntendEntry(
    @OptionTag @SerializedName("label") val intend: String = "",
    @OptionTag val model: String? = null,
    @OptionTag @SerializedName("selection-required") val selectionRequired: Boolean = false,
    @OptionTag @SerializedName("selected_lines_min") val selectedLinesMin: Int = 0,
    @OptionTag @SerializedName("selected_lines_max") val selectedLinesMax: Int = 99999,
    @OptionTag val metering: Boolean = false,
    @OptionTag var functionName: String = ""
)

class DiffIntentProvider {
    private var _cloudIntents: List<DiffIntendEntry> = emptyList()
    var defaultThirdPartyFunctions: List<DiffIntendEntry>
        get(): List<DiffIntendEntry> = _cloudIntents
        set(newList) {
            _cloudIntents = newList
        }


    var historyIntents
        set(newVal) {
            AppSettingsState.instance.diffIntentEntriesHistory = newVal
        }
        get() = AppSettingsState.instance.diffIntentEntriesHistory



    fun pushFrontHistoryIntent(newStr: DiffIntendEntry) {
        var srcHints = AppSettingsState.instance.diffIntentEntriesHistory.filter { it != newStr }
        srcHints = srcHints.subList(0, minOf(srcHints.size, 20))
        AppSettingsState.instance.diffIntentEntriesHistory = listOf(newStr) + srcHints
    }

    fun lastHistoryEntry(): DiffIntendEntry? {
        return AppSettingsState.instance.diffIntentEntriesHistory.firstOrNull()
    }

    companion object {
        @JvmStatic
        val instance: DiffIntentProvider
            get() = ApplicationManager.getApplication().getService(DiffIntentProvider::class.java)
    }
}