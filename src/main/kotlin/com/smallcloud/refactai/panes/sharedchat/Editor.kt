package com.smallcloud.refactai.panes.sharedchat

import com.intellij.ide.ui.LafManager
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.application
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.panes.sharedchat.browser.getActionKeybinding
import com.smallcloud.refactai.settings.AppSettingsState
import java.io.File
import java.nio.file.Paths


class Editor (val project: Project) {
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
                if (code.isNullOrEmpty()) {
                    cb(Events.Editor.Snippet())
                } else {
                    val snippet = Events.Editor.Snippet(language ?: "text", code, path, name)
                    cb(snippet)
                }
            } else {
                cb(null)
            }
        }
    }

    private fun findRoots(paths: List<String>): List<String> {
        val sortedPaths = paths.map { Paths.get(it).normalize() }.sortedBy { it.nameCount }
        val roots = mutableListOf<java.nio.file.Path>()
        for (path in sortedPaths) {
            if (roots.none { path.startsWith(it) }) {
                roots.add(path)
            }
        }
        return roots.map { it.toString() }
    }

    private fun getWorkspaceRoots(): List<String> {
        val projectRootManager = ProjectRootManager.getInstance(project)
        return projectRootManager.contentRoots.mapNotNull { root ->
            root.path.takeIf { root.isInLocalFileSystem && it.isNotBlank() }
        }.ifEmpty {
            val listOfFiles: MutableList<String> = mutableListOf<String>().also { list ->
                project.basePath?.let { list.add(it) }
            }
            application.runReadAction {
                project.modules.forEach { module ->
                    val rootManager = ModuleRootManager.getInstance(module)
                    rootManager.fileIndex.iterateContent { vfile ->
                        if (vfile.isInLocalFileSystem &&
                            (rootManager.fileIndex.isInContent(vfile) ||
                                rootManager.fileIndex.isInSourceContent(vfile) ||
                                rootManager.fileIndex.isInTestSourceContent(vfile))
                        ) {
                            listOfFiles.add(vfile.toNioPath().toString())
                        }
                        true
                    }
                }
            }
            findRoots(listOfFiles)
        }.ifEmpty { listOfNotNull(project.basePath) }
            .map { path -> runCatching { File(path).canonicalPath }.getOrElse { path } }
    }

    fun getCurrentProject(): Events.CurrentProject.SetCurrentProjectPayload {
        val workspaceRoots = getWorkspaceRoots().takeIf { it.isNotEmpty() }
        return Events.CurrentProject.SetCurrentProjectPayload(project.name, workspaceRoots)
    }

    fun getUserConfig(): Events.Config.UpdatePayload {
        val hasAst = AppSettingsState.instance.astIsEnabled
        val hasVecdb = AppSettingsState.instance.vecdbIsEnabled
        val hasExperimental = AppSettingsState.instance.experimentalLspFlagEnabled
        val features = Events.Config.Features(hasAst, hasVecdb, knowledge = hasExperimental)
        val isDarkMode = LafManager.getInstance().currentUIThemeLookAndFeel.isDark
        val mode = if (isDarkMode) "dark" else "light"
        val themeProps = Events.Config.ThemeProps(mode)
        val lspHolder = LSPProcessHolder.getInstance(project)
        val rawPort = lspHolder?.baseUrlOrNull()?.port ?: 0
        if (rawPort <= 0) {
            lspHolder?.ensureStartedAsync("editor-config-request")
        }
        val lspPort = if (rawPort > 0) rawPort else 0
        val keyBindings = Events.Config.KeyBindings(getActionKeybinding("ForceCompletionAction"))

        return Events.Config.UpdatePayload(features, themeProps, lspPort, keyBindings)
    }

    fun getActiveFileInfo(cb: (Events.ActiveFile.FileInfo) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && FileEditorManager.getInstance(project).selectedFiles.isNotEmpty()) {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor

                val cursorOffset = editor?.caretModel?.offset
                val cursor = cursorOffset?.let { editor.offsetToLogicalPosition(it).line + 1 }
                val virtualFile = fileEditorManager.selectedFiles[0]
                val filePath = virtualFile.path
                val fileName = virtualFile.name

                val selection = editor?.caretModel?.currentCaret?.selectionRange
                val range = TextRange(selection?.startOffset ?: 0, selection?.endOffset ?: 0)
                val line1 = selection?.startOffset?.let { editor.offsetToLogicalPosition(it).line + 1 } ?: 0
                val line2 = selection?.endOffset?.let { editor.offsetToLogicalPosition(it).line + 1 } ?: 0

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
