package com.smallcloud.codify.modes.completion.structs

data class EditorState(
    val modificationStamp: Long,
    val offset: Int,
    val text: String
)
