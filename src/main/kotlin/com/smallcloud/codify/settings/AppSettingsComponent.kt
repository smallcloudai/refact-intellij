package com.smallcloud.codify.settings

import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Supports creating and managing a [JPanel] for the Settings Dialog.
 */
class AppSettingsComponent {
    val panel: JPanel
    private val myTokenText = JBTextField()
    private val myModelText = JBTextField()
    private val myTemperatureText = JBTextField()

    init {
        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Enter your token: "), myTokenText, 1, false)
                .addLabeledComponent(JBLabel("Enter model: "), myModelText, 1, false)
                .addLabeledComponent(JBLabel("Enter temperature: "), myTemperatureText, 1, false)
                .addComponentFillVertically(JPanel(), 0)
                .panel
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
    var temperatureValue: Float
        get() {
            if (myTemperatureText.text == "") return 0.0f
            return myTemperatureText.text.toFloat()
        }
        set(newVal) {
            myTemperatureText.text = newVal.toString()
        }
}