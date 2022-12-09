package com.smallcloud.codify.utils

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.NotNull

fun dispatch(@NotNull runnable: Runnable) {
//    if (ApplicationManager.getApplication().isDispatchThread) {
        runnable.run()
//    } else {
//        ApplicationManager.getApplication().invokeAndWait(runnable)
//    }
}