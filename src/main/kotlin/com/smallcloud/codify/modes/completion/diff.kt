package com.smallcloud.codify.modes.completion

import org.apache.commons.lang.StringUtils


fun difference(currentText: String?, predictedText: String?, offset: Int): Pair<String, Int>? {
    return if ((currentText == null) or (predictedText == null)) {
        null
    } else {
        val startDiffIdx = StringUtils.indexOfDifference(currentText!!.substring(0, offset), predictedText!!)
        // User has made some changes before the request, drop the suggestion
        if (offset != startDiffIdx) {
            return null
        }
        // There are no differences between the response and request
        if (startDiffIdx == -1) {
            return null
        }

        val currentTextTail = currentText.substring(startDiffIdx)
        val predictedTextTail = predictedText.substring(startDiffIdx)
        if (currentTextTail == predictedTextTail) {
            return null
        }

        val endDiffIdx = StringUtils.indexOfDifference(predictedTextTail.reversed(), currentTextTail.reversed())
        val predictedEndDiffIdx = predictedTextTail.length - endDiffIdx
        val currentEndDiffIdx = maxOf(currentTextTail.length - endDiffIdx, 0)
        if (predictedEndDiffIdx > 0) {
            Pair(predictedTextTail.substring(0, predictedEndDiffIdx), currentEndDiffIdx)
        } else {
            null
        }
    }
}
