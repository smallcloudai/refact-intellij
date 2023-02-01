package com.smallcloud.codify.account

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.notifications.emitLogin


class LoginStateService {
    private var lastWebsiteLoginStatus: String = "OK"
    private var lastInferenceLoginStatus: String = "OK"
    private var popupLoginMessageOnce: Boolean = false
    private var lastLoginTime: Long = 0

    fun getLastWebsiteLoginStatus(): String {
        return lastWebsiteLoginStatus
    }

    fun getLastInferenceLoginStatus(): String {
        return lastInferenceLoginStatus
    }

    fun tryToWebsiteLogin(force: Boolean = false) {
        if (System.currentTimeMillis() - lastLoginTime < 30_000) {
            return
        }
        lastLoginTime = System.currentTimeMillis()
        AppExecutorUtil.getAppExecutorService().submit {
            try {
                Logger.getInstance("check_login").warn("call")
                lastWebsiteLoginStatus = checkLogin(force)
                emitLoginIfNeeded()
            } catch (e: Exception) {
                e.message?.let { logError("check_login exception", it) }
                emitLoginIfNeeded()
            } finally {
                popupLoginMessageOnce = true
            }
        }
    }

    private fun emitLoginIfNeeded() {
        if (!popupLoginMessageOnce && lastWebsiteLoginStatus.isEmpty()) {
            val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
            emitLogin(project)
        }
    }
}
