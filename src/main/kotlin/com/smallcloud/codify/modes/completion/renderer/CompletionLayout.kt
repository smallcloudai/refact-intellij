package com.smallcloud.codify.modes.completion.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.alsoIfNull
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.text.findTextRange
import com.smallcloud.codify.modes.completion.structs.Completion
import org.jetbrains.annotations.NotNull
import java.awt.Color
import java.util.concurrent.Future

class AsyncCompletionLayout(
    private val editor: Editor
) : Disposable {
    private val renderChunkSize: Int = 1
    private val renderChunkTimeoutMs: Long = 2
    private var inlayer: AsyncInlayer? = null
    private var blockEvents: Boolean = false
    private var textCache: String = ""
    private var isUpdating: Boolean = true
    private var updateTask: Future<*>? = null
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("CodifyAsyncCompletionLayout", 1)
    var rendered: Boolean = false
    private var needToRender: Boolean = true
    private var highlighted: Boolean = false
    var lastCompletionData: Completion? = null
    private var rangeHighlighters: MutableList<RangeHighlighter> = mutableListOf()

    override fun dispose() {
        stopUpdate()
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

    private fun stopUpdate() {
        isUpdating = false
        updateTask?.let {
            it.get()
            updateTask = null
        }
    }

    fun update(
        completionData: Completion,
        needToRender: Boolean,
        animation: Boolean
    ): Future<*>? {
        if (!isUpdating) return null
        updateTask = scheduler.submit {
            val textRange = completionData.completion.findTextRange(textCache) ?: return@submit
            val newText = completionData.completion.substring(textRange.length)
            if (newText.isEmpty()) return@submit
            textCache = completionData.completion
            lastCompletionData = completionData
            try {
                blockEvents = true
                editor.document.startGuardedBlockChecking()
                inlayer.alsoIfNull {
                    inlayer = AsyncInlayer(editor, completionData.startIndex)
                }
                highlight(completionData)
                renderAndUpdateState(needToRender, animation, newText)
            } catch (ex: Exception) {
                Disposer.dispose(this)
                throw ex
            } finally {
                editor.document.stopGuardedBlockChecking()
                blockEvents = false
            }
        }
        return updateTask
    }

    private fun renderAndUpdateState(
        needToRender: Boolean,
        animation: Boolean,
        text: String
    ) {
        if (needToRender) {
            if (animation) {
                for (ch in text.chunked(renderChunkSize)) {
                    ApplicationManager.getApplication().invokeLater({
                        inlayer?.addText(ch)
                    }, { !this.needToRender })
                    Thread.sleep(renderChunkTimeoutMs)
                }
            } else {
                ApplicationManager.getApplication().invokeLater({
                    inlayer?.addText(text)
                }, { !this.needToRender })
            }
            rendered = true
        } else {
            inlayer?.addTextWithoutRendering(text)
        }
    }

    private fun highlight(completionData: Completion) {
        if (highlighted) return

        if (completionData.firstLineEndIndex > completionData.startIndex ||
            completionData.leftSymbolsToRemove > 0) {
            ApplicationManager.getApplication().invokeAndWait {
                if (completionData.firstLineEndIndex > completionData.startIndex) {
                    rangeHighlighters.add(
                        editor.markupModel.addRangeHighlighter(
                            completionData.startIndex,
                            completionData.firstLineEndIndex,
                            99999,
                            TextAttributes().apply {
                                backgroundColor = Color(200, 0, 0, 80)
                            },
                            HighlighterTargetArea.EXACT_RANGE
                        )
                    )
                }
                if (completionData.leftSymbolsToRemove > 0) {
                    rangeHighlighters.add(
                        editor.markupModel.addRangeHighlighter(
                            completionData.startIndex - completionData.leftSymbolsToRemove - completionData.leftSymbolsToSkip,
                            completionData.startIndex - completionData.leftSymbolsToSkip,
                            99999,
                            TextAttributes().apply {
                                backgroundColor = Color(200, 0, 0, 80)
                            },
                            HighlighterTargetArea.EXACT_RANGE
                        )
                    )
                }
            }
            highlighted = true
        }
    }

    fun applyPreview(caret: Caret?) {
        caret ?: return
        val project = editor.project ?: return
        try {
            hide()
            stopUpdate()
            ApplicationManager.getApplication().invokeAndWait{
                PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@invokeAndWait
                applyPreviewInternal()
            }
        } catch (e: Throwable) {
            Logger.getInstance(javaClass).warn("Failed in the processes of accepting completion", e)
        } finally {
            Disposer.dispose(this)
        }
    }

    private fun applyPreviewInternal() {
        lastCompletionData?.let {
            var startIndex = it.startIndex
            var firstLineEndIndex = it.firstLineEndIndex
            if (it.leftSymbolsToRemove > 0) {
                editor.document.replaceString(
                    startIndex - it.leftSymbolsToRemove - it.leftSymbolsToSkip,
                    startIndex - it.leftSymbolsToSkip,
                    ""
                )
                startIndex -= it.leftSymbolsToRemove
                firstLineEndIndex -= it.leftSymbolsToRemove
                editor.caretModel.moveToOffset(startIndex)
            }

            val lines = it.completion.split('\n')
            var firstLine = lines.first()
            editor.document.replaceString(startIndex, firstLineEndIndex, firstLine)
            val firstEosIndex = editor.document.text.substring(firstLineEndIndex).indexOfFirst { s -> s == '\n' }
            editor.caretModel.moveToOffset(startIndex + firstEosIndex)
            if (it.multiline) {
                startIndex += firstEosIndex
                val residual = lines.subList(1, lines.size).joinToString("\n", prefix = "\n")
                editor.document.replaceString(startIndex, startIndex, residual)
                editor.caretModel.moveToOffset(startIndex + residual.length)
            }
        }
    }

    fun hide() {
        ApplicationManager.getApplication().invokeAndWait{
            if (!rendered) return@invokeAndWait
            rendered = false
            blockEvents = false
            rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
            rangeHighlighters.clear()
            highlighted = false
            inlayer?.hide()
        }
    }

    fun show() {
        ApplicationManager.getApplication().invokeAndWait {
            if (rendered) return@invokeAndWait
            lastCompletionData?.let { highlight(it) }
            inlayer?.show()
            rendered = true
        }
    }
}
