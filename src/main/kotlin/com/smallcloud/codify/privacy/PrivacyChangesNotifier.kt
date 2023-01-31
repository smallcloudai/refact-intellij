package com.smallcloud.codify.privacy

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic

interface PrivacyChangesNotifier {
    fun privacyChanged() {}

    companion object {
        val TOPIC = Topic.create(
            "Codify Privacy Changed Notifier",
            PrivacyChangesNotifier::class.java
        )
    }
}