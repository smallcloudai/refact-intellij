package com.smallcloud.codify.modes.completion

class CompletionRenderer {
    fun render() {
        val currentText = editor.document.text
        val predictedText = prediction.choices[0].files[request_body.cursorFile]
        inlineData = difference(currentText, predictedText, offset) ?: return
        val inline = inlineData!!.first
        if (inline.isEmpty()) return


        val lines = inline.split("\n")

        try {
            editor.document.startGuardedBlockChecking();
            inlayer.render(lines, offset)
        } finally {
            editor.document.stopGuardedBlockChecking();
        }
    }
}