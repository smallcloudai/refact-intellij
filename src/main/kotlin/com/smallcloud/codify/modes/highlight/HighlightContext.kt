package com.smallcloud.codify.modes.highlight

import com.smallcloud.codify.modes.diff.DiffIntentEntry

data class HighlightContext(
    val entry: DiffIntentEntry,
    val startOffset: Int, val endOffset: Int
)