package com.smallcloud.refactai.modes.diff

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.struct.ShortLongthinkHistoryInfo
import com.smallcloud.refactai.settings.ExtraState.Companion.instance as ExtraState

class DiffIntentProvider: Disposable {
    private var _cloudIntents: List<LongthinkFunctionEntry> = emptyList()
    var defaultThirdPartyFunctions: List<LongthinkFunctionEntry>
        get(): List<LongthinkFunctionEntry> = _cloudIntents
        set(newList) {
            _cloudIntents = newList
        }

    var historyIntents: List<LongthinkFunctionEntry>
        set(newVal) {
            ExtraState.historyEntries = newVal.map { ShortLongthinkHistoryInfo.fromEntry(it) }
        }
        get() = ExtraState.historyEntries.map { shortInfo ->
            var appropriateEntry = _cloudIntents.find { it.functionName == shortInfo.functionName } ?: return@map null
            appropriateEntry = appropriateEntry.mergeShortInfo(shortInfo)
            if (appropriateEntry.intent.isEmpty()) return@map null
            appropriateEntry
        }.filterNotNull()


    fun pushFrontHistoryIntent(newEntry: LongthinkFunctionEntry) {
        if (newEntry.intent.isEmpty()) return
        var srcHints = historyIntents.filter { it.intent != newEntry.intent }
        srcHints = srcHints.subList(0, minOf(srcHints.size, 20))
        historyIntents = listOf(newEntry) + srcHints
    }

    fun lastHistoryEntry(): LongthinkFunctionEntry? {
        return historyIntents.firstOrNull()
    }

    companion object {
        @JvmStatic
        val instance: DiffIntentProvider
            get() = ApplicationManager.getApplication().getService(DiffIntentProvider::class.java)
    }

    override fun dispose() {}
}
