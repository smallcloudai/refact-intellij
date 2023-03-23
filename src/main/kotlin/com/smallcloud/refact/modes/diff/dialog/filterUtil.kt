package com.smallcloud.refact.modes.diff.dialog

import com.smallcloud.refact.Resources
import com.smallcloud.refact.struct.LongthinkFunctionEntry

private fun filterByString(source: List<LongthinkFunctionEntry>, filter: String): List<LongthinkFunctionEntry> {
    var realFilter = filter.lowercase()
    while (realFilter.startsWith(" ")) {
        realFilter = realFilter.substring(1)
    }
    val realStagingFilter = Resources.stagingFilterPrefix.lowercase() + " " + realFilter
    return source.filter {
        it.label.lowercase().startsWith(realFilter) ||
                it.label.lowercase().startsWith(realStagingFilter) ||
                it.catchAny()
    }
}


fun filter(source: List<LongthinkFunctionEntry>, filterStr: String, fromHL: Boolean): List<LongthinkFunctionEntry> {
    val localFiltered = filterByString(source, filterStr).toMutableList()
    return if (fromHL) {
        localFiltered.sortedWith(compareByDescending<LongthinkFunctionEntry> { it.isBookmarked }
                .thenByDescending { it.catchAllHighlight }
                .thenByDescending { it.catchAllSelection }
                .thenByDescending { it.catchQuestionMark }
                .thenByDescending { it.likes }
                .thenByDescending { it.supportHighlight }
        )
    } else {
        localFiltered.sortedWith(compareByDescending<LongthinkFunctionEntry> { it.isBookmarked }
                .thenByDescending { it.catchAllSelection }
                .thenByDescending { it.catchAllHighlight }
                .thenByDescending { it.catchQuestionMark }
                .thenByDescending { it.likes }
                .thenByDescending { it.supportHighlight }
        )
    }
}