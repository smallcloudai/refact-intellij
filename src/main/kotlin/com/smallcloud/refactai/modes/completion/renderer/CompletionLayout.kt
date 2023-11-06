package com.smallcloud.refactai.modes.completion.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.alsoIfNull
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.modes.completion.structs.Completion
import dev.gitlive.difflib.DiffUtils
import dev.gitlive.difflib.patch.DeltaType
import java.util.concurrent.Future

class AsyncCompletionLayout(
        private val editor: Editor
) : Disposable {
    private val renderChunkSize: Int = 1
    private val renderChunkTimeoutMs: Long = 2
    private var inlayer: AsyncInlayer? = null
    private var blockEvents: Boolean = false
    private var isUpdating: Boolean = true
    private var updateTask: Future<*>? = null
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("SMCAsyncCompletionLayout", 1)
    var rendered: Boolean = false
    private var needToRender: Boolean = true
    private var highlighted: Boolean = false
    var lastCompletionData: Completion? = null
    private var rangeHighlighters: MutableList<RangeHighlighter> = mutableListOf()

    override fun dispose() {
        dispose(true)
    }

    fun dispose(needGet: Boolean = true) {
        stopUpdate(needGet)
        ApplicationManager.getApplication().invokeLater {
            rendered = false
            needToRender = false
            highlighted = false
            blockEvents = false
            rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
            rangeHighlighters.clear()
            inlayer?.dispose()
        }
    }

    private fun stopUpdate(needGet: Boolean = true) {
        synchronized(this) {
            if (!scheduler.isShutdown) {
                scheduler.shutdownNow()
            }
            isUpdating = false
            needToRender = false
            if (updateTask == null) return
            if (needGet && (!updateTask?.isCancelled!! || !updateTask?.isDone!!)) {
                updateTask!!.get()
            }
            updateTask = null
        }
    }

    fun update(
            completionData: Completion,
            offset: Int,
            needToRender: Boolean,
            animation: Boolean
    ): Future<*>? {
        if (lastCompletionData != null &&  completionData.createdTs != lastCompletionData?.createdTs) return null
        if (!isUpdating) return null
        updateTask = scheduler.submit {
            lastCompletionData = completionData
            try {
                blockEvents = true
                editor.document.startGuardedBlockChecking()

                inlayer.alsoIfNull {
                    inlayer = AsyncInlayer(editor)
                }

                if (completionData.multiline) {
                    renderAndUpdateMultilineState(completionData, offset, needToRender, animation)
                } else {
                    renderAndUpdateState(completionData, offset, needToRender, animation)
                }
            } catch (ex: Exception) {
                dispose(false)
                throw ex
            } finally {
                editor.document.stopGuardedBlockChecking()
                blockEvents = false
            }
        }
        return updateTask
    }

    private fun renderAndUpdateState(
            completionData: Completion,
            offset: Int,
            needToRender: Boolean,
            animation: Boolean) {
        inlayer?.let { inlayer ->
            val currentLine = completionData.originalText.substring(completionData.offset)
                    .substringBefore('\n', "")
            val patch = DiffUtils.diff(currentLine.toList(), completionData.completion.toList())
            for (delta in patch.getDeltas()) {
                if (delta.type != DeltaType.INSERT) { continue }
                val currentOffset = offset + delta.source.position
                var blockText = delta.target.lines?.joinToString("") ?: ""
                val currentText = inlayer.getText(currentOffset) ?: ""
                if (blockText.startsWith(currentText)) {
                    blockText = blockText.substring(currentText.length)
                }

                inlayer.setText(currentOffset, currentText + blockText)
                val text = blockText

                if (needToRender) {
                    if (animation) {
                        for (ch in text.chunked(renderChunkSize)) {
                            if (!this.needToRender) {
                                return@let
                            }
                            ApplicationManager.getApplication().invokeLater({
                                inlayer.addText(currentOffset, ch)
                            }, { !this.needToRender })
                            Thread.sleep(renderChunkTimeoutMs)
                        }
                    } else {
                        ApplicationManager.getApplication().invokeLater({
                            inlayer.addText(currentOffset, text)
                        }, { !this.needToRender })
                    }
                    rendered = true
                } else {
                    inlayer.addTextWithoutRendering(currentOffset, text)
                }
            }
        }
    }

    private fun renderAndUpdateMultilineState(
            completionData: Completion,
            offset: Int,
            needToRender: Boolean,
            animation: Boolean) {
        inlayer?.let { inlayer ->
            var blockText = lastCompletionData!!.completion
            val currentText = inlayer.getText(offset) ?: ""
            if (blockText.startsWith(currentText)) {
                blockText = blockText.substring(currentText.length)
            }
            inlayer.setText(offset, completionData.completion)
            val text = blockText

            if (needToRender) {
                if (animation) {
                    for (ch in text.chunked(renderChunkSize)) {
                        if (!this.needToRender) {
                            return@let
                        }
                        ApplicationManager.getApplication().invokeLater({
                            inlayer.addText(offset, ch)
                        }, { !this.needToRender })
                        Thread.sleep(renderChunkTimeoutMs)
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater({
                        inlayer.addText(offset, text)
                    }, { !this.needToRender })
                }
                rendered = true
            } else {
                inlayer.addTextWithoutRendering(offset, text)
            }

        }
    }

    fun applyPreview(caret: Caret?) {
        caret ?: return
        val project = editor.project ?: return
        try {
            hide()
            stopUpdate()
            runWriteAction {
                PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@runWriteAction
                applyPreviewInternal()
            }
        } catch (e: Throwable) {
            Logger.getInstance(javaClass).warn("Failed in the processes of accepting completion", e)
        } finally {
            Disposer.dispose(this)
        }
    }

    private fun applyPreviewInternal() {
        lastCompletionData?.let { completion ->
            val startIndex = completion.offset
            var endIndex = completion.offset
            if (!completion.multiline) {
                val currentLine = completion.originalText.substring(startIndex)
                        .substringBefore('\n', "")
                endIndex = completion.offset + currentLine.length
            }
            editor.document.replaceString(startIndex, endIndex, completion.completion)
            editor.caretModel.moveToOffset(completion.offset + completion.completion.length)
        }
    }

    fun hide() {
        ApplicationManager.getApplication().invokeLater {
            if (!rendered) return@invokeLater
            rendered = false
            blockEvents = false
            rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
            rangeHighlighters.clear()
            highlighted = false
//            inlayers.forEach { it.value.hide() }
            inlayer?.hide()
//            inlayers.lastOrNull()?.hide()
        }
    }

    fun show() {
        ApplicationManager.getApplication().invokeLater {
            if (rendered) return@invokeLater
//            lastCompletionData?.let { highlight(it) }
//            inlayers.forEach { it.value.show  () }
            inlayer?.show()
//            inlayers.lastOrNull()?.show()
            rendered = true
        }
    }
}
