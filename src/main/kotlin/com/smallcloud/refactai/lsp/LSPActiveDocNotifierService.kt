package com.smallcloud.refactai.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.smallcloud.refactai.listeners.SelectionChangedNotifier

class LSPActiveDocNotifierService(val project: Project): Disposable {
    init {
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