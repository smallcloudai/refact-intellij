package com.smallcloud.codify

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic
import com.smallcloud.codify.inline.CompletionModule
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.ProcessType
import com.smallcloud.codify.struct.SMCRequestBody
import com.smallcloud.codify.utils.dispatch


interface ExtraInfoChangedNotifier {
    fun websiteMessageChanged(newMsg: String?) {}
    fun inferenceMessageChanged(newMsg: String?) {}
    fun loginMessageChanged(newMsg: String?) {}
    fun pluginEnableChanged(newVal: Boolean) {}

    companion object {
        val TOPIC = Topic.create("Extra Info Changed Notifier", ExtraInfoChangedNotifier::class.java)
    }
}

class SMCPlugin : Disposable {
    private var modules: Map<ProcessType, Module> = mapOf(
        ProcessType.COMPLETION to CompletionModule()
    )

    var isEnable: Boolean = false
        set(value) {
            if (value != field) {
                field = value
                if (value) enable() else disable()
                dispatch {
                    ApplicationManager.getApplication()
                        .messageBus
                        .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                        .pluginEnableChanged(field)
                }
            }
        }
    var websiteMessage: String? = null
        set(newMsg) {
            if (field != newMsg) {
                field = newMsg
                dispatch {
                    ApplicationManager.getApplication()
                        .messageBus
                        .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                        .websiteMessageChanged(websiteMessage)
                }
            }
        }
    var inferenceMessage: String? = null
        set(newMsg) {
            if (field != newMsg) {
                field = newMsg
                dispatch {
                    ApplicationManager.getApplication()
                        .messageBus
                        .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                        .inferenceMessageChanged(field)
                }
            }
        }

    var loginMessage: String?
        get() = AppSettingsState.instance.loginMessage
        set(newMsg) {
            if (loginMessage != newMsg) {
                dispatch {
                    ApplicationManager.getApplication()
                        .messageBus
                        .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                        .loginMessageChanged(newMsg)
                }
            }
        }

    private fun disable() {
        modules = emptyMap()
    }

    private fun enable() {
        modules = mapOf(
            ProcessType.COMPLETION to CompletionModule()
        )
    }

    fun process(processType: ProcessType, requestBody: SMCRequestBody, editor: Editor) {
        val request = InferenceGlobalContext.make_request(requestBody)

        val module = modules[processType]
        if (module != null && request != null) {
            module.process(requestBody, request, editor)
        }
    }

    companion object {
        var instance = SMCPlugin()
        fun startup(settings: AppSettingsState) {
            instance.isEnable = settings.pluginIsEnabled
        }
    }

    override fun dispose() {
    }
}
