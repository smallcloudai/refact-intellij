package com.smallcloud.codify.settings

import com.intellij.openapi.application.invokeLater
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.smallcloud.codify.account.AccountManager.is_login
import com.smallcloud.codify.account.AccountManager.logout
import com.smallcloud.codify.account.login
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer


class AppRootComponent {
    private val loginButton = JButton("Login / Register")
    private val logoutButton = JButton("Logout")
    private val bugReportButton = JButton("Bug Report...")
    private val loggedLabel = JBLabel("")
    private var myPanel: JPanel = recreate_panel()
    private var timer: Timer

    init {
        timer = Timer(500) {
            invokeLater { revalidate() }
        }
        timer.start()
        loginButton.addActionListener {
            login()
        }
        logoutButton.addActionListener {
            logout()
        }
    }

    private fun revalidate() {
        setup_properties()
        myPanel.revalidate()
    }

    private fun setup_properties() {
        loggedLabel.text = "Logged as ${AppSettingsState.instance.userLogged}"
        loggedLabel.isVisible = is_login
        logoutButton.isVisible = is_login
        bugReportButton.isVisible = is_login
        loginButton.isVisible = !is_login
    }

    val preferredFocusedComponent: JComponent
        get() = if (is_login) bugReportButton else loginButton

    private fun recreate_panel(): JPanel {
        var builder = FormBuilder.createFormBuilder()
        val description = JBLabel("Codify: AI autocomplete, refactoring and advanced code generation")
        builder.addComponent(description)
        builder.addComponent(loggedLabel)
        builder.addComponent(logoutButton)
        builder.addComponent(bugReportButton)
        builder = builder.addComponent(loginButton)
        setup_properties()
        return builder.addComponentFillVertically(JPanel(), 0).panel
    }

    val panel: JPanel
        get() {
            return myPanel
        }
}