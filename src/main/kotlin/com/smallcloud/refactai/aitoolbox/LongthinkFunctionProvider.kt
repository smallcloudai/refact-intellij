package com.smallcloud.refactai.aitoolbox

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.struct.LongthinkFunctionVariation
import com.smallcloud.refactai.struct.ShortLongthinkHistoryInfo
import com.smallcloud.refactai.settings.ExtraState.Companion.instance as ExtraState

interface LongthinkFunctionProviderChangedNotifier {
    fun longthinkFunctionsChanged(functions: List<LongthinkFunctionEntry>) {}
    fun longthinkFiltersChanged(filters: List<String>) {}

    companion object {
        val TOPIC = Topic.create(
                "Longthink Function Provider Changed Notifier",
                LongthinkFunctionProviderChangedNotifier::class.java
        )
    }
}


class LongthinkFunctionProvider: Disposable {
    private var _cloudIntents: List<LongthinkFunctionEntry> = emptyList()
    private var _intentFilters: List<String> = emptyList()

    var intentFilters: List<String>
        get() = _intentFilters
        set(newList) {
            if (_intentFilters != newList) {
                _intentFilters = newList
                ApplicationManager.getApplication().messageBus
                        .syncPublisher(LongthinkFunctionProviderChangedNotifier.TOPIC)
                        .longthinkFiltersChanged(_intentFilters)
            }
        }

    val functionVariations: List<LongthinkFunctionVariation>
        get() {
            val functionNameToVariations = mutableMapOf<String, Pair<MutableList<String>,
                    MutableList<LongthinkFunctionEntry>>>()

            for (func in defaultThirdPartyFunctions) {
                val matchedFilter = _intentFilters.firstOrNull { func.functionName.endsWith(it) }
                val funcName = if (matchedFilter != null)
                    func.functionName.substring(0, func.functionName.length - matchedFilter.length - 1) else
                        func.functionName
                val filtersAndVariations = functionNameToVariations.getOrPut(funcName) { Pair(mutableListOf(), mutableListOf()) }
                filtersAndVariations.first.add(matchedFilter ?: "")
                filtersAndVariations.second.add(func)
            }
            return functionNameToVariations.map { LongthinkFunctionVariation(it.value.second, it.value.first) }
        }

    var defaultThirdPartyFunctions: List<LongthinkFunctionEntry>
        get(): List<LongthinkFunctionEntry> {
            return _cloudIntents.filter { !(it.functionName.contains("free-chat") ||
                    it.functionName.contains("completion")) }
        }
        set(newList) {
            if (_cloudIntents != newList) {
                _cloudIntents = newList
                ApplicationManager.getApplication().messageBus
                        .syncPublisher(LongthinkFunctionProviderChangedNotifier.TOPIC)
                        .longthinkFunctionsChanged(_cloudIntents)
            }
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

    val allChats: List<LongthinkFunctionEntry>
        get() {
            return _cloudIntents.filter {
                it.functionName.contains("chat") && it.model?.isNotEmpty() ?: false
            }
        }

    companion object {
        @JvmStatic
        val instance: LongthinkFunctionProvider
            get() = ApplicationManager.getApplication().getService(LongthinkFunctionProvider::class.java)
    }

    override fun dispose() {}

    fun cleanUp() {
        defaultThirdPartyFunctions = emptyList()
        intentFilters = emptyList()
    }
}
