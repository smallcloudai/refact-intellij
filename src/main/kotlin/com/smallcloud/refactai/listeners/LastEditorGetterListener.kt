package com.smallcloud.refactai.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
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

class LastEditorGetterListener : EditorFactoryListener, SelectionListener, Disposable {
    private fun setup(editor: Editor) {
        LAST_EDITOR = editor
        LAST_EDITOR!!.selectionModel.addSelectionListener(GlobalSelectionListener(), this)
    }


    override fun editorCreated(event: EditorFactoryEvent) {
        event.editor.caretModel.addCaretListener(object: CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                LAST_EDITOR = event.editor
                ApplicationManager.getApplication().messageBus
                        .syncPublisher(SelectionChangedNotifier.TOPIC)
                        .isEditorChanged(LAST_EDITOR)
            }
            override fun caretRemoved(event: CaretEvent) {
                LAST_EDITOR = event.editor
                ApplicationManager.getApplication().messageBus
                        .syncPublisher(SelectionChangedNotifier.TOPIC)
                        .isEditorChanged(LAST_EDITOR)
            }

            override fun caretAdded(event: CaretEvent) {
                LAST_EDITOR = event.editor
                ApplicationManager.getApplication().messageBus
                        .syncPublisher(SelectionChangedNotifier.TOPIC)
                        .isEditorChanged(LAST_EDITOR)
            }
        })
        event.editor.document.addDocumentListener(object: DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                LAST_EDITOR = getActiveEditor(event.document)
                ApplicationManager.getApplication().messageBus
                        .syncPublisher(SelectionChangedNotifier.TOPIC)
                        .isEditorChanged(LAST_EDITOR)
            }
        })
        event.editor.contentComponent.addFocusListener(object: FocusListener {
            override fun focusGained(e: FocusEvent?) {
                LAST_EDITOR = event.editor
                ApplicationManager.getApplication().messageBus
                        .syncPublisher(SelectionChangedNotifier.TOPIC)
                        .isEditorChanged(LAST_EDITOR)
            }
            override fun focusLost(e: FocusEvent?) {}
        })
        setup(event.editor)
    }
    private fun getActiveEditor(document: Document): Editor? {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            return null
        }
        return EditorFactory.getInstance().getEditors(document).firstOrNull()
    }

    override fun dispose() {}
    companion object {
        var LAST_EDITOR: Editor? = null
    }
}
