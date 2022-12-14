package com.smallcloud.codify.settings

import com.intellij.openapi.options.Configurable
import com.smallcloud.codify.io.InferenceGlobalContext
import com.smallcloud.codify.account.AccountManager
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * Provides controller functionality for application settings.
 */
class AppSettingsConfigurable : Configurable {
    private var mySettingsComponent: AppSettingsComponent? = null

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP
    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
        return "Settings"
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return mySettingsComponent!!.preferredFocusedComponent
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = AppSettingsComponent()
        return mySettingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        var modified = (mySettingsComponent!!.tokenText.isNotEmpty()
                && (AccountManager.apiKey == null || mySettingsComponent!!.tokenText != AccountManager.apiKey))
        modified = modified or (mySettingsComponent!!.tokenText.isEmpty() && AccountManager.apiKey != null)

        modified = modified or (mySettingsComponent!!.modelText != InferenceGlobalContext.model)

        modified = modified or (mySettingsComponent!!.temperatureText.isNotEmpty() &&
                (InferenceGlobalContext.temperature == null ||
                        mySettingsComponent!!.temperatureText.toFloat() != InferenceGlobalContext.temperature))
        modified = modified or (mySettingsComponent!!.temperatureText.isEmpty() && InferenceGlobalContext.temperature != null)

        modified = modified or (mySettingsComponent!!.contrastUrlText.isNotEmpty() &&
                (InferenceGlobalContext.inferenceUrl == null || mySettingsComponent!!.contrastUrlText != InferenceGlobalContext.inferenceUrl))
        modified = modified or (mySettingsComponent!!.contrastUrlText.isEmpty() && InferenceGlobalContext.inferenceUrl != null)
        return modified
    }

    override fun apply() {
        AccountManager.apiKey = if (mySettingsComponent!!.tokenText.isEmpty()) null else mySettingsComponent!!.tokenText
        InferenceGlobalContext.model = if (mySettingsComponent!!.modelText.isEmpty()) null else mySettingsComponent!!.modelText
        if (mySettingsComponent!!.temperatureText.isEmpty()) {
            InferenceGlobalContext.temperature = null
        } else {
            try {
                InferenceGlobalContext.temperature = mySettingsComponent!!.temperatureText.toFloat()
            } catch (e: Exception) {
                InferenceGlobalContext.temperature
            }
        }
        InferenceGlobalContext.inferenceUrl = if (mySettingsComponent!!.contrastUrlText.isEmpty())
            null else mySettingsComponent!!.contrastUrlText
    }

    override fun reset() {
        mySettingsComponent!!.tokenText = AccountManager.apiKey ?: ""
        mySettingsComponent!!.modelText = InferenceGlobalContext.model ?: ""
        mySettingsComponent!!.temperatureText = if (InferenceGlobalContext.temperature == null) "" else
            InferenceGlobalContext.temperature.toString()
        mySettingsComponent!!.contrastUrlText = InferenceGlobalContext.inferenceUrl ?: ""
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}
