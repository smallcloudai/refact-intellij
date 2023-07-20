package com.smallcloud.refactai.modes.highlight

import com.smallcloud.refactai.struct.LongthinkFunctionEntry

data class HighlightContext(
    val entry: LongthinkFunctionEntry,
    val startOffset: Int, val endOffset: Int
)