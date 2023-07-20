package com.smallcloud.refactai.aitoolbox

import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.struct.LongthinkFunctionVariation

private fun filterByString(source: List<LongthinkFunctionVariation>,
                           filterString: String,
                           filterBy: ((LongthinkFunctionVariation) -> Boolean)? = null): List<LongthinkFunctionVariation> {
    var realFilter = filterString.lowercase()
    while (realFilter.startsWith(" ")) {
        realFilter = realFilter.substring(1)
    }
    val realStagingFilter = Resources.stagingFilterPrefix.lowercase() + " " + realFilter
    return source.filter {
        it.label.lowercase().startsWith(realFilter) ||
                it.label.lowercase().startsWith(realStagingFilter) ||
                it.catchAny()
    }.filter { if (filterBy != null) filterBy(it) else true}
}


fun filter(source: List<LongthinkFunctionVariation>, filterStr: String, fromHL: Boolean,
           filterBy: ((LongthinkFunctionVariation) -> Boolean)? = null): List<LongthinkFunctionVariation> {
    val localFiltered = filterByString(source, filterStr, filterBy).toMutableList()
    val filtered = if (fromHL) {
        localFiltered.sortedWith(compareByDescending<LongthinkFunctionVariation> { it.isBookmarked }
                .thenByDescending { it.catchAllHighlight }
                .thenByDescending { it.catchAllSelection }
                .thenByDescending { it.catchQuestionMark }
                .thenByDescending { it.likes }
                .thenByDescending { it.supportHighlight }
        )
    } else {
        localFiltered.sortedWith(compareByDescending<LongthinkFunctionVariation> { it.isBookmarked }
                .thenByDescending { it.catchAllSelection }
                .thenByDescending { it.catchAllHighlight }
                .thenByDescending { it.catchQuestionMark }
                .thenByDescending { it.likes }
                .thenByDescending { it.supportHighlight }
        )
    }
    return filtered.ifEmpty { source }
}