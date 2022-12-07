package com.smallcloud.codify.settings

import com.intellij.openapi.options.Configurable
import com.smallcloud.codify.Resources
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

//import com.intellij.collaboration.auth.ui.
/**
 * Provides controller functionality for application settings.
 */
class AppSettingsConfigurable : Configurable {
    private var mySettingsComponent: AppSettingsComponent? = null

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP
    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String? {
        return "Settings"
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
        var modified = (mySettingsComponent!!.tokenText.isNotEmpty()
                && (settings.token == null || mySettingsComponent!!.tokenText != settings.token))
        modified = modified or (mySettingsComponent!!.tokenText.isEmpty() && settings.token != null)

        modified = modified or (mySettingsComponent!!.modelText != settings.model)

        modified = modified or (mySettingsComponent!!.temperatureText.isNotEmpty() &&
                (settings.temperature == null ||
                        mySettingsComponent!!.temperatureText.toFloat() != settings.temperature))
        modified = modified or (mySettingsComponent!!.temperatureText.isEmpty() && settings.temperature != null)

        modified = modified or (mySettingsComponent!!.contrastUrlText.isNotEmpty() &&
                (settings.contrast_url == null || mySettingsComponent!!.contrastUrlText != settings.contrast_url))
        modified = modified or (mySettingsComponent!!.contrastUrlText.isEmpty() && settings.contrast_url != null)
        return modified
    }

    override fun apply() {
        val settings: AppSettingsState = AppSettingsState.instance
        settings.token = if (mySettingsComponent!!.tokenText.isEmpty()) null else mySettingsComponent!!.tokenText
        settings.model = mySettingsComponent!!.modelText
        if (mySettingsComponent!!.temperatureText.isEmpty()) {
            settings.temperature = null
        } else {
            try {
                settings.temperature = mySettingsComponent!!.temperatureText.toFloat()
            } catch (e: Exception) {
                settings.temperature
            }
        }
        settings.contrast_url = if (mySettingsComponent!!.contrastUrlText.isEmpty())
            null else mySettingsComponent!!.contrastUrlText
    }

    override fun reset() {
        val settings: AppSettingsState = AppSettingsState.instance
        mySettingsComponent!!.tokenText = settings.token ?: ""
        mySettingsComponent!!.modelText = settings.model
        mySettingsComponent!!.temperatureText = if (settings.temperature != null) settings.temperature.toString() else ""
        mySettingsComponent!!.contrastUrlText = settings.contrast_url ?: ""
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}