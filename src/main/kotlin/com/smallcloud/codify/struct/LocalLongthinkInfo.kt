package com.smallcloud.codify.struct

import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties

data class LocalLongthinkInfo(var isBookmarked: Boolean = false) {
    companion object {
        fun fromEntry(entry: LongthinkFunctionEntry): LocalLongthinkInfo {
            return LocalLongthinkInfo().also { localInfo ->
                LocalLongthinkInfo::class.memberProperties.forEach { localField ->
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