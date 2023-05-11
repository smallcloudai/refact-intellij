package com.smallcloud.refactai.aitoolbox

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.smallcloud.refactai.listeners.LastEditorGetterListener
import com.smallcloud.refactai.struct.LongthinkFunctionEntry


object State {
    var entry: LongthinkFunctionEntry = LongthinkFunctionEntry()
    var currentIntent: String = ""
    var historyIndex: Int = -1
    val startPosition: LogicalPosition = LogicalPosition(0, 0)
    val finishPosition: LogicalPosition = LogicalPosition(0, 0)
    val activeFilters: MutableSet<String> = mutableSetOf()

    val activeMode: Mode
        get() {
            return if (historyIndex >= 0) {
                Mode.HISTORY
            } else {
                Mode.FILTER
            }
        }

    val editor: Editor?
        get() {
            return LastEditorGetterListener.LAST_EDITOR
        }

    val haveSelection: Boolean
        get() {
            var hasSelection = false
            ApplicationManager.getApplication().invokeAndWait {
                hasSelection = editor?.selectionModel?.hasSelection() ?: false
            }
            return hasSelection
        }
}