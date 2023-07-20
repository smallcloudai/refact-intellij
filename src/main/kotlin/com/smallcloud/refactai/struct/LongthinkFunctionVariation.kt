package com.smallcloud.refactai.struct

class LongthinkFunctionVariation(val functions: List<LongthinkFunctionEntry>,
                                 availableFilters: List<String>) {

    fun getFunctionByFilter(filter: String): LongthinkFunctionEntry {
        return functions.find { it.functionName.endsWith(filter)} ?: throw Exception("Filter not found")
    }

    var activeFilter: String = availableFilters.first()
        get() {
            if (availableFilters.contains(field)) {
                return field
            }
            return availableFilters.first()
        }

    fun getFunctionByFilter(): LongthinkFunctionEntry {
        return getFunctionByFilter(activeFilter)
    }

    val functionName: String
    val availableFilters: List<String>
    init {
        if (availableFilters.isEmpty()) {
            this.availableFilters = listOf("")
        } else {
            this.availableFilters = availableFilters
        }

        val filterName = availableFilters.first()
        val func = functions.first()
        functionName = if (filterName.isEmpty()) {
            func.functionName
        } else {
            func.functionName.substring(0, func.functionName.length - filterName.length - 1)
        }
    }

    val label: String
        get() {
            return getFunctionByFilter().label
        }
    fun catchAny() = getFunctionByFilter().catchAny()
    var isBookmarked: Boolean
        get() {
            return getFunctionByFilter().isBookmarked
        }
        set(newVal) {
            getFunctionByFilter().isBookmarked = newVal
        }
    val catchAllHighlight: Boolean
        get() {
            return getFunctionByFilter().catchAllHighlight
        }
    val catchAllSelection: Boolean
        get() {
            return getFunctionByFilter().catchAllSelection
        }
    val catchQuestionMark: Boolean
        get() {
            return getFunctionByFilter().catchQuestionMark
        }
    var likes: Int
        get() {
            return getFunctionByFilter().likes
        }
        set(newVal) {
            getFunctionByFilter().likes = newVal
        }
    val supportHighlight: Boolean = functions.first().supportHighlight
    val supportSelection: Boolean = functions.first().supportSelection
    var isLiked: Boolean
        get() {
            return getFunctionByFilter().isLiked
        }
        set(newVal) {
            getFunctionByFilter().isLiked = newVal
        }
    var intent: String = ""
    var entryName: String = ""
}