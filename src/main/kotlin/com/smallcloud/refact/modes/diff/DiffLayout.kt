package com.smallcloud.refact.modes.diff

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Disposer
import com.smallcloud.refact.modes.diff.renderer.Inlayer
import com.smallcloud.refact.struct.SMCRequest
import dev.gitlive.difflib.patch.DeltaType
import dev.gitlive.difflib.patch.Patch

class DiffLayout(
    private val editor: Editor,
    val request: SMCRequest,
    private val patch: Patch<String>
) : Disposable {
    private var inlayer: Inlayer? = null
    private var blockEvents: Boolean = false
    var rendered: Boolean = false

    override fun dispose() {
        rendered = false
        blockEvents = false
        inlayer?.dispose()
    }

    private fun getOffsetFromStringNumber(stringNumber: Int, column: Int = 0): Int {
        return getOffsetFromStringNumber(editor, stringNumber, column)
    }

    fun render(): DiffLayout {
        assert(!rendered) { "Already rendered" }
        try {
            blockEvents = true
            editor.document.startGuardedBlockChecking()
            inlayer = Inlayer(editor).render(request.body.intent, patch)
            rendered = true
        } catch (ex: Exception) {
            Disposer.dispose(this)
            throw ex
        } finally {
            editor.document.stopGuardedBlockChecking()
            blockEvents = false
        }
        return this
    }

    fun cancelPreview() {
        Disposer.dispose(this)
    }

    fun applyPreview() {
        try {
            applyPreviewInternal()
        } catch (e: Throwable) {
            Logger.getInstance(javaClass).warn("Failed in the processes of accepting completion", e)
        } finally {
            Disposer.dispose(this)
        }
    }

    private fun applyPreviewInternal() {
        val document = editor.document
        for (det in patch.getDeltas().sortedByDescending { it.source.position }) {
            if (det.target.lines == null) continue
            when (det.type) {
                DeltaType.INSERT -> {
                    document.insertString(
                        getOffsetFromStringNumber(det.source.position),
                        det.target.lines!!.joinToString("\n", postfix = "\n")
                    )
                }

                DeltaType.CHANGE -> {
                    document.deleteString(
                        getOffsetFromStringNumber(det.source.position),
                        getOffsetFromStringNumber(det.source.position + det.source.size())
                    )
                    document.insertString(
                        getOffsetFromStringNumber(det.source.position),
                        det.target.lines!!.joinToString("\n", postfix = "\n")
                    )
                }

                DeltaType.DELETE -> {
                    document.deleteString(
                        getOffsetFromStringNumber(det.source.position),
                        getOffsetFromStringNumber(det.source.position + det.source.size())
                    )
                }

                else -> {}
            }
        }
    }
}