package com.smallcloud.refactai.io

import com.google.gson.JsonObject
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.listeners.QuickLongthinkActionsService
import com.smallcloud.refactai.settings.ExtraState
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.utils.makeGson
import com.smallcloud.refactai.aitoolbox.LongthinkFunctionProvider.Companion.instance as LongthinkFunctionProvider
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

fun loadLongThinkFunctions() {
    val uri = InferenceGlobalContext.inferenceUri?.resolve(
            Resources.defaultSelfHostedLongthinkFunctionsSuffix) ?: return
    try {
        val result = sendRequest(uri, "GET", headers = mutableMapOf("Content-Type" to "application/json"),
                requestProperties = mapOf(
                        "redirect" to "follow",
                        "cache" to "no-cache",
                        "referrer" to "no-referrer"
                ))
        val gson = makeGson()
        val body = gson.fromJson(result.body, JsonObject::class.java)
        if (body.has("longthink-functions")) {
            val cloudEntries = body.get("longthink-functions").asJsonObject.entrySet().map {
                val elem = gson.fromJson(it.value, LongthinkFunctionEntry::class.java)
                elem.entryName = it.key
                return@map elem.mergeLocalInfo(ExtraState.instance.getLocalLongthinkInfo(elem.entryName))
            }
            LongthinkFunctionProvider.defaultThirdPartyFunctions = cloudEntries
            LongthinkFunctionProvider.intentFilters = listOf()
            QuickLongthinkActionsService.instance.recreateActions()
        }

    } catch (_: Exception) {
        println("Failed to load long think functions")
    }
}

fun loadInfoFromSelfHostedServer() {
    loadLongThinkFunctions()
}