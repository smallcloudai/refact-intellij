package com.smallcloud.codify.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.Resources
import com.smallcloud.codify.Resources.pluginDescriptionStr
import com.smallcloud.codify.account.*
import com.smallcloud.codify.account.AccountManager.isLoggedIn
import com.smallcloud.codify.account.AccountManager.logout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

private enum class SettingsState {
    SIGNED,
    UNSIGNED,
    WAITING
}

class AppRootComponent {
    private var loginCounter: Int
    private var currentState: SettingsState = SettingsState.UNSIGNED
    private val loginButton = JButton("Login / Register")
    private val logoutButton = JButton("Logout")
    private val forceLoginButton = JButton(AllIcons.Actions.Refresh)
    private val waitLoginLabel = JBLabel()
    private val activePlanLabel = JBLabel("")

    init {
        loginCounter = loginCooldownCounter
        currentState = if (isLoggedIn) {
            SettingsState.SIGNED
        } else if (AccountManager.ticket != null) {
            SettingsState.WAITING
        } else {
            SettingsState.UNSIGNED
        }
        ApplicationManager.getApplication()
            .messageBus
            .connect(PluginState.instance)
            .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                override fun isLoggedInChanged(limited: Boolean) {
                    currentState = if (limited) SettingsState.SIGNED else SettingsState.UNSIGNED
                    revalidate()
                }

                override fun planStatusChanged(newPlan: String?) {
                    revalidate()
                }

                override fun ticketChanged(newTicket: String?) {
                    if (newTicket != null) currentState = SettingsState.WAITING
                    revalidate()
                }
            })
        ApplicationManager.getApplication()
            .messageBus
            .connect(PluginState.instance)
            .subscribe(LoginCounterChangedNotifier.TOPIC, object : LoginCounterChangedNotifier {
                override fun counterChanged(newValue: Int) {
                    loginCounter = newValue
                    revalidate()
                }
            })

        loginButton.addActionListener {
            login()
        }
        logoutButton.addActionListener {
            logout()
        }
        forceLoginButton.addActionListener {
            ApplicationManager.getApplication().getService(LoginStateService::class.java).tryToWebsiteLogin(true)
        }
    }

    private var myPanel: JPanel = recreatePanel()

    private fun revalidate() {
        setupProperties()
        myPanel.revalidate()
    }

    private fun setupProperties() {
        activePlanLabel.text = "Active plan: ${AccountManager.activePlan}"
        activePlanLabel.isVisible = currentState == SettingsState.SIGNED && AccountManager.activePlan != null
        logoutButton.isVisible = currentState == SettingsState.SIGNED
        loginButton.isVisible = currentState != SettingsState.SIGNED
        forceLoginButton.isVisible = currentState != SettingsState.UNSIGNED
        waitLoginLabel.text = if (currentState == SettingsState.WAITING)
            "${Resources.waitWebsiteLoginStr} $loginCounter" else "Logged as ${AccountManager.user}"
        waitLoginLabel.isVisible = currentState != SettingsState.UNSIGNED
    }

    val preferredFocusedComponent: JComponent
        get() = if (isLoggedIn) forceLoginButton else loginButton

    private fun recreatePanel(): JPanel {
        val description = JBLabel(pluginDescriptionStr)
        setupProperties()
        return FormBuilder.createFormBuilder().run {
            addComponent(description)
            addLabeledComponent(waitLoginLabel, forceLoginButton)
            addComponent(activePlanLabel)
            addComponent(logoutButton)
            addComponent(loginButton)
            addComponentFillVertically(JPanel(), 0).panel
        }
    }

    val panel: JPanel
        get() {
            return myPanel
        }
}
