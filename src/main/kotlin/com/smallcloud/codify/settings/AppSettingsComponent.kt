package com.smallcloud.codify.settings

import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import javax.swing.*

/**
 * Supports creating and managing a [JPanel] for the Settings Dialog.
 */
class AppSettingsComponent {
    val panel: JPanel
    private val myTokenText = JBTextField()
    private val myModelText = JBTextField()
    private val myTemperatureText = JBTextField()
    private val myContrastUrlText = JBTextField()

    init {
        panel = FormBuilder.createFormBuilder().run {
            addLabeledComponent(JBLabel("Secret API Key: "), myTokenText, 1, false)
            addLabeledComponent(JBLabel("Model: "), myModelText, 1, false)
            addComponentToRightColumn(
                JBLabel(
                    "Leave empty if not sure", UIUtil.ComponentStyle.SMALL,
                    UIUtil.FontColor.BRIGHTER
                ), 0
            )
            addLabeledComponent(JBLabel("Temperature: "), myTemperatureText, 1, false)
            addComponentToRightColumn(
                JBLabel(
                    "Leave empty if not sure", UIUtil.ComponentStyle.SMALL,
                    UIUtil.FontColor.BRIGHTER
                ), 0
            )
            addLabeledComponent(JBLabel("Inference URL: "), myContrastUrlText, 1, false)
            addComponentToRightColumn(
                JBLabel(
                    "Fill this if you are using your own inference server",
                    UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER
                ), 0
            )
            addComponentFillVertically(JPanel(), 0)
        }.panel
    }

    val preferredFocusedComponent: JComponent
        get() = myTokenText

    var tokenText: String
        get() = myTokenText.text
        set(newText) {
            myTokenText.text = newText
        }

    var modelText: String
        get() = myModelText.text
        set(newText) {
            myModelText.text = newText
        }

    var contrastUrlText: String
        get() = myContrastUrlText.text
        set(newText) {
            myContrastUrlText.text = newText
        }

    var temperatureText: String
        get() {
            return myTemperatureText.text
        }
        set(newText) {
            myTemperatureText.text = newText
        }
}