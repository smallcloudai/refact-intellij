package com.smallcloud.codify.modes.completion

import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.jetbrains.rd.util.getOrCreate
import com.smallcloud.codify.modes.ModeProvider

class CompletionLookupListener(editor: Editor): LookupListener {
    private val modeProvider: ModeProvider = ModeProvider.getOrCreateModeProvider(editor)
    private val completionMode: CompletionMode = modeProvider.getCompletionMode() as CompletionMode
    private val logger = Logger.getInstance("CompletionLookupListener")

    override fun lookupShown(event: LookupEvent) {
        logger.info("lookupShown")
        completionMode.hideCompletion()
        completionMode.needToRender = false
    }

    override fun beforeItemSelected(event: LookupEvent): Boolean {
        logger.info("beforeItemSelected")
        return true
    }

    override fun itemSelected(event: LookupEvent) {
        logger.info("itemSelected")
        completionMode.cancelOrClose(event.lookup.editor)
        completionMode.needToRender = true
    }

    override fun lookupCanceled(event: LookupEvent) {
        logger.info("lookupCanceled")
        completionMode.showCompletion()
        completionMode.needToRender = true
    }

    override fun currentItemChanged(event: LookupEvent) {
        logger.info("currentItemChanged")
    }

    override fun uiRefreshed() {
        logger.info("uiRefreshed")
    }

    override fun focusDegreeChanged() {
        logger.info("focusDegreeChanged")
    }

    companion object {
        private const val MAX_EDITORS: Int = 8
        private var lookupListeners: LinkedHashMap<Int, CompletionLookupListener> = linkedMapOf()
        private var providersToTs: LinkedHashMap<Int, Long> = linkedMapOf()

        fun getOrCreate(editor: Editor): CompletionLookupListener {
            val hashId = System.identityHashCode(editor)
            if (lookupListeners.size > MAX_EDITORS) {
                val toRemove = providersToTs.minByOrNull { it.value }?.key
                providersToTs.remove(toRemove)
                lookupListeners.remove(toRemove)
            }
            return lookupListeners.getOrCreate(hashId) {
                val listener = CompletionLookupListener(editor)
                providersToTs[hashId] = System.currentTimeMillis()
                listener
            }
        }
    }
}