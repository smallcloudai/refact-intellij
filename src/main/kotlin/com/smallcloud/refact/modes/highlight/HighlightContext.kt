package com.smallcloud.refact.modes.highlight

import com.smallcloud.refact.struct.LongthinkFunctionEntry

data class HighlightContext(
    val entry: LongthinkFunctionEntry,
    val startOffset: Int, val endOffset: Int
)