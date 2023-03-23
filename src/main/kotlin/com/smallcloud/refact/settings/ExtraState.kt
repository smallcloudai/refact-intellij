package com.smallcloud.refact.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.smallcloud.refact.struct.LocalLongthinkInfo
import com.smallcloud.refact.struct.ShortLongthinkHistoryInfo

@State(name = "com.smallcloud.codify.settings.ExtraState", storages = [Storage("CodifyExtra.xml")])
class ExtraState : PersistentStateComponent<ExtraState.State> {
    class State {
        var localLongthinkInfos: MutableMap<String, LocalLongthinkInfo> =
            emptyMap<String, LocalLongthinkInfo>().toMutableMap()
        var historyEntries: List<ShortLongthinkHistoryInfo> = emptyList()
        var usageStatsMessagesCache: MutableMap<String, Int> = HashMap()
        var usageAcceptRejectMetricsCache: MutableList<String> = mutableListOf()
    }

    fun getLocalLongthinkInfo(functionName: String): LocalLongthinkInfo? {
        return state.localLongthinkInfos[functionName]
    }

    fun insertLocalLongthinkInfo(functionName: String, info: LocalLongthinkInfo) {
        state.localLongthinkInfos[functionName] = info
    }

    private var state: State = State()

    var historyEntries
        get() = state.historyEntries
        set(value) {
            state.historyEntries = value
        }
    var usageStatsMessagesCache
        get() = state.usageStatsMessagesCache
        set(value) {
            state.usageStatsMessagesCache = value
        }
    var usageAcceptRejectMetricsCache
        get() = state.usageAcceptRejectMetricsCache
        set(value) {
            state.usageAcceptRejectMetricsCache = value
        }

    override fun getState(): State {
        return state
    }

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        @JvmStatic
        val instance: ExtraState
            get() = ApplicationManager.getApplication().getService(ExtraState::class.java)
    }
}