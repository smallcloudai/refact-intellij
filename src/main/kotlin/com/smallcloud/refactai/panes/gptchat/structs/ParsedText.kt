package com.smallcloud.refactai.panes.gptchat.structs

data class ParsedText(var rawText: String, var htmlText: String, var isCode: Boolean, var isError: Boolean = false)