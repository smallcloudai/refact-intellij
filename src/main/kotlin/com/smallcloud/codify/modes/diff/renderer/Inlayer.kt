package com.smallcloud.codify.modes.diff.renderer

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
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
import com.smallcloud.codify.listeners.CancelPressedAction
import com.smallcloud.codify.listeners.TabPressedAction
import com.smallcloud.codify.modes.diff.getOffsetFromStringNumber
import dev.gitlive.difflib.DiffUtils
import dev.gitlive.difflib.patch.DeltaType
import dev.gitlive.difflib.patch.Patch


class Inlayer(val editor: Editor) : Disposable {
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

    private fun renderPanel(msg: String, offset: Int) {
        val finalOffset = minOf(editor.selectionModel.selectionStart, offset)
        val logicalPosition = editor.offsetToLogicalPosition(finalOffset)
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
            "\uD83D\uDC4D Approve (Tab)" to { TabPressedAction.actionPerformed(editor, context) },
            "\uD83D\uDC4E Reject (ESC)" to { CancelPressedAction.actionPerformed(editor, context) },
            "Rerun \"${msg}\" (F1)" to {
                val action = ActionManager.getInstance().getAction("DiffAction")
                val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, context)
                ActionUtil.performActionDumbAwareWithCallbacks(action, event)
            }
        ))
        editor.inlayModel
            .addBlockElement(finalOffset, false, true, 1, renderer)
            ?.also {
                Disposer.register(this, it)
                Disposer.register(it, renderer)
                inlays.add(it)
                renderers.add(renderer)
            }
    }

    fun render(msg: String, patch: Patch<String>): Inlayer {
        val sortedDeltas = patch.getDeltas().sortedBy { it.source.position }
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
        renderPanel(msg, getOffsetFromStringNumber(sortedDeltas[0].source.position))
        return this
    }
}