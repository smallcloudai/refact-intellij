package com.smallcloud.codify.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.FormBuilder
import com.smallcloud.codify.Resources
import com.smallcloud.codify.Resources.loginCooldown
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.AccountManager.isLoggedIn
import com.smallcloud.codify.account.AccountManager.logout
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.account.LoginStateService
import com.smallcloud.codify.account.login
import com.smallcloud.codify.struct.PlanType
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

private enum class SettingsState {
    SIGNED,
    UNSIGNED,
    WAITING
}

class AppRootComponent {
    private var loginTask: Future<*>? = null
    private var loginCounter: Int = loginCooldown
    private var currentState: SettingsState = SettingsState.UNSIGNED
    private val loginButton = JButton("Login / Register")
    private val logoutButton = JButton("Logout")
    private val forceLoginButton = JButton(AllIcons.Actions.Refresh)
    private val bugReportButton = JButton("Bug Report...")
    private val waitLoginLabel = JBLabel()
    private val activePlanLabel = JBLabel("")

    init {
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

                override fun planStatusChanged(newPlan: PlanType) {
                    revalidate()
                }

                override fun ticketChanged(newTicket: String?) {
                    if (newTicket != null) currentState = SettingsState.WAITING
                    revalidate()
                }
            })

        loginButton.addActionListener {
            login()

            if (loginTask != null && (!loginTask!!.isDone || !loginTask!!.isCancelled))
                return@addActionListener
            runCounterTask()
        }
        logoutButton.addActionListener {
            logout()
        }
        forceLoginButton.addActionListener {
            ApplicationManager.getApplication().getService(LoginStateService::class.java).tryToWebsiteLogin()
        }
    }

    private var myPanel: JPanel = recreatePanel()

    private fun runCounterTask() {
        var i = 1
        loginTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
            loginCounter = loginCooldown - (i % loginCooldown)
            if (i % loginCooldown == 0) {
                ApplicationManager.getApplication().getService(LoginStateService::class.java).tryToWebsiteLogin()
            }

            if (isLoggedIn || i == loginCooldown * 10) {
                loginTask?.cancel(false)
            }
            revalidate()
            i++
        }, 0, 1, TimeUnit.SECONDS)
    }

    private fun revalidate() {
        setupProperties()
        myPanel.revalidate()
    }

    private fun setupProperties() {
        activePlanLabel.text = "Active plan: ${AccountManager.activePlan}"
        activePlanLabel.isVisible = currentState == SettingsState.SIGNED
        logoutButton.isVisible = currentState == SettingsState.SIGNED
        bugReportButton.isVisible = currentState == SettingsState.SIGNED
        loginButton.isVisible = currentState != SettingsState.SIGNED
        forceLoginButton.isVisible = currentState != SettingsState.UNSIGNED
        waitLoginLabel.text = if (currentState == SettingsState.WAITING)
            "${Resources.waitWebsiteLoginStr} $loginCounter" else "Logged as ${AccountManager.user}"
        waitLoginLabel.isVisible = currentState != SettingsState.UNSIGNED
    }

    val preferredFocusedComponent: JComponent
        get() = if (isLoggedIn) bugReportButton else loginButton

    private fun recreatePanel(): JPanel {
        val description = JBLabel("Codify: AI autocomplete, refactoring and advanced code generation")
        setupProperties()
        return FormBuilder.createFormBuilder().run {
            addComponent(description)
            addLabeledComponent(waitLoginLabel, forceLoginButton)
            addComponent(activePlanLabel)
            addComponent(logoutButton)
            addComponent(bugReportButton)
            addComponent(loginButton)
            addComponentFillVertically(JPanel(), 0).panel
        }
    }

    val panel: JPanel
        get() {
            return myPanel
        }
}
