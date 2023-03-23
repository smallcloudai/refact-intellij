package com.smallcloud.refact.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.smallcloud.refact.Resources
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * Provides controller functionality for application settings.
 */
class AppRootConfigurable(private val project: Project) : Configurable {
    private var mySettingsComponent: AppRootComponent? = null

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP
    override fun getDisplayName(): @Nls(capitalization = Nls.Capitalization.Title) String {
        return Resources.codifyStr
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return mySettingsComponent!!.preferredFocusedComponent
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = AppRootComponent(project)
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
