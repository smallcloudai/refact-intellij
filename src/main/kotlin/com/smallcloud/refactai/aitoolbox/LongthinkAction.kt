package com.smallcloud.refactai.aitoolbox

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.util.Key
import com.smallcloud.refactai.listeners.AIToolboxInvokeAction
import com.smallcloud.refactai.listeners.LastEditorGetterListener
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.struct.LongthinkFunctionVariation
import javax.swing.JComponent

val LongthinkKey = Key.create<LongthinkFunctionVariation>("refact.longthink")

class LongthinkAction: DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val longthink = (e.inputEvent.component as JComponent).getUserData(LongthinkKey)
        if (longthink?.entryName?.isNotEmpty() == true) {
            doActionPerformed(longthink.functions.first())
        }
    }
    fun doActionPerformed(longthink: LongthinkFunctionEntry) {
        LastEditorGetterListener.LAST_EDITOR?.let { AIToolboxInvokeAction().doActionPerformed(it, longthink) }
    }

}