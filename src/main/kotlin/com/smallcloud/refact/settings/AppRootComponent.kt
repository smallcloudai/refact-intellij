package com.smallcloud.refact.settings

//import com.smallcloud.codify.Resources.pluginDescriptionStr
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.smallcloud.refact.CodifyBundle
import com.smallcloud.refact.PluginState
import com.smallcloud.refact.Resources
import com.smallcloud.refact.account.*
import com.smallcloud.refact.account.AccountManager.isLoggedIn
import com.smallcloud.refact.account.AccountManager.logout
import com.smallcloud.refact.privacy.Privacy
import com.smallcloud.refact.privacy.PrivacyChangesNotifier
import com.smallcloud.refact.privacy.PrivacyService
import com.smallcloud.refact.settings.renderer.PrivacyOverridesTable
import com.smallcloud.refact.settings.renderer.privacyToString
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.util.concurrent.TimeUnit
import javax.swing.*


private enum class SettingsState {
    SIGNED,
    UNSIGNED,
    WAITING
}


class AppRootComponent(private val project: Project) {
    private var loginCounter: Int
    private var currentState: SettingsState = SettingsState.UNSIGNED
    private val loginButton = JButton(CodifyBundle.message("rootSettings.loginOrRegister"))
    private val logoutButton = JButton(CodifyBundle.message("rootSettings.logout"))
    private val forceLoginButton = JButton(AllIcons.Actions.Refresh)
    private val waitLoginLabel = JBLabel()
    private val activePlanLabel = JBLabel("")

    private val privacyTitledSeparator = TitledSeparator(CodifyBundle.message("rootSettings.yourPrivacyRules"))
    private val privacySettingDescription = JBLabel("${CodifyBundle.message("rootSettings.globalDefaults")}:")

    private val privacyDefaultsRBGroup = ButtonGroup()
    private val privacyDefaultsRBDisabled = JBRadioButton(
        "${CodifyBundle.message("privacy.level0Name")}: " +
                CodifyBundle.message("privacy.level0ShortDescription")
    )
    private val privacyDefaultsRBDisabledDescription = JBLabel(
        CodifyBundle.message("privacy.level0ExtraDescription"),
        UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER
    )
    private val privacyDefaultsRBCodify = JBRadioButton(
        "${CodifyBundle.message("privacy.level1Name")}: " +
                CodifyBundle.message("privacy.level1ShortDescription")
    )
    private val privacyDefaultsRBCodifyDescription = JBLabel(
        CodifyBundle.message("privacy.level1ExtraDescription"),
        UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER
    )
    private val privacyDefaultsRBCodifyPlus = JBRadioButton(
        "${CodifyBundle.message("privacy.level2Name")}: " +
                CodifyBundle.message("privacy.level2ShortDescription")
    )
    private val privacyDefaultsRBCodifyPlusDescription = JBLabel(
        CodifyBundle.message("privacy.level2ExtraDescription"),
        UIUtil.ComponentStyle.SMALL, UIUtil.FontColor.BRIGHTER
    )

    private val privacyOverridesLabel = JBLabel(CodifyBundle.message("rootSettings.globalPrivacyOverrides"))
    private val privacyOverridesTable = PrivacyOverridesTable()
    private val privacyOverridesScrollPane: JBScrollPane

