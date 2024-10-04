package com.smallcloud.refactai.modes.diff

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition

fun getOffsetFromStringNumber(editor: Editor, stringNumber: Int, column: Int = 0): Int {
    return editor.logicalPositionToOffset(LogicalPosition(maxOf(stringNumber, 0), column))
}