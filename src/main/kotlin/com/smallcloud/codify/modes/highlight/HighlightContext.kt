package com.smallcloud.codify.modes.highlight

data class HighlightContext(
    val intend: String,
    val startOffset: Int, val endOffset: Int
)