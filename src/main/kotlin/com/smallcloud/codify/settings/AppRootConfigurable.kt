package com.smallcloud.codify.settings

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent


/**
 * Provides controller functionality for application settings.
 */
class AppRootConfigurable : Configurable {
    private var mySettingsComponent: AppRootComponent? = null

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP
    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String? {
        return "Codify"
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return mySettingsComponent!!.preferredFocusedComponent
    }

    override fun createComponent(): JComponent? {
        mySettingsComponent = AppRootComponent()
        return mySettingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun apply() {
    }

    override fun reset() {
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}