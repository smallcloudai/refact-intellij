package com.smallcloud.refactai.struct

import com.google.gson.Gson
import com.intellij.util.xmlb.Converter
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties

data class ShortLongthinkHistoryInfo(
    var intent: String = "",
    var functionName: String = ""
) {
    companion object {
        fun fromEntry(entry: LongthinkFunctionEntry): ShortLongthinkHistoryInfo {
            return ShortLongthinkHistoryInfo().also { localInfo ->
                localInfo::class.memberProperties.forEach { localField ->
                    val entryField = entry::class.memberProperties.find { it.name == localField.name }
                    if (localField is KMutableProperty<*>) {
                        entryField?.let {
                            localField.setter.call(localInfo, it.getter.call(entry))
                        }
                    }
                }
            }
        }
    }
}

private class ShortLongthinkHistoryInfoConverter: Converter<ShortLongthinkHistoryInfo>() {
    override fun toString(value: ShortLongthinkHistoryInfo): String? {
        val gson = Gson()
        return gson.toJson(value)
    }

    override fun fromString(value: String): ShortLongthinkHistoryInfo? {
        val gson = Gson()
        return gson.fromJson(value, ShortLongthinkHistoryInfo::class.java)
    }
}