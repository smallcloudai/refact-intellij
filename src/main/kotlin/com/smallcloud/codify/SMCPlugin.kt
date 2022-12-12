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

class SMCPlugin: Disposable {
    private var modules: Map<ProcessType, Module> = mapOf(
            ProcessType.COMPLETION to CompletionModule()
    )
    private var websiteMessage: String? = null
    private var inferenceMessage: String? = null
//    private var loginMessage: String? = null
    private var isEnable: Boolean = false
    var is_enable: Boolean
        get() = isEnable
        set(value) {
            if (value != isEnable) {
                isEnable = value
                if (value) enable() else disable()
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                            .pluginEnableChanged(isEnable)
                }
            }
        }
    var website_message: String?
        get() = websiteMessage
        set(newMsg) {
            if (websiteMessage != newMsg) {
                websiteMessage = newMsg
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                            .websiteMessageChanged(websiteMessage)
                }
            }
        }
    var inference_message: String?
        get() = inferenceMessage
        set(newMsg) {
            if (inferenceMessage != newMsg) {
                inferenceMessage = newMsg
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                            .inferenceMessageChanged(inferenceMessage)
                }
            }
        }

    var login_message: String?
        get() = AppSettingsState.instance.loginMessage
        set(newMsg) {
            if (login_message != newMsg) {
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

    fun process(process_type: ProcessType, request_body: SMCRequestBody, editor: Editor) {
        val request = InferenceGlobalContext.make_request(request_body)

        val module = modules[process_type]
        if (module != null && request != null) {
            module.process(request_body, request, editor)
        }
    }

    companion object {
        var instant = SMCPlugin()
        fun startup(settings: AppSettingsState) {
            instant.is_enable = settings.pluginIsEnabled
        }
    }

    override fun dispose() {
    }
}