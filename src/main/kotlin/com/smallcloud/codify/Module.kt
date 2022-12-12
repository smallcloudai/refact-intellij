package com.smallcloud.codify

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.modes.ModeProvider
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody


class Module {
    private val modeProvidersByEditors: List<Pair<ModeProvider, EditorEx>> = listOf()

    fun process(editor: Editor) {
        ModeProvider
    }
}