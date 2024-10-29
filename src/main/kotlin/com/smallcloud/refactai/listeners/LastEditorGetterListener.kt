package com.smallcloud.refactai.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import com.smallcloud.refactai.PluginState


interface SelectionChangedNotifier {
    fun isEditorChanged(editor: Editor?) {}

    companion object {
        val TOPIC = Topic.create("Selection Changed Notifier", SelectionChangedNotifier::class.java)
    }
}

class LastEditorGetterListener : EditorFactoryListener, FileEditorManagerListener {
    private val focusChangeListener = object : FocusChangeListener {
        override fun focusGained(editor: Editor) {
            setEditor(editor)
        }
    }

    init {
        ApplicationManager.getApplication()
            .messageBus.connect(PluginState.instance)
            .subscribe<FileEditorManagerListener>(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        instance = this
    }

    private fun setEditor(editor: Editor) {
        if (LAST_EDITOR != editor) {
            LAST_EDITOR = editor
            ApplicationManager.getApplication().messageBus
                .syncPublisher(SelectionChangedNotifier.TOPIC)
                .isEditorChanged(editor)
        }
    }

    private fun setup(editor: Editor) {
        (editor as EditorEx).addFocusListener(focusChangeListener)
    }

    private fun getVirtualFile(editor: Editor): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(editor.document)
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val editor = EditorFactory.getInstance().allEditors.firstOrNull { getVirtualFile(it) == file }
        if (editor != null) {
            setup(editor)
        }
    }

    override fun editorCreated(event: EditorFactoryEvent) {}

    companion object {
        lateinit var instance: LastEditorGetterListener
        var LAST_EDITOR: Editor? = null
    }
}
