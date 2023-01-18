package com.smallcloud.codify.modes.highlight

data class HighlightContext(
    val intent: String,
    val startOffset: Int, val endOffset: Int
)