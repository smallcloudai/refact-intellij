package com.smallcloud.refactai.code_lens

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.smallcloud.refactai.code_lens.renderer.Inlayer
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance
import com.smallcloud.refactai.lsp.lspGetCodeLens
import com.smallcloud.refactai.struct.ChatMessage
import kotlin.math.max


class CodeLensLayout(private val editor: Editor): Disposable {
    private var inlayer: Inlayer? = null
    init {
        val virtualFile = getVirtualFile(editor)
        if (virtualFile != null) {
            val editorUri = virtualFile.url
            val codeLens = getCodeLens(editorUri)
//            val codeLens = mutableListOf(CodeLen(0, listOf(Pair("test", CodeLensAction(listOf("test"), editorUri)),
//                    Pair("test2", CodeLensAction(listOf("test2"), editorUri)))))
            inlayer = Inlayer(editor, codeLens)
        }
    }

    private fun getVirtualFile(editor: Editor): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(editor.document)
    }

    private fun getCodeLens(editorUri: String): List<CodeLen> {
        val codeLensStr = lspGetCodeLens(editor.project!!, editorUri)
        val gson = Gson()
        val customization = getInstance(editor.project!!).fetchCustomization()
        val codeLensJson = gson.fromJson(codeLensStr, JsonObject::class.java)
        val resCodeLenses = mutableListOf<CodeLen>()
        if (customization.has("code_lens")) {
            val allCodeLenses = customization.get("code_lens").asJsonObject
            if (codeLensJson.has("code_lens")) {
                val codeLenses = codeLensJson.get("code_lens")!!.asJsonArray
                for (codeLens in codeLenses) {
                    val line1 = max(codeLens.asJsonObject.get("line1").asInt - 1, 0)
                    val label2Action = emptyList<Pair<String, CodeLensAction>>().toMutableList()
                    allCodeLenses.entrySet().forEach { (key, value) ->
                        val msgs = value.asJsonObject.get("messages").asJsonArray.map {
                            gson.fromJson(it.asJsonObject, ChatMessage::class.java)
                        }.toList()
                        label2Action.add(Pair(value.asJsonObject.get("label").asString,
                            CodeLensAction(listOf("test2"), editorUri)))
                    }
                    resCodeLenses.add(CodeLen(line1, label2Action))
                }
            }
        }

        return resCodeLenses
    }

    override fun dispose() {
        inlayer?.dispose()
    }
}