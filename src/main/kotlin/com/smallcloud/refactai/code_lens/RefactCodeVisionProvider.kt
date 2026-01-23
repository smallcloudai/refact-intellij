package com.smallcloud.refactai.code_lens

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.codeInsight.codeVision.*
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.util.TextRange
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance
import com.smallcloud.refactai.lsp.lspGetCodeLens
import com.smallcloud.refactai.struct.ChatMessage
import kotlin.math.max
import com.intellij.codeInsight.codeVision.CodeVisionBundle
data class CodeLen(
    val range: TextRange,
    val label: String,
    val action: CodeLensAction
)

fun makeIdForProvider(commandKey: String): String {
    return "refactai.codelens.$commandKey"
}

class RefactCodeVisionProvider(
    private val commandKey: String,
    private val posAfter: String?,
    private val label: String,
    private val customization: JsonObject
) :
    CodeVisionProvider<Unit> {
    override val defaultAnchor: CodeVisionAnchorKind
        get() = CodeVisionAnchorKind.Top
    override val id: String
        get() = makeIdForProvider(commandKey)
    override val name: String
        get() = "Refact.ai Hint($label)"
    override val relativeOrderings: List<CodeVisionRelativeOrdering>
        get() {
            return if (posAfter == null) {
                listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingFirst)
            } else {
                listOf(CodeVisionRelativeOrdering.CodeVisionRelativeOrderingAfter("refactai.codelens.$posAfter"))
            }
        }

    override fun precomputeOnUiThread(editor: Editor) {}

    private fun getCodeLens(editor: Editor): List<CodeLen> {
        val codeLensStr = lspGetCodeLens(editor)
        val gson = Gson()
        val codeLensJson = try {
            gson.fromJson(codeLensStr, JsonObject::class.java)
        } catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()

        val resCodeLenses = mutableListOf<CodeLen>()
        if (!customization.has("code_lens")) return resCodeLenses

        val allCodeLenses = customization.get("code_lens")
        if (allCodeLenses == null || !allCodeLenses.isJsonObject) return resCodeLenses

        if (!codeLensJson.has("code_lens")) return resCodeLenses
        val codeLenses = codeLensJson.get("code_lens")
        if (codeLenses == null || !codeLenses.isJsonArray) return resCodeLenses

        val lineCount = runReadAction { editor.document.lineCount }
        if (lineCount == 0) return resCodeLenses

        for (codeLens in codeLenses.asJsonArray) {
            try {
                val obj = codeLens.asJsonObject
                var line1 = max(obj.get("line1")?.asInt?.minus(1) ?: continue, 0).coerceAtMost(lineCount - 1)
                var line2 = max(obj.get("line2")?.asInt?.minus(1) ?: continue, 0).coerceAtMost(lineCount - 1)
                if (line2 < line1) {
                    val tmp = line1
                    line1 = line2
                    line2 = tmp
                }

                val value = allCodeLenses.asJsonObject.get(commandKey)
                if (value == null || !value.isJsonObject) continue

                val range = runReadAction {
                    val startOffset = editor.document.getLineStartOffset(line1)
                    val endOffset = editor.document.getLineEndOffset(line2)
                    TextRange(startOffset, endOffset)
                }

                val messagesJson = value.asJsonObject.get("messages")
                val msgs = if (messagesJson != null && messagesJson.isJsonArray) {
                    messagesJson.asJsonArray.mapNotNull {
                        try { gson.fromJson(it.asJsonObject, ChatMessage::class.java) } catch (_: Exception) { null }
                    }.toTypedArray()
                } else {
                    emptyArray()
                }
                val userMsg = msgs.find { it.role == "user" }

                val sendImmediately = value.asJsonObject.get("auto_submit")?.asBoolean ?: false
                val openNewTab = value.asJsonObject.get("new_tab")?.asBoolean ?: true
                val label = value.asJsonObject.get("label")?.asString ?: continue

                val isValidCodeLen = msgs.isEmpty() || userMsg != null
                if (isValidCodeLen) {
                    resCodeLenses.add(
                        CodeLen(range, label, CodeLensAction(editor, line1, line2, msgs, sendImmediately, openNewTab))
                    )
                }
            } catch (_: Exception) {
                continue
            }
        }

        return resCodeLenses
    }

    override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
//        Logger.getInstance(RefactCodeVisionProvider::class.java).warn("computeCodeVision $commandKey start")
        val lsp = editor.project?.let { getInstance(it) } ?: return CodeVisionState.NotReady
        if (!lsp.isWorking) return CodeVisionState.NotReady

        try {
            val codeLens = getCodeLens(editor)
            val result = ArrayList<Pair<TextRange, CodeVisionEntry>>()
//            Logger.getInstance(RefactCodeVisionProvider::class.java)
//                .warn("computeCodeVision $commandKey ${codeLens.size}")
            for (codeLen in codeLens) {
                result.add(codeLen.range to ClickableTextCodeVisionEntry(codeLen.label, id, { _, _ ->
                    codeLen.action.actionPerformed()
                }, Resources.Icons.LOGO_12x12))
            }
            return CodeVisionState.Ready(result)
        } catch (e: Exception) {
            return CodeVisionState.NotReady
        }
    }
}