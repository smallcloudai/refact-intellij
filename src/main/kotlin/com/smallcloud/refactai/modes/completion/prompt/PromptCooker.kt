package com.smallcloud.refactai.modes.completion.prompt

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.smallcloud.refactai.modes.EditorTextState
import info.debatty.java.stringsimilarity.Jaccard

data class PromptInfo(
    val fileName: String,
    val textLines: List<String>,
    val prompt: String,
    val distance: Double,
    val fileInfo: FileInformationEntry,
) {
    val text = textLines.joinToString("\n")
    fun cursors(): Pair<Int, Int> {
        val start = text.indexOf(prompt)
        val end = start + prompt.length
        return start to end
    }
}

object PromptCooker {
    private const val windowSize: Int = 40
    private val simAlg = Jaccard()

    fun cook(
        currentFileEditorHelper: EditorTextState,
        files: List<FileInformationEntry>,
        mostImportantFilesMaxCount: Int,
        lessImportantFilesMaxCount: Int,
        maxFileSize: Int
    ): List<PromptInfo> {
        val currentFile = FileDocumentManager.getInstance().getFile(currentFileEditorHelper.document)
        val currentExt = currentFile?.extension
        val mostImportantFiles = files
            .filter { it.file != currentFile }
            .filter { it.isOpened() }
            .sortedByDescending { it.lastEditorShown }
            .take(mostImportantFilesMaxCount)
        val lessImportantFiles = files
            .filter { it.file != currentFile }
            .filter { !mostImportantFiles.contains(it) }
            .sortedByDescending { it.lastUpdatedTs }
            .take(lessImportantFilesMaxCount)
        val filteredFiles = mostImportantFiles + lessImportantFiles

        val topIdx = minOf(
            currentFileEditorHelper.currentLineNumber,
            windowSize / 2
        )
        val botIdx = minOf(
            currentFileEditorHelper.lines.size - currentFileEditorHelper.currentLineNumber,
            windowSize / 2
        )
        val currentText = currentFileEditorHelper.lines.subList(
            currentFileEditorHelper.currentLineNumber - topIdx,
            botIdx + currentFileEditorHelper.currentLineNumber
        ).joinToString("\n")
        return compareOtherFiles(currentText, filteredFiles, currentExt, maxFileSize)
    }

    private fun compareOtherFiles(
        currentText: String,
        files: List<FileInformationEntry>,
        currentExt: String?,
        maxFileSize: Int,
    ): List<PromptInfo> {
        return files
            .filter { it.file.extension == currentExt && it.file.length < maxFileSize }
            .map { info -> info to getLinesFromFile(info.file) }
            .filter { it.second != null }
            .mapNotNull { (info, lines) ->
                lines!!
                    .windowed(windowSize, step = windowSize / 2, partialWindows = true) {
                        it.joinToString("\n")
                    }
                    .map {
                        PromptInfo(
                            fileName = info.projectRelativeFilePath,
                            textLines = lines,
                            prompt = it,
                            distance = simAlg.distance(it, currentText),
                            fileInfo = info
                        )
                    }
                    .minByOrNull { it.distance }
            }
    }

    private fun getLinesFromFile(file: VirtualFile): List<String>? {
        val reader = file.inputStream.bufferedReader()
        return try {
            reader.use { it.readLines() }
        } catch (e: Exception) {
            null
        }
    }
}
