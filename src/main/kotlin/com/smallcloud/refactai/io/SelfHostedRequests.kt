package com.smallcloud.refactai.io

//fun loadLongThinkFunctions() {
//    val uri = InferenceGlobalContext.inferenceUri?.resolve(
//            Resources.defaultSelfHostedLongthinkFunctionsSuffix) ?: return
//    try {
//        val result = sendRequest(uri, "GET", headers = mutableMapOf("Content-Type" to "application/json"),
//                requestProperties = mapOf(
//                        "redirect" to "follow",
//                        "cache" to "no-cache",
//                        "referrer" to "no-referrer"
//                ))
//        val gson = makeGson()
//        val body = gson.fromJson(result.body, JsonObject::class.java)
//        if (body.has("longthink-functions")) {
//            val cloudEntries = body.get("longthink-functions").asJsonObject.entrySet().map {
//                val elem = gson.fromJson(it.value, LongthinkFunctionEntry::class.java)
//                elem.entryName = it.key
//                return@map elem.mergeLocalInfo(ExtraState.instance.getLocalLongthinkInfo(elem.entryName))
//            }
//            LongthinkFunctionProvider.defaultThirdPartyFunctions = cloudEntries
//            LongthinkFunctionProvider.intentFilters = listOf()
//            QuickLongthinkActionsService.instance.recreateActions()
//        }
//
//    } catch (_: Exception) {
//        println("Failed to load long think functions")
//    }
//}
//
//fun loadInfoFromSelfHostedServer() {
//    loadLongThinkFunctions()
//}