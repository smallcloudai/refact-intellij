package com.smallcloud.codify.modes.highlight

import com.smallcloud.codify.struct.LongthinkFunctionEntry

data class HighlightContext(
    val entry: LongthinkFunctionEntry,
    val startOffset: Int, val endOffset: Int
)