    private fun askDialog(project: Project, newPrivacy: Privacy): Int {
        return if (MessageDialogBuilder.okCancel(
                Resources.codifyStr, "Be careful! " +
                        "You are about to change global privacy default to:\n\n<b>${privacyToString[newPrivacy]}</b>\n\n" +
                        "Access settings for\n<b>${project.basePath}</b>\nwill remain at " +
                        "<b>${privacyToString[PrivacyService.instance.getPrivacy(project.basePath!!)]}</b>"
            )
                .yesText(Messages.getOkButton())
                .noText(Messages.getCancelButton())
                .icon(Messages.getQuestionIcon())
                .doNotAsk(object : DoNotAskOption.Adapter() {
                    override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
                        if (isSelected && exitCode == Messages.OK) {
                            PrivacyState.instance.dontAskDefaultPrivacyChanged = true
                        }
                    }

                    override fun getDoNotShowMessage(): String {
                        return CodifyBundle.message("rootSettings.dialog.dontShowAgain")
                    }
                })
                .ask(project)
        ) Messages.OK else Messages.CANCEL
    }

    init {
        privacyDefaultsRBGroup.add(privacyDefaultsRBDisabled)
        privacyDefaultsRBGroup.add(privacyDefaultsRBCodify)
        privacyDefaultsRBGroup.add(privacyDefaultsRBCodifyPlus)

        val connectionTypeChangedListener =
            ItemListener { e: ItemEvent ->
                if (e.stateChange == ItemEvent.SELECTED) {
                    val newPrivacy: Privacy = when (e.source) {
                        privacyDefaultsRBDisabled -> Privacy.DISABLED
                        privacyDefaultsRBCodify -> Privacy.ENABLED
                        privacyDefaultsRBCodifyPlus -> Privacy.THIRDPARTY
                        else -> Privacy.DISABLED
                    }
                    if (PrivacyState.instance.defaultPrivacy == newPrivacy) return@ItemListener

                    if (!PrivacyState.instance.dontAskDefaultPrivacyChanged) {
                        val okCancel = askDialog(project, newPrivacy)
                        if (okCancel != Messages.OK) {
                            when (PrivacyState.instance.defaultPrivacy) {
                                Privacy.DISABLED -> privacyDefaultsRBDisabled.isSelected = true
                                Privacy.ENABLED -> privacyDefaultsRBCodify.isSelected = true
                                Privacy.THIRDPARTY -> privacyDefaultsRBCodifyPlus.isSelected = true
                                else -> privacyDefaultsRBCodifyPlus.isSelected = true
                            }
                            return@ItemListener
                        }
                    }

                    PrivacyState.instance.defaultPrivacy = newPrivacy
                    ApplicationManager.getApplication()
                        .messageBus
                        .syncPublisher(PrivacyChangesNotifier.TOPIC)
                        .privacyChanged()
                }
            }
        privacyDefaultsRBDisabled.addItemListener(connectionTypeChangedListener)
        privacyDefaultsRBCodify.addItemListener(connectionTypeChangedListener)
        privacyDefaultsRBCodifyPlus.addItemListener(connectionTypeChangedListener)

        when (PrivacyState.instance.defaultPrivacy) {
            Privacy.DISABLED -> privacyDefaultsRBDisabled.isSelected = true
            Privacy.ENABLED -> privacyDefaultsRBCodify.isSelected = true
            Privacy.THIRDPARTY -> privacyDefaultsRBCodifyPlus.isSelected = true
            else -> privacyDefaultsRBCodifyPlus.isSelected = true
        }

        privacyOverridesScrollPane = JBScrollPane(
            privacyOverridesTable,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )

        loginCounter = loginCoolDownCounter
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
        forceLoginButton.addActionListener {
            activePlanLabel.text = "${CodifyBundle.message("rootSettings.activePlan")}: ⏳"
            AppExecutorUtil.getAppScheduledExecutorService().schedule(
                {
                    activePlanLabel.text =
                        "${CodifyBundle.message("rootSettings.activePlan")}: ${AccountManager.activePlan}"
                }, 500, TimeUnit.MILLISECONDS
            )
        }
    }

    private var myPanel: JPanel = recreatePanel()

    private fun revalidate() {
        setupProperties()
        myPanel.revalidate()
    }

    private fun setupProperties() {
        activePlanLabel.text = "${CodifyBundle.message("rootSettings.activePlan")}: ${AccountManager.activePlan}"
        activePlanLabel.isVisible = currentState == SettingsState.SIGNED && AccountManager.activePlan != null
        logoutButton.isVisible = currentState == SettingsState.SIGNED
        loginButton.isVisible = currentState != SettingsState.SIGNED
        forceLoginButton.isVisible = currentState != SettingsState.UNSIGNED
        waitLoginLabel.text = if (currentState == SettingsState.WAITING)
            "${CodifyBundle.message("rootSettings.waitWebsiteLoginStr")} $loginCounter" else
            "${CodifyBundle.message("rootSettings.loggedAs")} ${AccountManager.user}"
        waitLoginLabel.isVisible = currentState != SettingsState.UNSIGNED

        privacyTitledSeparator.isVisible = currentState == SettingsState.SIGNED
        privacySettingDescription.isVisible = currentState == SettingsState.SIGNED
        privacyDefaultsRBDisabled.isVisible = currentState == SettingsState.SIGNED
        privacyDefaultsRBCodify.isVisible = currentState == SettingsState.SIGNED
        privacyDefaultsRBCodifyPlus.isVisible = currentState == SettingsState.SIGNED
        privacyDefaultsRBDisabledDescription.isVisible = currentState == SettingsState.SIGNED
        privacyDefaultsRBCodifyDescription.isVisible = currentState == SettingsState.SIGNED
        privacyDefaultsRBCodifyPlusDescription.isVisible = currentState == SettingsState.SIGNED
        privacyOverridesLabel.isVisible = currentState == SettingsState.SIGNED
        privacyOverridesScrollPane.isVisible = currentState == SettingsState.SIGNED
        privacyOverridesTable.isVisible = currentState == SettingsState.SIGNED
    }

    val preferredFocusedComponent: JComponent
        get() = if (isLoggedIn) forceLoginButton else loginButton

    private fun recreatePanel(): JPanel {
//        val description = JBLabel(pluginDescriptionStr)
        setupProperties()
        return FormBuilder.createFormBuilder().run {
            addComponent(TitledSeparator(CodifyBundle.message("rootSettings.account")))
//            addComponent(description, UIUtil.LARGE_VGAP)
            addLabeledComponent(waitLoginLabel, forceLoginButton)
            addComponent(activePlanLabel)
            addComponent(logoutButton)
            addComponent(loginButton)

            addComponent(privacyTitledSeparator, UIUtil.LARGE_VGAP)
            addComponent(privacySettingDescription, UIUtil.LARGE_VGAP)
            addComponent(privacyDefaultsRBDisabled, (UIUtil.DEFAULT_VGAP * 1.5).toInt())
            addComponent(privacyDefaultsRBDisabledDescription, 0)
            addComponent(privacyDefaultsRBCodify, (UIUtil.DEFAULT_VGAP * 1.5).toInt())
            addComponent(privacyDefaultsRBCodifyDescription, 0)
            addComponent(privacyDefaultsRBCodifyPlus, (UIUtil.DEFAULT_VGAP * 1.5).toInt())
            addComponent(privacyDefaultsRBCodifyPlusDescription, 0)
            addComponent(privacyOverridesLabel, UIUtil.LARGE_VGAP)
            addComponent(privacyOverridesScrollPane)

            addComponentFillVertically(JPanel(), 0).panel
        }
    }

    val panel: JPanel
        get() {
            return myPanel
        }
}
