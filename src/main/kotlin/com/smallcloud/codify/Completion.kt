//package com.smallcloud.codify
//
//import com.intellij.codeInsight.completion.*
//import com.intellij.codeInsight.lookup.LookupElementBuilder
//import com.intellij.openapi.editor.Document
//import com.intellij.openapi.editor.Editor
//import com.intellij.openapi.editor.event.DocumentEvent
//import com.intellij.openapi.editor.event.DocumentListener
//import com.intellij.openapi.fileEditor.FileEditorManager
//import com.intellij.openapi.project.ProjectManager
//import com.intellij.patterns.ElementPattern
//import com.intellij.patterns.PlatformPatterns
//import com.intellij.psi.*
//import com.intellij.util.ProcessingContext
//import com.smallcloud.codify.io.fetch
//import com.intellij.openapi.application.ApplicationManager
//abstract class MyCompletionProvider : CompletionProvider<CompletionParameters>() {
//    abstract val context: ElementPattern<out PsiElement>
//    open val type: CompletionType = CompletionType.BASIC
//}
//
//
//fun indexOfDifference(str1: String?, str2: String?): Int {
//    if (str1 === str2) {
//        return -1
//    }
//    if (str1 == null || str2 == null) {
//        return 0
//    }
//    var i: Int
//    i = 0
//    while (i < str1.length && i < str2.length) {
//        if (str1[i] != str2[i]) {
//            break
//        }
//        ++i
//    }
//    return if (i < str2.length || i < str1.length) {
//        i
//    } else -1
//}
//
//fun difference(str1: String?, str2: String?): String? {
//    if (str1 == null) {
//        return str2
//    }
//    if (str2 == null) {
//        return str1
//    }
//    val at = indexOfDifference(str1, str2)
//    return if (at == -1) {
//        ""
//    } else str2.substring(at)
//}
//
//object FileScopeCompletionProvider : MyCompletionProvider() {
//    override val context: ElementPattern<PsiElement>
//        get() = PlatformPatterns.psiElement()
//
//    override fun addCompletions(
//            parameters: CompletionParameters,
//            processingContext: ProcessingContext,
//            result: CompletionResultSet
//    ) {
//        val editor = parameters.editor
//        val text = editor.document.text
//        val offset = parameters.offset
////        val document = event.document // current document url
////        val fileName = getActiveEditor(document) ?: return // current editor url
////        val text = document.text // get the text of the document
////        val offset = event.offset // from 0 to text.length
//        val maxTokens = 50
//        val maxEdits = 1
//        val project = ProjectManager.getInstance().openProjects.firstOrNull()
//
//        val file_name = "test.py"
//        val out = fetch("",
//                mapOf(file_name to text),
//                "Infill",
//                "infill",
//                file_name,
//                offset,
//                offset,
//                maxTokens,
//                maxEdits,
//                listOf("\n\n", "\n")
//                )
//
//        val modif_doc = out.choices[0].files[file_name]
//        val diff = difference(text, modif_doc) ?: ""
//        val new_text = diff.substringBefore("\n")
//        var elem = PrioritizedLookupElement
//                .withPriority(LookupElementBuilder.create(new_text), 10000.0)
//        elem = PrioritizedLookupElement.withExplicitProximity(elem, 0)
//        result.addElement(elem)
//    }
//}
//class MyCompletionContributor : CompletionContributor() {
//    private val providers = listOf(
//            FileScopeCompletionProvider
//    )
//
//    init {
//        providers.forEach { extend(it) }
//    }
//
//    private fun extend(provider: MyCompletionProvider) {
//        extend(provider.type, provider.context, provider)
//    }
//}
//
//
//class Completion : DocumentListener {
//    override fun documentChanged(event: DocumentEvent) {
//        val document = event.document // current document url
//        val fileName = getActiveEditor(document) ?: return // current editor url
//        val text = document.text // get the text of the document
//        val offset = event.offset // from 0 to text.length
//        val maxTokens = 50
//        val maxEdits = 1
//        val project = ProjectManager.getInstance().openProjects.firstOrNull()
////        val obj = mapOf(
////            "filename" to fileName,
////            "text" to text,
////            "cursor" to offset,
////            "maxTokens" to maxTokens,
////            "maxEdits" to maxEdits
////        )
//        val file_name = "test.py"
////        val out = fetch("",
////                mapOf(file_name to text),
////                "Infill",
////                "infill",
////                file_name,
////                offset,
////                offset,
////                maxTokens,
////                maxEdits,
////                listOf("\n\n")
////                )
////        val modif_doc = out.choices[0].files[file_name]
//
//
////        println(obj["text"])
////        val sources = {};
////        sources[fileName] = whole_doc;
//
//
//        //                 sources,
//        //                "Infill", // message
//        //                "infill", // api function
//        //                file_name,
//        //                cursor,
//        //                cursor,
//        //                max_tokens,
//        //                max_edits,
//        //                stop_tokens,
////        val max_tokens = 50
////        val max_edits = 1
//
//
//        // {
//        // "model":"CONTRASTcode/stable",
//        // "sources":{"oleg_zzz.py":"# blackjack game\n# by oleg\n\nimport random\nimport sys \n\n"},
//        // "intent":"Infill",
//        // "function":"infill",
//        // "cursor_file":"oleg_zzz.py",
//        // "cursor0":53,
//        // "cursor1":53,
//        // "temperature":0.2,
//        // "max_tokens":50,
//        // "max_edits":1,
//        // "stop":["\n","\n\n"]}
//
////        val editor = TabnineDocumentListener.getActiveEditor(document)
////        if (editor == null || !EditorUtils.isMainEditor(editor)) {
////            return
////        }
////        CompletionPreview.clear(editor)
////        ApplicationManager.getApplication()
////            .invokeLater {
////                val offset = (editor.caretModel.offset
////                        + if (ApplicationManager.getApplication().isUnitTestMode()) event.newLength else 0)
////                if (shouldIgnoreChange(event, editor, offset)) {
////                    return@invokeLater
////                }
////                handler.retrieveAndShowCompletion(editor, offset, DefaultCompletionAdjustment())
////            }
//    }
//
//    private fun getActiveEditor(document: Document): Editor? {
//        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null
//        return FileEditorManager.getInstance(project).selectedTextEditor
//    }
//}
//
//
////class TabnineDocumentListener : BulkAwareDocumentListener {
////    private val handler: InlineCompletionHandler = singletonOfInlineCompletionHandler()
////    private val suggestionsModeService: SuggestionsModeService = instanceOfSuggestionsModeService()
////    override fun documentChangedNonBulk(@NotNull event: DocumentEvent) {
////        val document = event.document
////        val editor = getActiveEditor(document)
////        if (editor == null || !EditorUtils.isMainEditor(editor)) {
////            return
////        }
////        CompletionPreview.clear(editor)
////        ApplicationManager.getApplication()
////            .invokeLater {
////                val offset = (editor.caretModel.offset
////                        + if (ApplicationManager.getApplication().isUnitTestMode()) event.newLength else 0)
////                if (shouldIgnoreChange(event, editor, offset)) {
////                    return@invokeLater
////                }
////                handler.retrieveAndShowCompletion(editor, offset, DefaultCompletionAdjustment())
////            }
////    }
////
////    private fun shouldIgnoreChange(event: DocumentEvent, editor: Editor, offset: Int): Boolean {
////        val document = event.document
////        if (event.newLength < 1 || !suggestionsModeService.getSuggestionMode().isInlineEnabled()) {
////            return true
////        }
////        if (editor.editorKind != EditorKind.MAIN_EDITOR
////            && !ApplicationManager.getApplication().isUnitTestMode()
////        ) {
////            return true
////        }
////        if (!checkModificationAllowed(editor) || document.getRangeGuard(offset, offset) != null) {
////            document.fireReadOnlyModificationAttempt()
////            return true
////        }
////        return if (!CompletionUtils.isValidMidlinePosition(document, offset)) {
////            true
////        } else isInTheMiddleOfWord(document, offset)
////    }
////
////    private fun isInTheMiddleOfWord(@NotNull document: Document, offset: Int): Boolean {
////        try {
////            if (DocumentUtil.isAtLineEnd(offset, document)) {
////                return false
////            }
////            val nextChar = document.getText(TextRange(offset, offset + 1))[0]
////            return Character.isLetterOrDigit(nextChar) || nextChar == '_' || nextChar == '-'
////        } catch (e: Throwable) {
////            Logger.getInstance(javaClass)
////                .debug("Could not determine if text is in the middle of word, skipping: ", e)
////        }
////        return false
////    }
////
////    companion object {
////        @Nullable
////        private fun getActiveEditor(@NotNull document: Document): Editor? {
////            if (!ApplicationManager.getApplication().isDispatchThread()) {
////                return null
////            }
////            val focusOwner: Component = IdeFocusManager.getGlobalInstance().getFocusOwner()
////            val dataContext: DataContext = DataManager.getInstance().getDataContext(focusOwner)
////            // ignore caret placing when exiting
////            var activeEditor: Editor? =
////                if (ApplicationManager.getApplication().isDisposed()) null else CommonDataKeys.EDITOR.getData(
////                    dataContext
////                )
////            if (activeEditor != null && activeEditor.document !== document) {
////                activeEditor = null
////            }
////            return activeEditor
////        }
////    }
////}