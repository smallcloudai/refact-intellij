package com.smallcloud.codify.modes.diff.renderer

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font
import java.awt.font.TextAttribute


val greenColor = Color(0, 200, 0, 50)
val redColor = Color(200, 0, 0, 50)
val veryGreenColor = Color(0, 200, 0, 100)
val veryRedColor = Color(200, 0, 0, 100)

object RenderHelper {
    fun getFont(editor: Editor, deprecated: Boolean): Font {
        val font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        if (!deprecated) {
            return font
        }
        val attributes: MutableMap<TextAttribute, Any?> = HashMap(font.attributes)
        attributes[TextAttribute.STRIKETHROUGH] = TextAttribute.STRIKETHROUGH_ON
        return Font(attributes)
    }

    val color: Color
        get() {
            return JBColor.GRAY
        }

    val underlineColor: Color
        get() {
            return JBColor.BLUE
        }
}
