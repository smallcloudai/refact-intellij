package com.smallcloud.refactai.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import com.smallcloud.refactai.PluginState
import java.util.concurrent.ScheduledFuture


interface SelectionChangedNotifier {
    fun isSelectionChanged(isSelection: Boolean) {}
    fun isEditorChanged(editor: Editor?) {}

    companion object {
        val TOPIC = Topic.create("Selection Changed Notifier", SelectionChangedNotifier::class.java)
    }
}

class GlobalSelectionListener : SelectionListener {
    private var lastTask: ScheduledFuture<*>? = null
    override fun selectionChanged(e: SelectionEvent) {
        val isSelection = e.newRange.length > 0
        lastTask?.cancel(true)
        lastTask = AppExecutorUtil.getAppScheduledExecutorService().schedule({
            ApplicationManager.getApplication().messageBus
                    .syncPublisher(SelectionChangedNotifier.TOPIC)
                    .isSelectionChanged(isSelection)

        }, 500, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
}

class LastEditorGetterListener : EditorFactoryListener, FileEditorManagerListener {
    private val selectorListener = GlobalSelectionListener()

    init {
        ApplicationManager.getApplication()
                .messageBus.connect(PluginState.instance)
                .subscribe<FileEditorManagerListener>(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
        instance = this
    }

    private fun setup(editor: Editor) {
        LAST_EDITOR = editor
        LAST_EDITOR!!.selectionModel.addSelectionListener(selectorListener, PluginState.instance)
    }

    private fun getVirtualFile(editor: Editor): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(editor.document)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val editor = event.newFile?.let {
            EditorFactory.getInstance().allEditors.firstOrNull { getVirtualFile(it) == event.newFile }
        }
        LAST_EDITOR = editor
        ApplicationManager.getApplication().messageBus
                .syncPublisher(SelectionChangedNotifier.TOPIC)
                .isEditorChanged(LAST_EDITOR)
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
