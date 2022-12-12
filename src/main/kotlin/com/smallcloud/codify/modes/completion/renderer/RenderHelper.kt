package com.smallcloud.codify.modes.completion.renderer

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import java.awt.Color
import java.awt.Font
import java.awt.font.TextAttribute

object RenderHelper {
    fun getFont(editor: Editor, deprecated: Boolean): Font {
        val font = editor.colorsScheme.getFont(EditorFontType.ITALIC)
        if (!deprecated) {
            return font
        }
        val attributes: MutableMap<TextAttribute, Any?> = HashMap(font.attributes)
        attributes[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
        return Font(attributes)
    }

    val color: Color
        get() {
            return Color.gray
        }
}
