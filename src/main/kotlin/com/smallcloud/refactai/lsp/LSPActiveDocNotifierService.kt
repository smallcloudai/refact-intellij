package com.smallcloud.refactai.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.smallcloud.refactai.listeners.LastEditorGetterListener
import com.smallcloud.refactai.listeners.SelectionChangedNotifier

class LSPActiveDocNotifierService(val project: Project): Disposable {
    init {
        if (LastEditorGetterListener.LAST_EDITOR != null) {
            lspSetActiveDocument(LastEditorGetterListener.LAST_EDITOR!!)
        }

        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(SelectionChangedNotifier.TOPIC, object : SelectionChangedNotifier {
            override fun isEditorChanged(editor: Editor?) {
                if (editor == null) return
                lspSetActiveDocument(editor)
            }
        })
    }

    override fun dispose() {}
}