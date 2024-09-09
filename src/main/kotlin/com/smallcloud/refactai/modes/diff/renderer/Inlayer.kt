package com.smallcloud.refactai.modes.diff.renderer

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.findTextRange
import com.smallcloud.refactai.listeners.CancelPressedAction
import com.smallcloud.refactai.listeners.TabPressedAction
import com.smallcloud.refactai.modes.diff.getOffsetFromStringNumber
import dev.gitlive.difflib.DiffUtils
import dev.gitlive.difflib.patch.DeltaType
import dev.gitlive.difflib.patch.Patch


class Inlayer(val editor: Editor, private val intent: String) : Disposable {
    private var inlays: MutableList<Inlay<*>> = mutableListOf()
    private var renderers: MutableList<PanelRenderer> = mutableListOf()
    private var rangeHighlighters: MutableList<RangeHighlighter> = mutableListOf()
    private fun getOffsetFromStringNumber(stringNumber: Int, column: Int = 0): Int {
        return getOffsetFromStringNumber(editor, stringNumber, column)
    }

    override fun dispose() {
        inlays.forEach { it.dispose() }
        inlays.clear()

        renderers.forEach { it.dispose() }
        renderers.clear()

        rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
        rangeHighlighters.clear()
    }

    private fun renderInsertBlock(lines: List<String>, smallPatches: List<Patch<Char>>, offset: Int) {
        val logicalPosition = editor.offsetToLogicalPosition(offset)
        val renderer = InsertBlockElementRenderer(editor, lines, smallPatches, false)
        val isAbove = (logicalPosition.line < 1)
        val element = editor
            .inlayModel
            .addBlockElement(offset, false, isAbove, if (isAbove) 1 else 998, renderer)
        element?.let {
            Disposer.register(this, it)
            inlays.add(element)
        }
    }

    private fun findAlignment(str: String): String {
        val splited = str.split(Regex("\\s+"))
        if (splited.size < 2 || splited[0] != "") return ""
        return str.substring(0, str.findTextRange(splited[1])!!.startOffset)
    }

    // here
    private fun renderPanel(msg: String, offset: Int) {
        val logicalPosition = editor.offsetToLogicalPosition(offset)
        val alignment = findAlignment(
            editor.document.getText(
                TextRange(
                    editor.document.getLineStartOffset(logicalPosition.line),
                    editor.document.getLineEndOffset(logicalPosition.line)
                )
            )
        )
        val firstSymbolPos =
            editor.offsetToXY(editor.document.getLineStartOffset(logicalPosition.line) + alignment.length)
        val context = DataManager.getInstance().getDataContext(editor.contentComponent)
        val renderer = PanelRenderer(firstSymbolPos, editor, listOf(
            "${getAcceptSymbol()} Approve (Tab)" to { TabPressedAction().actionPerformed(editor, context) },
            "${getRejectSymbol()} Reject (ESC)" to { CancelPressedAction().actionPerformed(editor, context) },
//            "${getRerunSymbol()} Rerun \"${msg}\" (F1)" to {
//                val action = AIToolboxInvokeAction()
//                val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context)
//                ActionUtil.performActionDumbAwareWithCallbacks(action, event)
//            }
        ))
        editor.inlayModel
            .addBlockElement(offset, false, true, 1, renderer)
            ?.also {
                Disposer.register(this, it)
                Disposer.register(it, renderer)
                inlays.add(it)
                renderers.add(renderer)
            }
    }

    fun update(patch: Patch<String>): Inlayer {
        val sortedDeltas = patch.getDeltas().sortedBy { it.source.position }
        val offset: Int = if (sortedDeltas.isNotEmpty()) {
            if (sortedDeltas.first().type == DeltaType.INSERT) {
                getOffsetFromStringNumber(sortedDeltas.first().source.position - 1, column = 0)
            } else {
                getOffsetFromStringNumber(sortedDeltas.first().source.position, column = 0)
            }
        } else {
            editor.selectionModel.selectionStart
        }
        editor.caretModel.moveToOffset(offset)
        for (det in sortedDeltas) {
            if (det.target.lines == null) continue
            when (det.type) {
                DeltaType.INSERT -> {
                    renderInsertBlock(
                        det.target.lines!!, emptyList(),
                        getOffsetFromStringNumber(det.source.position + det.source.size() - 1)
                    )
                }

                DeltaType.CHANGE -> {
                    rangeHighlighters.add(
                        editor.markupModel.addRangeHighlighter(
                            getOffsetFromStringNumber(det.source.position),
                            getOffsetFromStringNumber(det.source.position + det.source.size()),
                            99999,
                            TextAttributes().apply {
                                backgroundColor = redColor
                            },
                            HighlighterTargetArea.EXACT_RANGE
                        )
                    )
                    val smallPatches: MutableList<Patch<Char>> = emptyList<Patch<Char>>().toMutableList()
                    for (i in 0 until minOf(det.target.size(), det.source.size())) {
                        val srcLine = det.source.lines?.get(i)
                        val tgtLine = det.target.lines!![i]
                        if (srcLine == null) break

                        val smallPatch = DiffUtils.diff(srcLine.toList(), tgtLine.toList())
                        smallPatches.add(smallPatch)
                        for (smallDelta in smallPatch.getDeltas()) {
                            rangeHighlighters.add(
                                editor.markupModel.addRangeHighlighter(
                                    getOffsetFromStringNumber(det.source.position + i, smallDelta.source.position),
                                    getOffsetFromStringNumber(
                                        det.source.position + i,
                                        smallDelta.source.position + smallDelta.source.size()
                                    ),
                                    99999,
                                    TextAttributes().apply {
                                        backgroundColor = veryRedColor
                                    },
                                    HighlighterTargetArea.EXACT_RANGE
                                )
                            )
                        }
                    }

                    renderInsertBlock(
                        det.target.lines!!,
                        smallPatches,
                        getOffsetFromStringNumber(det.source.position + det.source.size() - 1)
                    )
                }

                DeltaType.DELETE -> {
                    rangeHighlighters.add(
                        editor.markupModel.addRangeHighlighter(
                            getOffsetFromStringNumber(det.source.position),
                            getOffsetFromStringNumber(det.source.position + det.source.size()),
                            99999,
                            TextAttributes().apply {
                                backgroundColor = redColor
                            },
                            HighlighterTargetArea.EXACT_RANGE
                        )
                    )
                }

                else -> {}
            }
        }
        renderPanel(intent, offset)
        return this
    }

    private fun getAcceptSymbol(): String {
        return when(System.getProperty("os.name")) {
            "Mac OS X" -> "\uD83D\uDC4D"
            else -> "✓"
        }
    }

    private fun getRejectSymbol(): String {
        return when(System.getProperty("os.name")) {
            "Mac OS X" -> "\uD83D\uDC4E"
            else -> "×"
        }
    }

    private fun getRerunSymbol(): String {
        return when(System.getProperty("os.name")) {
            "Mac OS X" -> "\uD83D\uDD03"
            else -> "↻"
        }
    }
}