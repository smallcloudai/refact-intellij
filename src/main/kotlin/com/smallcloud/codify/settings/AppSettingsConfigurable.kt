package com.smallcloud.codify.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.io.InferenceGlobalContext
import org.jetbrains.annotations.Nls
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
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
            modified or (mySettingsComponent!!.contrastUrlText.isNotEmpty() && (!InferenceGlobalContext.hasUserInferenceUri() ||
                    makeUrlGreat(mySettingsComponent!!.contrastUrlText) != InferenceGlobalContext.inferenceUri))
        modified =
            modified or (mySettingsComponent!!.contrastUrlText.isEmpty() && InferenceGlobalContext.hasUserInferenceUri())

        modified = modified || mySettingsComponent!!.useForceCompletion != InferenceGlobalContext.useForceCompletion
        modified = modified || mySettingsComponent!!.useMultipleFilesCompletion != InferenceGlobalContext.useMultipleFilesCompletion

        modified = modified || mySettingsComponent!!.useDeveloperMode != InferenceGlobalContext.developerModeEnabled
        modified = modified or (mySettingsComponent!!.longthinkModel.isNotEmpty()
                && (InferenceGlobalContext.longthinkModel == null ||
                InferenceGlobalContext.longthinkModel != mySettingsComponent!!.longthinkModel))
        modified = modified or (mySettingsComponent!!.longthinkModel.isEmpty()
                && InferenceGlobalContext.longthinkModel != null)

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
        InferenceGlobalContext.inferenceUri =
            if (mySettingsComponent!!.contrastUrlText.isEmpty()) null else
                makeUrlGreat(mySettingsComponent!!.contrastUrlText)
        InferenceGlobalContext.useForceCompletion = mySettingsComponent!!.useForceCompletion
        InferenceGlobalContext.useMultipleFilesCompletion = mySettingsComponent!!.useMultipleFilesCompletion
        InferenceGlobalContext.developerModeEnabled = mySettingsComponent!!.useDeveloperMode
        InferenceGlobalContext.longthinkModel = mySettingsComponent!!.longthinkModel.ifEmpty { null }
    }

    override fun reset() {
        mySettingsComponent!!.tokenText = AccountManager.apiKey ?: ""
        mySettingsComponent!!.modelText = InferenceGlobalContext.model ?: ""
        mySettingsComponent!!.contrastUrlText =
            if (InferenceGlobalContext.hasUserInferenceUri()) InferenceGlobalContext.inferenceUri.toString() else ""
        mySettingsComponent!!.useForceCompletion = InferenceGlobalContext.useForceCompletion
        mySettingsComponent!!.useMultipleFilesCompletion = InferenceGlobalContext.useMultipleFilesCompletion
        mySettingsComponent!!.useDeveloperMode = InferenceGlobalContext.developerModeEnabled
        mySettingsComponent!!.longthinkModel = InferenceGlobalContext.longthinkModel ?: ""
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }


}
