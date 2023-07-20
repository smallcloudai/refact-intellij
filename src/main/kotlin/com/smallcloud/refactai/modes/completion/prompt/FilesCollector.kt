package com.smallcloud.refactai.modes.completion.prompt

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.ObjectUtils
import com.smallcloud.refactai.privacy.Privacy
import com.smallcloud.refactai.privacy.PrivacyService
import java.nio.file.Path
import kotlin.io.path.relativeTo
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import kotlin.io.path.Path as makePath

data class FileInformationEntry(
    val file: VirtualFile,
    var lastEditorShown: Long,
    val projectRelativeFilePath: String,
    private val fileEditorManager: FileEditorManager,
) {
    val lastUpdatedTs: Long
        get() = file.timeStamp

    fun getEditor(): FileEditor? = fileEditorManager.getSelectedEditor(file)
    fun isOpened(): Boolean = fileEditorManager.isFileOpen(file)
}

class FilesCollector(
    private val project: Project
) : FileEditorManagerListener {
    private val fileEditorManager: FileEditorManager = FileEditorManager.getInstance(project)
    private var filesInformation: LinkedHashSet<FileInformationEntry> = linkedSetOf()
    private val projectPaths: List<Path> =
        ModuleManager.getInstance(project).modules
            .map {
                ModuleRootManager.getInstance(it)
            }
            .map {
                it.sourceRoots.map {
                    makePath(it.path)
                }
            }
            .flatten()

    init {
        FilenameIndex.getAllFilenames(project).mapNotNull {
                FilenameIndex.getVirtualFilesByName(it, ProjectScope.getProjectScope(project)).find { virtualFile ->
                        virtualFile.isValid && virtualFile.exists() && virtualFile.isInLocalFileSystem && !virtualFile.isDirectory
                    }
            }.forEach {
                makeEntry(it, projectPaths)
            }

        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    }

    private fun makeEntry(file: VirtualFile, projectPaths: List<Path>) {
        val maybeInfo = filesInformation.find { info -> file == info.file }
        if (maybeInfo != null) {
            return
        }

        val filePath = makePath(file.path)
        val relativeFilePath: String = if (projectPaths.isNotEmpty()) {
            projectPaths.map { filePath.relativeTo(it).toString() }.minBy { it.length }
        } else {
            filePath.toString()
        }
        val entry = FileInformationEntry(file, 0, relativeFilePath, fileEditorManager)
        filesInformation.add(entry)
        entry.getEditor()?.let { editor ->
            if (entry.isOpened()) {
                entry.lastEditorShown = System.currentTimeMillis()
            }

            ObjectUtils.consumeIfCast((editor as PsiAwareTextEditorImpl).editor, EditorEx::class.java) {
                it?.addFocusListener(object : FocusChangeListener {
                    override fun focusGained(editor: Editor) {
                        entry.lastEditorShown = System.currentTimeMillis()
                    }
                })
            }
        }
    }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        makeEntry(file, projectPaths)
    }

    fun collect(): List<FileInformationEntry> {
        return filesInformation.filter {
            it.file.isValid && it.file.exists() && (PrivacyService.instance.getPrivacy(it.file) != Privacy.DISABLED
                    || InferenceGlobalContext.isSelfHosted)
        }
    }

    companion object {
        private val FILES_COLLECTORS = Key.create<FilesCollector>("FILES_COLLECTORS")

        fun getInstance(project: Project): FilesCollector {
            if (!FILES_COLLECTORS.isIn(project)) {
                FILES_COLLECTORS[project] = FilesCollector(project)
            }
            return FILES_COLLECTORS[project]
        }
    }
}
