package com.smallcloud.codify.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.AccountManager.logout
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.account.login
import com.smallcloud.codify.struct.PlanType
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class AppRootComponent {
    private val loginButton = JButton("Login / Register")
    private val logoutButton = JButton("Logout")
    private val bugReportButton = JButton("Bug Report...")
    private val loggedLabel = JBLabel("")
    private val activePlanLabel = JBLabel("")
    private var myPanel: JPanel = recreatePanel()

    init {
        ApplicationManager.getApplication()
                .messageBus
                .connect(SMCPlugin.instance)
                .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                    override fun isLoggedInChanged(limited: Boolean) {
                        revalidate()
                    }
                    override fun planStatusChanged(newPlan: PlanType) {
                        revalidate()
                    }
                })

        loginButton.addActionListener {
            login()
        }
        logoutButton.addActionListener {
            logout()
        }
    }

    private fun revalidate() {
        setupProperties()
        myPanel.revalidate()
    }

    private fun setupProperties() {
        val is_logged_in = AccountManager.isLoggedIn
        loggedLabel.text = "Logged as ${AccountManager.user}"
        loggedLabel.isVisible = is_logged_in
        activePlanLabel.text = "Active plan: ${AccountManager.activePlan}"
        activePlanLabel.isVisible = is_logged_in
        logoutButton.isVisible = is_logged_in
        bugReportButton.isVisible = is_logged_in
        loginButton.isVisible = !is_logged_in
    }

    val preferredFocusedComponent: JComponent
        get() = if (AccountManager.isLoggedIn) bugReportButton else loginButton

    private fun recreatePanel(): JPanel {
        val description = JBLabel("Codify: AI autocomplete, refactoring and advanced code generation")
        setupProperties()
        return FormBuilder.createFormBuilder().run {
            addComponent(description)
            addComponent(loggedLabel)
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
