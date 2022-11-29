package com.smallcloud.codify

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager

class Completion : BulkAwareDocumentListener {
    override fun documentChangedNonBulk(event: DocumentEvent) {
        val document = event.document // current document url
        val fileName = getActiveEditor(document) ?: return // current editor url
        val text = document.text // get the text of the document
        val offset = event.offset // from 0 to text.length
        val maxTokens = 50
        val maxEdits = 1

        val obj = mapOf(
            "filename" to fileName,
            "text" to text,
            "cursor" to offset,
            "maxTokens" to maxTokens,
            "maxEdits" to maxEdits
        )

//        println(obj["text"])
//        val sources = {};
//        sources[fileName] = whole_doc;


        //                 sources,
        //                "Infill", // message
        //                "infill", // api function
        //                file_name,
        //                cursor,
        //                cursor,
        //                max_tokens,
        //                max_edits,
        //                stop_tokens,
//        val max_tokens = 50
//        val max_edits = 1


    // {
    // "model":"CONTRASTcode/stable",
    // "sources":{"oleg_zzz.py":"# blackjack game\n# by oleg\n\nimport random\nimport sys \n\n"},
    // "intent":"Infill",
    // "function":"infill",
    // "cursor_file":"oleg_zzz.py",
    // "cursor0":53,
    // "cursor1":53,
    // "temperature":0.2,
    // "max_tokens":50,
    // "max_edits":1,
    // "stop":["\n","\n\n"]}

//        val editor = TabnineDocumentListener.getActiveEditor(document)
//        if (editor == null || !EditorUtils.isMainEditor(editor)) {
//            return
//        }
//        CompletionPreview.clear(editor)
//        ApplicationManager.getApplication()
//            .invokeLater {
//                val offset = (editor.caretModel.offset
//                        + if (ApplicationManager.getApplication().isUnitTestMode()) event.newLength else 0)
//                if (shouldIgnoreChange(event, editor, offset)) {
//                    return@invokeLater
//                }
//                handler.retrieveAndShowCompletion(editor, offset, DefaultCompletionAdjustment())
//            }
    }

    private fun getActiveEditor(document: Document): Editor? {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null
        return FileEditorManager.getInstance(project).selectedTextEditor
    }
}


//class TabnineDocumentListener : BulkAwareDocumentListener {
//    private val handler: InlineCompletionHandler = singletonOfInlineCompletionHandler()
//    private val suggestionsModeService: SuggestionsModeService = instanceOfSuggestionsModeService()
//    override fun documentChangedNonBulk(@NotNull event: DocumentEvent) {
//        val document = event.document
//        val editor = getActiveEditor(document)
//        if (editor == null || !EditorUtils.isMainEditor(editor)) {
//            return
//        }
//        CompletionPreview.clear(editor)
//        ApplicationManager.getApplication()
//            .invokeLater {
//                val offset = (editor.caretModel.offset
//                        + if (ApplicationManager.getApplication().isUnitTestMode()) event.newLength else 0)
//                if (shouldIgnoreChange(event, editor, offset)) {
//                    return@invokeLater
//                }
//                handler.retrieveAndShowCompletion(editor, offset, DefaultCompletionAdjustment())
//            }
//    }
//
//    private fun shouldIgnoreChange(event: DocumentEvent, editor: Editor, offset: Int): Boolean {
//        val document = event.document
//        if (event.newLength < 1 || !suggestionsModeService.getSuggestionMode().isInlineEnabled()) {
//            return true
//        }
//        if (editor.editorKind != EditorKind.MAIN_EDITOR
//            && !ApplicationManager.getApplication().isUnitTestMode()
//        ) {
//            return true
//        }
//        if (!checkModificationAllowed(editor) || document.getRangeGuard(offset, offset) != null) {
//            document.fireReadOnlyModificationAttempt()
//            return true
//        }
//        return if (!CompletionUtils.isValidMidlinePosition(document, offset)) {
//            true
//        } else isInTheMiddleOfWord(document, offset)
//    }
//
//    private fun isInTheMiddleOfWord(@NotNull document: Document, offset: Int): Boolean {
//        try {
//            if (DocumentUtil.isAtLineEnd(offset, document)) {
//                return false
//            }
//            val nextChar = document.getText(TextRange(offset, offset + 1))[0]
//            return Character.isLetterOrDigit(nextChar) || nextChar == '_' || nextChar == '-'
//        } catch (e: Throwable) {
//            Logger.getInstance(javaClass)
//                .debug("Could not determine if text is in the middle of word, skipping: ", e)
//        }
//        return false
//    }
//
//    companion object {
//        @Nullable
//        private fun getActiveEditor(@NotNull document: Document): Editor? {
//            if (!ApplicationManager.getApplication().isDispatchThread()) {
//                return null
//            }
//            val focusOwner: Component = IdeFocusManager.getGlobalInstance().getFocusOwner()
//            val dataContext: DataContext = DataManager.getInstance().getDataContext(focusOwner)
//            // ignore caret placing when exiting
//            var activeEditor: Editor? =
//                if (ApplicationManager.getApplication().isDisposed()) null else CommonDataKeys.EDITOR.getData(
//                    dataContext
//                )
//            if (activeEditor != null && activeEditor.document !== document) {
//                activeEditor = null
//            }
//            return activeEditor
//        }
//    }
//}