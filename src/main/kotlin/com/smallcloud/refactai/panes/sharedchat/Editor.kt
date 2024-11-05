package com.smallcloud.refactai.panes.sharedchat

import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ui.UIUtil
import com.smallcloud.refactai.account.AccountManager.Companion.instance
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.panes.sharedchat.browser.getActionKeybinding
import com.smallcloud.refactai.settings.AppSettingsState


class Editor (val project: Project) {
    private val lsp: LSPProcessHolder = LSPProcessHolder.getInstance(project)!!
    private fun getLanguage(fm: FileEditorManager): Language? {
        val editor = fm.selectedTextEditor
        val language = editor?.document?.let {
            PsiDocumentManager.getInstance(project).getPsiFile(it)?.language
        }
        return language
    }

    fun getSelectedSnippet(cb: (Events.Editor.Snippet?) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && FileEditorManager.getInstance(project).selectedFiles.isNotEmpty()) {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor
                val file = fileEditorManager.selectedFiles[0]
                val path = file.path
                val name = file.name
                val language = this.getLanguage(fileEditorManager)?.id
                val caretModel = editor?.caretModel

                val selection = caretModel?.currentCaret?.selectionRange
                val range = TextRange(selection?.startOffset ?: 0, selection?.endOffset ?: 0)

                val code = editor?.document?.getText(range)
                if (language == null || code == null) {
                    cb(Events.Editor.Snippet())
                } else {
                    val snippet = Events.Editor.Snippet(language, code, path, name)
                    cb(snippet)
                }
            } else {
                cb(null)
            }
        }
    }

    fun getUserConfig(): Events.Config.UpdatePayload {
        val hasAst = AppSettingsState.instance.astIsEnabled
        val hasVecdb = AppSettingsState.instance.vecdbIsEnabled
        val features = Events.Config.Features(hasAst, hasVecdb)
        val isDarkMode = UIUtil.isUnderDarcula()
        val mode = if (isDarkMode) "dark" else "light"
        val themeProps = Events.Config.ThemeProps(mode)
        val apiKey = instance.apiKey
        val lspPort = lsp.url.port
        val addressURL = AppSettingsState.instance.userInferenceUri ?: ""
        val keyBindings = Events.Config.KeyBindings(getActionKeybinding("ForceCompletionAction"))

        return Events.Config.UpdatePayload(features, themeProps, lspPort, apiKey, addressURL, keyBindings)
    }

    fun getActiveFileInfo(cb: (Events.ActiveFile.FileInfo) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && FileEditorManager.getInstance(project).selectedFiles.isNotEmpty()) {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor

                val cursor = editor?.caretModel?.offset
                val virtualFile = fileEditorManager.selectedFiles[0]
                val filePath = virtualFile.path
                val fileName = virtualFile.name

                val selection = editor?.caretModel?.currentCaret?.selectionRange
                val range = TextRange(selection?.startOffset ?: 0, selection?.endOffset ?: 0)
                val line1 = selection?.startOffset?.let { editor.offsetToLogicalPosition(it).line } ?: 0
                val line2 = selection?.endOffset?.let { editor.offsetToLogicalPosition(it).line } ?: 0

                val code = editor?.document?.getText(range)
                val canPaste = selection != null && !selection.isEmpty

                val fileInfo = Events.ActiveFile.FileInfo(
                    fileName,
                    filePath,
                    canPaste,
                    cursor = cursor,
                    line1 = line1,
                    line2 = line2,
                    content = code,
                )
                cb(fileInfo)
            } else {
                val fileInfo = Events.ActiveFile.FileInfo()
                cb(fileInfo)
            }
        }
    }
}