package com.smallcloud.codify.settings

import com.intellij.ui.JBSplitter
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


/**
 * Supports creating and managing a [JPanel] for the Settings Dialog.
 */


class GhostText(textfield: JTextField, ghostText: String) : FocusListener,
    DocumentListener, PropertyChangeListener {
    private val textfield: JTextField
    private var isEmpty = false
    private var ghostColor: Color
    private var foregroundColor: Color? = null
    private val ghostText: String

    init {
        this.textfield = textfield
        this.ghostText = ghostText
        ghostColor = Color.LIGHT_GRAY
        textfield.addFocusListener(this)
        registerListeners()
        updateState()
        if (!this.textfield.hasFocus()) {
            focusLost(null)
        }
    }

    fun delete() {
        unregisterListeners()
        textfield.removeFocusListener(this)
    }

    private fun registerListeners() {
        textfield.document.addDocumentListener(this)
        textfield.addPropertyChangeListener("foreground", this)
    }

    private fun unregisterListeners() {
        textfield.document.removeDocumentListener(this)
        textfield.removePropertyChangeListener("foreground", this)
    }

    fun getGhostColor(): Color {
        return ghostColor
    }

    fun setGhostColor(ghostColor: Color) {
        this.ghostColor = ghostColor
    }

    private fun updateState() {
        isEmpty = textfield.text.isEmpty()
        foregroundColor = textfield.foreground
    }

    override fun focusGained(e: FocusEvent?) {
        if (isEmpty) {
            unregisterListeners()
            try {
                textfield.text = ""
                textfield.foreground = foregroundColor
            } finally {
                registerListeners()
            }
        }
    }

    override fun focusLost(e: FocusEvent?) {
        if (isEmpty) {
            unregisterListeners()
            try {
                textfield.text = ghostText
                textfield.foreground = ghostColor
            } finally {
                registerListeners()
            }
        }
    }

    override fun propertyChange(evt: PropertyChangeEvent?) {
        updateState()
    }

    override fun changedUpdate(e: DocumentEvent?) {
        updateState()
    }

    override fun insertUpdate(e: DocumentEvent?) {
        updateState()
    }

    override fun removeUpdate(e: DocumentEvent?) {
        updateState()
    }
}


class AppSettingsComponent {
    val splitter: JBSplitter = JBSplitter(true, 0.3f)
    private val mainPanel: JPanel
    private val experimentalPanel: JPanel
    val myTokenText = JBTextField()
    private val myModelText = JBTextField()
    private val myTemperatureText = JBTextField()
    private val myContrastUrlText = JBTextField()
    private val myUseForceCompletionMode = JCheckBox()
    private val myUseMultipleFilesCompletion = JCheckBox()
    private val myUseStreamingCompletion = JCheckBox()

    init {
        mainPanel = FormBuilder.createFormBuilder().run {
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
            addLabeledComponent(JBLabel("Use force completion mode: "), myUseForceCompletionMode, 1, false)
            addComponentToRightColumn(
                JBLabel(
                    "In this mode use the `alt + /` combination to trigger the completion",
                    UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER
                ), 0
            )
            addComponentFillVertically(JPanel(), 0)
        }.panel

        experimentalPanel = FormBuilder.createFormBuilder().run {
            addLabeledComponent(JBLabel("Experimental features "), myUseMultipleFilesCompletion, 0, true)
            addLabeledComponent(JBLabel("Use multiple files completion mode: "), myUseMultipleFilesCompletion, 1, false)
            addComponentToRightColumn(
                JBLabel(
                    "In this mode plugin uses multiple files in its context",
                    UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER
                ), 0
            )
            addLabeledComponent(JBLabel("Use streaming completion mode: "), myUseStreamingCompletion, 1, false)
            addComponentToRightColumn(
                JBLabel(
                    "In this mode plugin will render the completion text as soon as it comes",
                    UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER
                ), 0
            )
            addComponentFillVertically(JPanel(), 0)
        }.panel

        splitter.firstComponent = mainPanel
        splitter.secondComponent = experimentalPanel
        splitter.isShowDividerControls = true
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

    var useForceCompletion: Boolean
        get() {
            return myUseForceCompletionMode.isSelected
        }
        set(value) {
            myUseForceCompletionMode.isSelected = value
        }

    var useMultipleFilesCompletion: Boolean
        get() {
            return myUseMultipleFilesCompletion.isSelected
        }
        set(value) {
            myUseMultipleFilesCompletion.isSelected = value
        }

    var useStreamingCompletion: Boolean
        get() {
            return myUseStreamingCompletion.isSelected
        }
        set(value) {
            myUseStreamingCompletion.isSelected = value
        }
}