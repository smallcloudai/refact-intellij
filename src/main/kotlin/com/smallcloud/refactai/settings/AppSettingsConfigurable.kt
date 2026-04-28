package com.smallcloud.refactai.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

/**
 * Provides controller functionality for application settings.
 */
class AppSettingsConfigurable : Configurable {
    private var mySettingsComponent: AppSettingsComponent? = null

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP
    override fun getDisplayName(): String {
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
        var modified = false
        modified = modified || mySettingsComponent!!.useDeveloperMode != InferenceGlobalContext.developerModeEnabled

        modified = modified || mySettingsComponent!!.xDebugLSPPort != InferenceGlobalContext.xDebugLSPPort

        modified = modified || mySettingsComponent!!.stagingVersion != InferenceGlobalContext.stagingVersion

        modified = modified || mySettingsComponent!!.astIsEnabled != InferenceGlobalContext.astIsEnabled
        modified = modified || mySettingsComponent!!.astFileLimit != InferenceGlobalContext.astFileLimit
        modified = modified || mySettingsComponent!!.vecdbIsEnabled != InferenceGlobalContext.vecdbIsEnabled
        modified = modified || mySettingsComponent!!.vecdbFileLimit != InferenceGlobalContext.vecdbFileLimit

        modified =
            modified || mySettingsComponent!!.inferenceModel?.trim()?.ifEmpty { null } != InferenceGlobalContext.model
        modified = modified || mySettingsComponent!!.insecureSSL != InferenceGlobalContext.insecureSSL
        modified = modified || mySettingsComponent!!.completionMaxTokens!= InferenceGlobalContext.completionMaxTokens
        modified = modified || mySettingsComponent!!.experimentalLspFlagEnabled != InferenceGlobalContext.experimentalLspFlagEnabled
        modified = modified || mySettingsComponent!!.pauseCompletion != !InferenceGlobalContext.useAutoCompletion
        return modified
    }

    override fun apply() {
        InferenceGlobalContext.developerModeEnabled = mySettingsComponent!!.useDeveloperMode
        InferenceGlobalContext.stagingVersion = mySettingsComponent!!.stagingVersion
        InferenceGlobalContext.xDebugLSPPort = mySettingsComponent!!.xDebugLSPPort
        InferenceGlobalContext.astIsEnabled = mySettingsComponent!!.astIsEnabled
        InferenceGlobalContext.astFileLimit = mySettingsComponent!!.astFileLimit
        InferenceGlobalContext.vecdbIsEnabled = mySettingsComponent!!.vecdbIsEnabled
        InferenceGlobalContext.vecdbFileLimit = mySettingsComponent!!.vecdbFileLimit
        InferenceGlobalContext.insecureSSL = mySettingsComponent!!.insecureSSL
        InferenceGlobalContext.completionMaxTokens = mySettingsComponent!!.completionMaxTokens
        InferenceGlobalContext.experimentalLspFlagEnabled = mySettingsComponent!!.experimentalLspFlagEnabled
        InferenceGlobalContext.useAutoCompletion = !mySettingsComponent!!.pauseCompletion
        InferenceGlobalContext.model = mySettingsComponent!!.inferenceModel?.trim()?.ifEmpty { null }
    }

    override fun reset() {
        mySettingsComponent!!.useDeveloperMode = InferenceGlobalContext.developerModeEnabled
        mySettingsComponent!!.stagingVersion = InferenceGlobalContext.stagingVersion
        mySettingsComponent!!.xDebugLSPPort = InferenceGlobalContext.xDebugLSPPort
        mySettingsComponent!!.astIsEnabled = InferenceGlobalContext.astIsEnabled
        mySettingsComponent!!.astFileLimit = InferenceGlobalContext.astFileLimit
        mySettingsComponent!!.vecdbIsEnabled = InferenceGlobalContext.vecdbIsEnabled
        mySettingsComponent!!.vecdbFileLimit = InferenceGlobalContext.vecdbFileLimit
        mySettingsComponent!!.inferenceModel = InferenceGlobalContext.model
        mySettingsComponent!!.insecureSSL = InferenceGlobalContext.insecureSSL
        mySettingsComponent!!.completionMaxTokens = InferenceGlobalContext.completionMaxTokens
        mySettingsComponent!!.experimentalLspFlagEnabled = InferenceGlobalContext.experimentalLspFlagEnabled
        mySettingsComponent!!.pauseCompletion = !InferenceGlobalContext.useAutoCompletion
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }


}
