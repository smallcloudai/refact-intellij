package com.smallcloud.refactai.panes.sharedchat

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    WorkingValidationTest::class,
    BasicChatWebViewTest::class
)
class ChatWebViewTestSuite {
    companion object
}
