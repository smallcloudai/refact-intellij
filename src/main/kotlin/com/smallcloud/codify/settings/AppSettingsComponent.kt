package com.smallcloud.codify.settings

import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.smallcloud.codify.io.check_login
import com.smallcloud.codify.io.login
import javax.swing.JButton
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
    private val myCotrastUrlText = JBTextField()
    private val loginButton = JButton("Log In")

    init {
        loginButton.addActionListener { login() }
    }

    init {
        panel = FormBuilder.createFormBuilder()
                .addLabeledComponent(JBLabel("Enter your token: "), myTokenText, 1, false)
                .addLabeledComponent(JBLabel("Enter model: "), myModelText, 1, false)
                .addLabeledComponent(JBLabel("Enter temperature: "), myTemperatureText, 1, false)
                .addLabeledComponent(JBLabel("Contrast url: "), myCotrastUrlText, 1, false)
                .addComponent(loginButton)
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
    var contrastUrlText: String
        get() =  myCotrastUrlText.text
        set(newText) {
            myCotrastUrlText.text = newText
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