package com.smallcloud.codify

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.inline.CompletionModule
import com.smallcloud.codify.io.fetch
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.ProcessType
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody
import org.jetbrains.annotations.TestOnly
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.font.TextAttribute
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit



fun getFont(editor: Editor, deprecated: Boolean): Font {
    val font = editor.colorsScheme.getFont(EditorFontType.ITALIC)
    if (!deprecated) {
        return font
    }
    val attributes: MutableMap<TextAttribute, Any?> = HashMap(font.attributes)
    attributes[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
    return Font(attributes)
}

fun longest_string(array: List<String>) : String {
    var index = 0
    var elementLength: Int = array.get(0).length
    for (i in 1 until array.size) {
        if (array.get(i).length > elementLength) {
            index = i
            elementLength = array.get(i).length
        }
    }
    return array.get(index)
}


class SMCPlugin {
    private var modules: Map<ProcessType, Module> = mapOf(
            ProcessType.COMPLETION to CompletionModule()
    )


    fun make_request(request_data: SMCRequestBody) : SMCRequest {
        request_data.model = AppSettingsState.instance.model
        request_data.client = "jetbrains-0.0.1"
        request_data.temperature = AppSettingsState.instance.temperature
        val req = SMCRequest(request_data, AppSettingsState.instance.token)
        return req
    }

    fun process(process_type: ProcessType, request_body: SMCRequestBody, editor: Editor) {
        val request = make_request(request_body)

        val module = modules[process_type]
        if (module != null) {
            module.process(request_body, request, editor)
        }
    }

    companion object {
        var instant = SMCPlugin()
    }
}