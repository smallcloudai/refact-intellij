package com.smallcloud.refactai.modes.completion.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.text.findTextRange
import com.smallcloud.refactai.modes.completion.structs.Completion
import dev.gitlive.difflib.patch.AbstractDelta
import dev.gitlive.difflib.patch.DeltaType
import java.awt.Color
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class AsyncCompletionLayout(
    private val editor: Editor
) : Disposable {
    private val renderChunkSize: Int = 1
    private val renderChunkTimeoutMs: Long = 2
    private var inlayers: MutableMap<Int, AsyncInlayer> = mutableMapOf()
    private var blockEvents: Boolean = false
    private var textCache: String = ""
    private var isUpdating: Boolean = true
    private var updateTask: Future<*>? = null
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("SMCAsyncCompletionLayout", 1)
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
            inlayers.forEach { it.value.dispose() }
            inlayers.clear()
//            inlayer?.dispose()
        }
    }

    private fun stopUpdate() {
        isUpdating = false
        updateTask?.let {
            it.get(1, TimeUnit.SECONDS)
            updateTask = null
        }
    }

    private fun getDeltasForFirstLine(completionData: Completion): List<AbstractDelta<Char>> {
        val patch = completionData.getPatch()
        val deltas = patch.getDeltas().sortedBy { it.source.position }
            var endLine = false
            return deltas.filter {
                if (endLine) return@filter false
                if (it.target.lines?.contains('\n') == true) {
                    endLine = true
                    return@filter true
                }
                return@filter true
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
                val deltasFirstLine = getDeltasForFirstLine(completionData)
                for (del in deltasFirstLine) {
                    if (inlayers[del.source.position] == null) {
                        inlayers[del.source.position] = AsyncInlayer(editor,
                                completionData.startIndex + del.source.position)
                    }
                }
                synchronized(this) {
                    inlayers = inlayers.filter {
                        val tmp = deltasFirstLine.find { del -> del.source.position == it.key }
                        if (tmp == null) {
                            ApplicationManager.getApplication().invokeAndWait { it.value.dispose() }
                            return@filter false
                        }
                        return@filter true
                    } as MutableMap<Int, AsyncInlayer>
                }

                highlight(completionData)
                renderAndUpdateState(needToRender, animation, deltasFirstLine)
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
            deltasFirstLine: List<AbstractDelta<Char>>
    ) {
        val blockText = try {
            val lines = lastCompletionData?.completion?.split('\n') ?: return
            lines.subList(1, lines.size).joinToString("\n", prefix = "\n")
        } catch (e: Exception) {
            ""
        }
        for ((idx, del) in deltasFirstLine.sortedBy { it.source.position }.withIndex()) {
            if (del.type == DeltaType.DELETE) continue
            val isLast = idx == deltasFirstLine.size - 1

            val inlayer = inlayers[del.source.position] ?: continue
            var text = del.target.lines?.joinToString("") ?: continue
            text = text.split("\n").first()
            if (isLast) {
                text += blockText
            }
            val currentText = inlayer.getText()
            if (currentText == text) continue
            inlayer.setText(text)
            text = text.drop(currentText.length)
            if (!isLast) {
                text = text.split('\n').first()
            }

            if (needToRender) {
                if (animation) {
                    for (ch in text.chunked(renderChunkSize)) {
                        ApplicationManager.getApplication().invokeLater({
                            inlayer.addText(ch)
                        }, { !this.needToRender })
                        Thread.sleep(renderChunkTimeoutMs)
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater({
                        inlayer.addText(text)
                    }, { !this.needToRender })
                }
                rendered = true
            } else {
                inlayer.addTextWithoutRendering(text)
            }
        }
    }

    private fun highlight(completionData: Completion) {
        if (highlighted) return

        var deltasFirstLine = getDeltasForFirstLine(completionData)
        val lastDelta = deltasFirstLine.last()
        deltasFirstLine = deltasFirstLine.dropLast(1)
        val startIndex = completionData.startIndex

        if (completionData.leftSymbolsToRemove > 0) {
            ApplicationManager.getApplication().invokeAndWait {
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
            highlighted = true
        }

        for (del in deltasFirstLine) {
            if (del.type != DeltaType.DELETE) continue

            val hlStart = startIndex + del.source.position
            val hlFinish = hlStart + (del.source.lines?.size ?: 0)
            if (hlFinish > hlStart) {
                ApplicationManager.getApplication().invokeAndWait {
                    rangeHighlighters.add(
                            editor.markupModel.addRangeHighlighter(
                                    hlStart, hlFinish,
                                    99999,
                                    TextAttributes().apply {
                                        backgroundColor = Color(200, 0, 0, 80)
                                    },
                                    HighlighterTargetArea.EXACT_RANGE
                            )
                    )
                }
                highlighted = true
            }
        }

        val newLineIdx = lastDelta.target.lines?.indexOf('\n') ?: return
        if (newLineIdx != -1) {
            val startOffset = startIndex + lastDelta.source.position
            if (startOffset < completionData.firstLineEndOfLineIndex) {
                ApplicationManager.getApplication().invokeAndWait {
                    rangeHighlighters.add(
                            editor.markupModel.addRangeHighlighter(
                                    startOffset,
                                    completionData.firstLineEndOfLineIndex,
                                    99999,
                                    TextAttributes().apply {
                                        backgroundColor = Color(200, 0, 0, 80)
                                    },
                                    HighlighterTargetArea.EXACT_RANGE
                            )
                    )
                }
                highlighted = true
            }
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
        lastCompletionData?.let { completion ->
            var startIndex = completion.startIndex
            var firstLineEndIndex = completion.firstLineEndIndex
            if (completion.leftSymbolsToRemove > 0) {
                editor.document.replaceString(
                    startIndex - completion.leftSymbolsToRemove - completion.leftSymbolsToSkip,
                    startIndex - completion.leftSymbolsToSkip,
                    ""
                )
                startIndex -= completion.leftSymbolsToRemove
                firstLineEndIndex -= completion.leftSymbolsToRemove
                editor.caretModel.moveToOffset(startIndex)
            }

            val lines = completion.completion.split('\n')
            var patch = completion.getForFirstLineEOSPatch()

            val maxPos = patch.getDeltas().maxBy { it.target.position }
            val maxOffset = maxPos.target.position + (maxPos.target.lines?.size ?: 0)
            val endOffsetForReplace = if (patch.getDeltas().size > 1)
                completion.firstLineEndOfLineIndex - completion.leftSymbolsToRemove else firstLineEndIndex

            var needFullFirstLinePatch = completion.completion.contains('\n')

            if (patch.getDeltas().size <= 1 && !needFullFirstLinePatch) {
                patch = completion.getForFirstLinePatch()
            }

            val newline = patch.applyTo(completion.originalText.subSequence(
                    startIndex, endOffsetForReplace).toList()).joinToString("")
            editor.document.replaceString(startIndex, endOffsetForReplace, newline)

            var newEOSInLine = editor.document.text.substring(startIndex).indexOf('\n')
            newEOSInLine = if (needFullFirstLinePatch)
                if (newEOSInLine == -1) editor.document.text.length - startIndex else newEOSInLine else maxOffset
            editor.caretModel.moveToOffset(startIndex + newEOSInLine)
            if (completion.multiline) {
                startIndex += newEOSInLine
                val residual = lines.subList(1, lines.size).joinToString("\n", prefix = "\n")
                editor.document.replaceString(startIndex, startIndex, residual)
                editor.caretModel.moveToOffset(startIndex + residual.length)
            }
        }
    }

    fun hide() {
        ApplicationManager.getApplication().invokeLater{
            if (!rendered) return@invokeLater
            rendered = false
            blockEvents = false
            rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
            rangeHighlighters.clear()
            highlighted = false
            inlayers.forEach { it.value.hide() }
//            inlayers.lastOrNull()?.hide()
        }
    }

    fun show() {
        ApplicationManager.getApplication().invokeLater {
            if (rendered) return@invokeLater
            lastCompletionData?.let { highlight(it) }
            inlayers.forEach { it.value.show() }
//            inlayers.lastOrNull()?.show()
            rendered = true
        }
    }
}
