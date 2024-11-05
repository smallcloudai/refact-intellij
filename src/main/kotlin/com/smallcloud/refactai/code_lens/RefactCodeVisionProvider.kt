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
        val codeLensJson = gson.fromJson(codeLensStr, JsonObject::class.java)
        val resCodeLenses = mutableListOf<CodeLen>()
        if (customization.has("code_lens")) {
            val allCodeLenses = customization.get("code_lens").asJsonObject
            if (codeLensJson.has("code_lens")) {
                val codeLenses = codeLensJson.get("code_lens")!!.asJsonArray
                for (codeLens in codeLenses) {
                    val line1 = max(codeLens.asJsonObject.get("line1").asInt - 1, 0)
                    val line2 = max(codeLens.asJsonObject.get("line2").asInt - 1, 0)
                    val range = runReadAction {
                        return@runReadAction TextRange(
                            editor.logicalPositionToOffset(LogicalPosition(line1, 0)),
                            editor.document.getLineEndOffset(line2)
                        )
                    }
                    val value = allCodeLenses.get(commandKey).asJsonObject
                    val msgs = value.asJsonObject.get("messages").asJsonArray.map {
                        gson.fromJson(it.asJsonObject, ChatMessage::class.java)
                    }.toList()
                    val msg = msgs.find { it.role == "user" }
                    val sendImmediately = value.asJsonObject.get("auto_submit").asBoolean
                    val openNewTab = value.asJsonObject.get("new_tab")?.asBoolean ?: true
                    if (msg != null || msgs.isEmpty()) {
                        resCodeLenses.add(
                            CodeLen(
                                range,
                                value.asJsonObject.get("label").asString,
                                CodeLensAction(editor, line1, line2, msg?.content ?: "", sendImmediately, openNewTab)
                            )
                        )
                    }
                }
            }
        }

        return resCodeLenses
    }

    override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
        Logger.getInstance(RefactCodeVisionProvider::class.java).warn("computeCodeVision $commandKey start")
        val lsp = editor.project?.let { getInstance(it) } ?: return CodeVisionState.NotReady
        if (!lsp.isWorking) return CodeVisionState.NotReady

        try {
            val codeLens = getCodeLens(editor)
            val result = ArrayList<Pair<TextRange, CodeVisionEntry>>()
            Logger.getInstance(RefactCodeVisionProvider::class.java)
                .warn("computeCodeVision $commandKey ${codeLens.size}")
            for (codeLen in codeLens) {
                result.add(codeLen.range to ClickableTextCodeVisionEntry(codeLen.label, id, { event, editor ->
                    codeLen.action.actionPerformed()
                }, Resources.Icons.LOGO_12x12))
            }
            return CodeVisionState.Ready(result)
        } catch (e: Exception) {
            return CodeVisionState.NotReady
        }
    }
}