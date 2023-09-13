package com.smallcloud.refactai.listeners

import com.intellij.openapi.Disposable
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

class LastEditorGetterListener : EditorFactoryListener, FileEditorManagerListener, Disposable {
    private val selectorListener = GlobalSelectionListener()

    init {
        ApplicationManager.getApplication()
                .messageBus.connect(this)
                .subscribe<FileEditorManagerListener>(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    }

    private fun setup(editor: Editor) {
        LAST_EDITOR = editor
        LAST_EDITOR!!.selectionModel.addSelectionListener(selectorListener, this)
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
        val editor = EditorFactory.getInstance().allEditors.first { getVirtualFile(it) == file }
        setup(editor)
    }

    override fun editorCreated(event: EditorFactoryEvent) {
//        val realEditor = event.editor.project?.let {
//            getVirtualFile(event.editor)?.let { it1 ->
//                FileEditorManager.getInstance(it).getAllEditors(it1) }
//        }?.firstOrNull()
//        if (realEditor != null) {
//            setup(event.editor)
//        }
    }

    override fun dispose() {}

    companion object {
        var LAST_EDITOR: Editor? = null
    }
}
