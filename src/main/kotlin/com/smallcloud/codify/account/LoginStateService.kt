package com.smallcloud.codify.account

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.notifications.emitLogin
import java.util.concurrent.Future


class LoginStateService {
    private var lastWebsiteLoginStatus: String = "OK"
    private var lastInferenceLoginStatus: String = "OK"
    private var popupLoginMessageOnce: Boolean = false

    fun getLastWebsiteLoginStatus(): String {
        return lastWebsiteLoginStatus
    }

    fun getLastInferenceLoginStatus(): String {
        return lastInferenceLoginStatus
    }

    fun tryToWebsiteLogin(force: Boolean = false) {
        AppExecutorUtil.getAppExecutorService().submit {
            try {
                Logger.getInstance("check_login").warn("call")
                lastWebsiteLoginStatus = checkLogin(force)
                if (!popupLoginMessageOnce && lastWebsiteLoginStatus.isEmpty()) {
                    val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return@submit
                    emitLogin(project)
                }
            } catch (e: Exception) {
                e.message?.let { logError("check_login exception", it) }
            }
            finally {
                popupLoginMessageOnce = true
            }
        }
    }
}
