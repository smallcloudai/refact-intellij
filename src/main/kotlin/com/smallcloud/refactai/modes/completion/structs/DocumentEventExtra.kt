package com.smallcloud.refactai.modes.completion.structs

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import java.lang.System.currentTimeMillis

data class DocumentEventExtra(
    val event: DocumentEvent?,
    val editor: Editor,
    val ts: Long,
    val force: Boolean = false,
    val offsetCorrection: Int = 0
) {
    companion object {
        fun empty(editor: Editor): DocumentEventExtra {
            return DocumentEventExtra(
                null, editor,  currentTimeMillis()
            )
        }
    }
}
