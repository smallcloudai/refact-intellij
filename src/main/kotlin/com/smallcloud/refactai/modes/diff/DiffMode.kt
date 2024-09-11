package com.smallcloud.refactai.modes.diff

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.ex.Range
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.io.ConnectionStatus
//import com.smallcloud.refactai.io.streamedInferenceFetchOld
import com.smallcloud.refactai.modes.Mode
import com.smallcloud.refactai.modes.ModeProvider.Companion.getOrCreateModeProvider
import com.smallcloud.refactai.modes.ModeType
//import com.smallcloud.refactai.modes.completion.prompt.RequestCreatorOld
import com.smallcloud.refactai.modes.completion.structs.DocumentEventExtra
// import com.smallcloud.refactai.modes.highlight.HighlightContext
import com.smallcloud.refactai.statistic.UsageStatistic
//import com.smallcloud.refactai.struct.LongthinkFunctionEntry
//import com.smallcloud.refactai.struct.SMCRequestOld
import com.smallcloud.refactai.utils.getExtension
import dev.gitlive.difflib.DiffUtils
import dev.gitlive.difflib.patch.Patch
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

class DiffMode(
    override var needToRender: Boolean = true
) : Mode {
    private val app = ApplicationManager.getApplication()
    private var diffLayout: DiffLayout? = null
    private var processTask: Future<*>? = null
    private var renderTask: Future<*>? = null
    private var needRainbowAnimation: Boolean = false
    private var lastFromHL: Boolean = false

    private fun isProgress(): Boolean {
        return needRainbowAnimation
    }

    private fun finishRenderRainbow() {
        needRainbowAnimation = false
//        if (!renderTask?.isDone!! || !renderTask?.isCancelled!!)
//            renderTask?.get()
    }

    private fun cancel(editor: Editor?) {
        try {
            processTask?.cancel(true)
            processTask?.get()
        } catch (_: CancellationException) {
        } finally {
            if (InferenceGlobalContext.status != ConnectionStatus.DISCONNECTED &&
                InferenceGlobalContext.status != ConnectionStatus.ERROR
            ) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            }
            app.invokeLater {
                finishRenderRainbow()
                diffLayout?.cancelPreview()
                diffLayout = null
            }
            if (editor != null && !Thread.currentThread().stackTrace.any { it.methodName == "switchMode" }) {
                getOrCreateModeProvider(editor).switchMode()
            }
        }
    }

    override fun beforeDocumentChangeNonBulk(event: DocumentEventExtra) {
        cancel(event.editor)
    }

    override fun onTextChange(event: DocumentEventExtra) {
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        diffLayout?.applyPreview()
        diffLayout = null
        println("Tab Pressed")
        // getOrCreateModeProvider(editor).getDiffMode().actionPerformed(editor)
        // getOrCreateModeProvider(editor).switchMode()
//        editor.putUserData(Resources.ExtraUserDataKeys.addedFromHL, lastFromHL)
//        if (lastFromHL) {
//            getOrCreateModeProvider(editor).getHighlightMode().actionPerformed(editor)
//        } else {
//            getOrCreateModeProvider(editor).switchMode()
//        }
    }

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        cancel(editor)
        lastFromHL = false
    }

    override fun onCaretChange(event: CaretEvent) {}

    fun isInRenderState(): Boolean {
        return (diffLayout != null && !diffLayout!!.rendered) ||
                (renderTask != null && !renderTask!!.isDone && !renderTask!!.isCancelled) || isProgress()
    }

    override fun isInActiveState(): Boolean {
        return isInRenderState() ||
                (processTask != null && !processTask!!.isDone && !processTask!!.isCancelled) ||
                diffLayout != null
    }

    override fun show() {
        TODO("Not yet implemented")
    }

    override fun hide() {
        TODO("Not yet implemented")
    }

    override fun cleanup(editor: Editor) {
        cancel(editor)
    }

    fun actionPerformed(
        editor: Editor,
        content: String
    ) {

        val selectionModel = editor.selectionModel
        val startSelectionOffset: Int = selectionModel.selectionStart
        val endSelectionOffset: Int = selectionModel.selectionEnd

        selectionModel.removeSelection()
        editor.contentComponent.requestFocus()
        getOrCreateModeProvider(editor).switchMode(ModeType.Diff)
        diffLayout?.cancelPreview()
        val diff = DiffLayout(editor, content)
        val originalText = editor.document.text
        val newText = originalText.replaceRange(startSelectionOffset, endSelectionOffset, content)
        val patch = DiffUtils.diff(originalText.split("\n"), newText.split("\n"))

        diffLayout = diff.update(patch)

    }
}