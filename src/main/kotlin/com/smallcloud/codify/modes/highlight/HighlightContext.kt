package com.smallcloud.codify.modes.highlight

import com.smallcloud.codify.modes.diff.DiffIntendEntry

data class HighlightContext(
    val entry: DiffIntendEntry,
    val startOffset: Int, val endOffset: Int
)