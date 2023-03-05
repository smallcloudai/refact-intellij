package com.smallcloud.codify.panes.gptchat.utils

import com.google.gson.Gson
import com.google.gson.JsonObject

fun parse(response: String?, doStream: Boolean): String? {
    val gson = Gson()
    if (doStream) {
        val dataPrefix = "data: "
        if (response == "$dataPrefix[DONE]") return ""
        if (response?.startsWith(dataPrefix) == true) {
            val line = response.substring(dataPrefix.length)
            val obj = gson.fromJson(line, JsonObject::class.java)
            return obj.get("delta").asString
        }
    } else {
        val obj = gson.fromJson(response, JsonObject::class.java)
        return obj.get("choices").asJsonArray[0].asJsonObject.get("message").asJsonObject.get("content").asString
    }
    return null
}