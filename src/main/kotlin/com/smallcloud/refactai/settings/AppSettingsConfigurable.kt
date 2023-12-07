package com.smallcloud.refactai.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.instance as LSPProcessHolder

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

    private fun makeRightUrl(url: String?): String? {
        var res = url
        if (res != null && !res.startsWith("http")) {
            res = "http://${res}"
        }
        return res
    }

    override fun isModified(): Boolean {
        var modified =
            (mySettingsComponent!!.tokenText.isNotEmpty() && (AccountManager.apiKey == null ||
                    mySettingsComponent!!.tokenText.trim() != AccountManager.apiKey))
        modified = modified || (mySettingsComponent!!.tokenText.isEmpty() && AccountManager.apiKey != null)

        modified = modified || (mySettingsComponent!!.modelText.isNotEmpty()
                && (InferenceGlobalContext.model == null ||
                InferenceGlobalContext.model != mySettingsComponent!!.modelText))
        modified = modified || (mySettingsComponent!!.modelText.isEmpty() && InferenceGlobalContext.model != null)

        modified =
            modified || (mySettingsComponent!!.contrastUrlText.isNotEmpty() &&
                    mySettingsComponent!!.contrastUrlText != InferenceGlobalContext.inferenceUri)
        modified =
            modified || (mySettingsComponent!!.contrastUrlText.isEmpty() && !InferenceGlobalContext.isCloud)

        modified = modified || mySettingsComponent!!.useMultipleFilesCompletion != InferenceGlobalContext.useMultipleFilesCompletion

        modified = modified || mySettingsComponent!!.useDeveloperMode != InferenceGlobalContext.developerModeEnabled

        modified = modified || mySettingsComponent!!.xDebugLSPPort != LSPProcessHolder.xDebugLSPPort

        modified = modified || (mySettingsComponent!!.longthinkModel.isNotEmpty()
                && (InferenceGlobalContext.longthinkModel == null ||
                InferenceGlobalContext.longthinkModel != mySettingsComponent!!.longthinkModel))
        modified = modified || (mySettingsComponent!!.longthinkModel.isEmpty()
                && InferenceGlobalContext.longthinkModel != null)

        modified = modified || mySettingsComponent!!.stagingVersion != InferenceGlobalContext.stagingVersion

        return modified
    }

    override fun apply() {
        AccountManager.apiKey = mySettingsComponent!!.tokenText.trim().ifEmpty { null }
        InferenceGlobalContext.model = mySettingsComponent!!.modelText.ifEmpty { null }
        InferenceGlobalContext.inferenceUri =
            makeRightUrl(mySettingsComponent!!.contrastUrlText.ifEmpty { null })
        mySettingsComponent!!.contrastUrlText = InferenceGlobalContext.inferenceUri ?: ""
        InferenceGlobalContext.useMultipleFilesCompletion = mySettingsComponent!!.useMultipleFilesCompletion
        InferenceGlobalContext.developerModeEnabled = mySettingsComponent!!.useDeveloperMode
        InferenceGlobalContext.longthinkModel = mySettingsComponent!!.longthinkModel.ifEmpty { null }
        InferenceGlobalContext.stagingVersion = mySettingsComponent!!.stagingVersion
        LSPProcessHolder.xDebugLSPPort = mySettingsComponent!!.xDebugLSPPort
    }

    override fun reset() {
        mySettingsComponent!!.tokenText = AccountManager.apiKey ?: ""
        mySettingsComponent!!.modelText = InferenceGlobalContext.model ?: ""
        mySettingsComponent!!.contrastUrlText = InferenceGlobalContext.inferenceUri ?: ""
        mySettingsComponent!!.useMultipleFilesCompletion = InferenceGlobalContext.useMultipleFilesCompletion
        mySettingsComponent!!.useDeveloperMode = InferenceGlobalContext.developerModeEnabled
        mySettingsComponent!!.longthinkModel = InferenceGlobalContext.longthinkModel ?: ""
        mySettingsComponent!!.stagingVersion = InferenceGlobalContext.stagingVersion
        mySettingsComponent!!.xDebugLSPPort = LSPProcessHolder.xDebugLSPPort
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }


}
