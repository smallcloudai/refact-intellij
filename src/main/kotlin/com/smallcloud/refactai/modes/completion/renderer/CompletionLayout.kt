package com.smallcloud.refactai.modes.completion.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.alsoIfNull
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.text.findTextRange
import com.smallcloud.refactai.modes.completion.structs.Completion
import dev.gitlive.difflib.patch.AbstractDelta
import dev.gitlive.difflib.patch.DeltaType
import dev.gitlive.difflib.patch.Patch
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
            isUpdating = false
            if (updateTask == null) return
            if (needGet) {
                updateTask!!.get()
            }
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
                    inlayer = AsyncInlayer(editor)
                }

                highlight(completionData)
                if (completionData.startIndex == completionData.firstLineEndOfLineIndex) {
                    renderAndUpdateState(needToRender, animation)
                } else {
                    var deltasFirstLine = completionData.getForFirstLineEOSPatch().getDeltas()
                    if (deltasFirstLine.size == 1 &&
                            deltasFirstLine.first().type == DeltaType.INSERT &&
                            completionData.endIndex <= completionData.firstLineEndOfLineIndex) {
                        deltasFirstLine = completionData.getForFirstLinePatch().getDeltas()
                    }
                    renderAndUpdatePartialState(needToRender, animation, deltasFirstLine)
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
            needToRender: Boolean,
            animation: Boolean) {
        inlayer?.let { inlayer ->
            val offset = lastCompletionData!!.startIndex - lastCompletionData!!.leftSymbolsToRemove
            var blockText = lastCompletionData!!.completion
            val currentText = inlayer.getText(offset) ?: ""
            if (blockText.startsWith(currentText)) {
                blockText = blockText.substring(currentText.length)
            }
            val realBlockText = currentText + blockText
            if (currentText == blockText) return@let
            inlayer.setText(offset, realBlockText)
            val text = blockText

            if (needToRender) {
                if (animation) {
                    for (ch in text.chunked(renderChunkSize)) {
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


    private fun renderAndUpdatePartialState(
            needToRender: Boolean,
            animation: Boolean,
            deltasFirstLine: List<AbstractDelta<Char>>
    ) {
        for (del in deltasFirstLine.sortedBy { it.source.position }) {
            val offset = lastCompletionData!!.startIndex + del.source.position
            if (del.type == DeltaType.DELETE) {
                inlayer?.setText(offset, "")
                continue
            }

            var text = del.target.lines?.joinToString("") ?: continue
            val currentText = inlayer?.getText(offset) ?: ""
            if (currentText == text) continue
            inlayer?.setText(offset, text)

            text = text.drop(currentText.length)

            if (needToRender) {
                if (animation) {
                    for (ch in text.chunked(renderChunkSize)) {
                        ApplicationManager.getApplication().invokeLater({
                            inlayer?.addText(offset, ch)
                        }, { !this.needToRender })
                        Thread.sleep(renderChunkTimeoutMs)
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater({
                        inlayer?.addText(offset, text)
                    }, { !this.needToRender })
                }
                rendered = true
            } else {
                inlayer?.addTextWithoutRendering(offset, text)
            }
        }

        val blockText = try {
            val lines = lastCompletionData?.completion?.split('\n') ?: return
            lines.subList(1, lines.size).joinToString("\n", prefix = "\n")
        } catch (e: Exception) {
            ""
        }
        if (blockText.substring(1).isNotEmpty()) {
            inlayer?.let { inlayer ->
                val currentText = inlayer.getLastText() ?: ""
                val realBlockText = currentText + blockText
                if (currentText == blockText) return@let
                inlayer.setLastText(realBlockText)
                val text = realBlockText.drop(currentText.length)

                if (needToRender) {
                    if (animation) {
                        for (ch in text.chunked(renderChunkSize)) {
                            ApplicationManager.getApplication().invokeLater({
                                inlayer.addTextToLast(ch)
                            }, { !this.needToRender })
                            Thread.sleep(renderChunkTimeoutMs)
                        }
                    } else {
                        ApplicationManager.getApplication().invokeLater({
                            inlayer.addTextToLast(text)
                        }, { !this.needToRender })
                    }
                    rendered = true
                } else {
                    inlayer.addTextWithoutRenderingToLast(text)
                }
            }
        }
    }

    private fun highlight(completionData: Completion) {
        if (highlighted) return

        if (completionData.leftSymbolsToRemove > 0) {
            ApplicationManager.getApplication().invokeLater {
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

        var deltasFirstLine = completionData.getForFirstLineEOSPatch().getDeltas()
        if (deltasFirstLine.isEmpty()) return
        val lastDelta = deltasFirstLine.last()
        deltasFirstLine = deltasFirstLine.dropLast(1).toMutableList()
        val startIndex = completionData.startIndex

        for (del in deltasFirstLine) {
            if (del.type != DeltaType.DELETE) continue

            val hlStart = startIndex + del.source.position
            val hlFinish = hlStart + (del.source.lines?.size ?: 0)
            if (hlFinish > hlStart) {
                ApplicationManager.getApplication().invokeLater {
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
                ApplicationManager.getApplication().invokeLater {
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
            var patch: Patch<Char>
            val endOffsetForReplace: Int
            if (completion.endIndex <= completion.firstLineEndOfLineIndex) {
                patch = completion.getForFirstLinePatch()
                endOffsetForReplace = completion.endIndex - completion.leftSymbolsToRemove
            } else {
                patch = completion.getForFirstLineEOSPatch()
                endOffsetForReplace = completion.firstLineEndOfLineIndex - completion.leftSymbolsToRemove
            }

            val newline = patch.applyTo(completion.originalText.subSequence(
                    startIndex, endOffsetForReplace).toList()).joinToString("")
            editor.document.replaceString(startIndex, endOffsetForReplace, newline)

            var newEOSInLine = editor.document.text.substring(startIndex).indexOf('\n')
            if (newEOSInLine == -1) {
                newEOSInLine = editor.document.text.substring(startIndex).length
            }
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
            lastCompletionData?.let { highlight(it) }
//            inlayers.forEach { it.value.show  () }
            inlayer?.show()
//            inlayers.lastOrNull()?.show()
            rendered = true
        }
    }
}
