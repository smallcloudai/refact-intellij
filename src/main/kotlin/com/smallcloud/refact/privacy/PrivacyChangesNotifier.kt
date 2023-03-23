package com.smallcloud.refact.privacy

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