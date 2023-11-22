package com.smallcloud.refactai.aitoolbox.utils

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.aitoolbox.State
import com.smallcloud.refactai.privacy.Privacy
import com.smallcloud.refactai.privacy.PrivacyService
import com.smallcloud.refactai.struct.LongthinkFunctionEntry

fun getFilteredIntent(intent: String): String {
    var filteredIntent = intent
    while (filteredIntent.isNotEmpty() && filteredIntent.last().isWhitespace()) {
        filteredIntent = filteredIntent.dropLast(1)
    }
    return filteredIntent
}

fun getReasonForEntryFromState(): String? {
    return getReasonForEntryFromState(State.entry)
}

fun getReasonForEntryFromState(entry: LongthinkFunctionEntry): String? {
    if (State.editor == null) return null
    val vFile = FileDocumentManager.getInstance().getFile(State.editor!!.document)
    if (PrivacyService.instance.getPrivacy(vFile) == Privacy.DISABLED) {
        return RefactAIBundle.message("aiToolbox.reasons.privacyDisabled")
    }
    if (entry.thirdParty && PrivacyService.instance.getPrivacy(vFile) < Privacy.THIRDPARTY) {
        return RefactAIBundle.message("aiToolbox.reasons.thirdParty")
    }
    if (getFilteredIntent(entry.intent).endsWith("?")) return null
    if (vFile != null && !entry.supportsLanguages.match(vFile.name)) {
        return RefactAIBundle.message("aiToolbox.reasons.supportLang")
    }
    if (!State.haveSelection) {
        if (!entry.supportHighlight) {
            return RefactAIBundle.message("aiToolbox.reasons.selectCodeFirst",
                    entry.selectedLinesMin, entry.selectedLinesMax)
        }
//        if (State.currentIntent.isEmpty() && (entry.catchAny())) {
//            return RefactAIBundle.message("aiToolbox.reasons.writeSomething")
//        }
    } else {
        val lines = State.finishPosition.line - State.startPosition.line + 1
        if (!entry.supportSelection) {
            return RefactAIBundle.message("aiToolbox.reasons.onlyForHL")
        }
        if (entry.selectedLinesMax < lines) {
            return RefactAIBundle.message("aiToolbox.reasons.linesGreater", entry.selectedLinesMax)
        }
        if (entry.selectedLinesMin > lines) {
            return RefactAIBundle.message("aiToolbox.reasons.linesLess", entry.selectedLinesMin)
        }
//        if (State.currentIntent.isEmpty() && (entry.catchAny())) {
//            return RefactAIBundle.message("aiToolbox.reasons.writeSomething")
//        }
    }

    return null
}