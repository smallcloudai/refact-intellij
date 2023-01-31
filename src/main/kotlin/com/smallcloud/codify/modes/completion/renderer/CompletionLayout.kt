package com.smallcloud.codify.modes.completion.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.alsoIfNull
import com.intellij.util.text.findTextRange
import com.smallcloud.codify.modes.completion.structs.Completion
import org.jetbrains.annotations.NotNull
import java.awt.Color

class AsyncCompletionLayout(
    private val editor: Editor
) : Disposable {
    private val renderChunkSize: Int = 1
    private val renderChunkTimeoutMs: Long = 2
    private var inlayer: AsyncInlayer? = null
    private var blockEvents: Boolean = false
    private var textCache: String = ""
    var rendered: Boolean = false
    var highlighted: Boolean = false
    var lastCompletionData: Completion? = null
    private var rangeHighlighters: MutableList<RangeHighlighter> = mutableListOf()

    override fun dispose() {
        ApplicationManager.getApplication().invokeAndWait {
            rendered = false
            highlighted = false
            blockEvents = false
            rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
            rangeHighlighters.clear()
            inlayer?.dispose()
        }
    }

    fun update(
        completionData: Completion,
        needToRender: Boolean,
        animation: Boolean
    ) {
        val textRange = completionData.visualizedCompletion.findTextRange(textCache) ?: return
        val newText = completionData.visualizedCompletion.substring(textRange.length)
        if (newText.isEmpty()) return
        textCache = completionData.visualizedCompletion
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

    private fun renderAndUpdateState(
        needToRender: Boolean,
        animation: Boolean,
        text: String
    ) {
        if (needToRender) {
            if (animation) {
                for (ch in text.chunked(renderChunkSize)) {
                    ApplicationManager.getApplication().invokeLater {
                        inlayer?.addText(ch)
                    }
                    Thread.sleep(renderChunkTimeoutMs)
                }
            } else {
                ApplicationManager.getApplication().invokeAndWait {
                    inlayer?.addText(text)
                }
            }
            rendered = true
        } else {
            inlayer?.addTextWithoutRendering(text)
        }
    }

    private fun highlight(completionData: Completion) {
        if (highlighted) return

        if (completionData.visualizedEndIndex > completionData.startIndex) {
            ApplicationManager.getApplication().invokeAndWait {
                rangeHighlighters.add(
                    editor.markupModel.addRangeHighlighter(
                        completionData.startIndex,
                        completionData.visualizedEndIndex,
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

    fun applyPreview(caret: Caret?) {
        caret ?: return
        val project = editor.project ?: return
        try {
            ApplicationManager.getApplication().invokeAndWait {
                val file: PsiFile =
                    PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@invokeAndWait
                applyPreviewInternal(caret.offset, project, file)
            }
        } catch (e: Throwable) {
            Logger.getInstance(javaClass).warn("Failed in the processes of accepting completion", e)
        } finally {
            Disposer.dispose(this)
        }
    }

    private fun applyPreviewInternal(@NotNull cursorOffset: Int, project: Project, file: PsiFile) {
        lastCompletionData?.let {
            editor.document.replaceString(it.startIndex, it.realCompletionIndex, it.realCompletion)
            editor.caretModel.moveToOffset(it.startIndex + it.realCompletion.length)
        }
    }

    fun hide() {
        ApplicationManager.getApplication().invokeAndWait {
            if (!rendered) return@invokeAndWait
            rendered = false
            blockEvents = false
            rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
            rangeHighlighters.clear()
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
