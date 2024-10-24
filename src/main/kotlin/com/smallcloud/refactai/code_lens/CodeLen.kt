package com.smallcloud.refactai.code_lens

import com.intellij.openapi.util.Key

data class CodeLen(val line: Int, val labelToAction: List<Pair<String, CodeLensAction>>)

val refactCodeLensEditorKey = Key.create<CodeLensLayout>("refact.editor.codeLens")
