package com.smallcloud.codify.utils

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFocusManager

fun getLastUsedProject(): Project {
    return IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project
        ?: ProjectManager.getInstance().openProjects.firstOrNull()
        ?: ProjectManager.getInstance().defaultProject
}