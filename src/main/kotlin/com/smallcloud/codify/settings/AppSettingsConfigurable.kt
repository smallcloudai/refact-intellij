package com.smallcloud.codify.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.io.InferenceGlobalContext
import org.jetbrains.annotations.Nls
import java.net.URI
import javax.swing.JComponent

/**
 * Provides controller functionality for application settings.
 */
class AppSettingsConfigurable : Configurable {
    private var mySettingsComponent: AppSettingsComponent? = null

    init {
        ApplicationManager.getApplication().messageBus.connect(PluginState.instance)
            .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                override fun apiKeyChanged(newApiKey: String?) {
                    mySettingsComponent?.myTokenText?.let { it.text = newApiKey }
                    mySettingsComponent?.splitter?.revalidate()
                }
            })
    }

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
        return mySettingsComponent!!.splitter
    }

    override fun isModified(): Boolean {
        var modified =
            (mySettingsComponent!!.tokenText.isNotEmpty() && (AccountManager.apiKey == null ||
                    mySettingsComponent!!.tokenText != AccountManager.apiKey))
        modified = modified or (mySettingsComponent!!.tokenText.isEmpty() && AccountManager.apiKey != null)

        modified = modified or (mySettingsComponent!!.modelText.isNotEmpty()
                && (InferenceGlobalContext.model == null ||
                InferenceGlobalContext.model != mySettingsComponent!!.modelText))
        modified = modified or (mySettingsComponent!!.modelText.isEmpty() && InferenceGlobalContext.model != null)

        modified =
            modified or (mySettingsComponent!!.temperatureText.isNotEmpty() && (InferenceGlobalContext.temperature == null ||
                    mySettingsComponent!!.temperatureText.toFloat() != InferenceGlobalContext.temperature))
        modified =
            modified or (mySettingsComponent!!.temperatureText.isEmpty() && InferenceGlobalContext.temperature != null)

        modified =
            modified or (mySettingsComponent!!.contrastUrlText.isNotEmpty() && (!InferenceGlobalContext.hasUserInferenceUri() ||
                    makeUrlGreat(mySettingsComponent!!.contrastUrlText) != InferenceGlobalContext.inferenceUri))
        modified =
            modified or (mySettingsComponent!!.contrastUrlText.isEmpty() && InferenceGlobalContext.hasUserInferenceUri())

        modified = modified || mySettingsComponent!!.useForceCompletion != AppSettingsState.instance.useForceCompletion
        modified = modified || mySettingsComponent!!.useMultipleFilesCompletion != AppSettingsState.instance.useMultipleFilesCompletion
        modified = modified || mySettingsComponent!!.useStreamingCompletion != AppSettingsState.instance.useStreamingCompletion

        return modified
    }

    private fun makeUrlGreat(uri: String): URI {
        var newUri = uri
        if(!uri.startsWith("http://") && !uri.startsWith("https://")) {
            newUri = "https://$newUri"
        }
        if(!uri.endsWith("/")) {
            newUri = "$newUri/"
        }
        return URI(newUri)
    }

    override fun apply() {
        AccountManager.apiKey = mySettingsComponent!!.tokenText.ifEmpty { null }
        InferenceGlobalContext.model = mySettingsComponent!!.modelText.ifEmpty { null }
        if (mySettingsComponent!!.temperatureText.isEmpty()) {
            InferenceGlobalContext.temperature = null
        } else {
            try {
                InferenceGlobalContext.temperature = mySettingsComponent!!.temperatureText.toFloat()
            } catch (e: Exception) {
                InferenceGlobalContext.temperature
            }
        }
        InferenceGlobalContext.inferenceUri =
            if (mySettingsComponent!!.contrastUrlText.isEmpty()) null else
                makeUrlGreat(mySettingsComponent!!.contrastUrlText)
        AppSettingsState.instance.useForceCompletion = mySettingsComponent!!.useForceCompletion
        AppSettingsState.instance.useMultipleFilesCompletion = mySettingsComponent!!.useMultipleFilesCompletion
        AppSettingsState.instance.useStreamingCompletion = mySettingsComponent!!.useStreamingCompletion
    }

    override fun reset() {
        mySettingsComponent!!.tokenText = AccountManager.apiKey ?: ""
        mySettingsComponent!!.modelText = InferenceGlobalContext.model ?: ""
        mySettingsComponent!!.temperatureText =
            if (InferenceGlobalContext.temperature == null) "" else InferenceGlobalContext.temperature.toString()
        mySettingsComponent!!.contrastUrlText =
            if (InferenceGlobalContext.hasUserInferenceUri()) InferenceGlobalContext.inferenceUri.toString() else ""
        mySettingsComponent!!.useForceCompletion = AppSettingsState.instance.useForceCompletion
        mySettingsComponent!!.useMultipleFilesCompletion = AppSettingsState.instance.useMultipleFilesCompletion
        mySettingsComponent!!.useStreamingCompletion = AppSettingsState.instance.useStreamingCompletion
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}
