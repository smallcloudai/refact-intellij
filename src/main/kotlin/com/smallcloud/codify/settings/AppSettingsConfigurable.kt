package com.smallcloud.codify.settings

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent


/**
 * Provides controller functionality for application settings.
 */
class AppSettingsConfigurable : Configurable {
    private var mySettingsComponent: AppSettingsComponent? = null

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP
    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String? {
        return "Codify"
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return mySettingsComponent!!.preferredFocusedComponent
    }

    override fun createComponent(): JComponent? {
        mySettingsComponent = AppSettingsComponent()
        return mySettingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        val settings: AppSettingsState = AppSettingsState.instance
        var modified = mySettingsComponent!!.tokenText != settings.token
        modified = modified or (mySettingsComponent!!.modelText != settings.model)
        modified = modified or (mySettingsComponent!!.temperatureValue != settings.temperature)
        return modified
    }

    override fun apply() {
        val settings: AppSettingsState = AppSettingsState.instance
        settings.token = mySettingsComponent!!.tokenText
        settings.model = mySettingsComponent!!.modelText
        settings.temperature = mySettingsComponent!!.temperatureValue
    }

    override fun reset() {
        val settings: AppSettingsState = AppSettingsState.instance
        mySettingsComponent!!.tokenText = settings.token
        mySettingsComponent!!.modelText = settings.model
        mySettingsComponent!!.temperatureValue = settings.temperature
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}