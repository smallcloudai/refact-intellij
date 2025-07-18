package com.smallcloud.refactai.lsp

import com.google.gson.Gson
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.modules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.util.application
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.smallcloud.refactai.io.ConnectionStatus
import java.nio.file.Paths
import kotlin.io.path.Path
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance as getLSPProcessHolder

fun findRoots(paths: List<String>): List<String> {
    val sortedPaths = paths.map { Paths.get(it).normalize() }.sortedBy { it.nameCount }

    val roots = mutableSetOf<String>()

    for (path in sortedPaths) {
        val pathStr = path.toString()
        if (roots.none { pathStr.startsWith("$it/") || pathStr == it }) {
            roots.add(pathStr)
        }
    }
    return roots.toList()
}

fun lspProjectInitialize(lsp: LSPProcessHolder, project: Project) {
    val projectRootManager = ProjectRootManager.getInstance(project)
    val projectRoots = projectRootManager.contentRoots.map { it.toString() }.ifEmpty {
        val listOfFiles: MutableList<String> = mutableListOf(project.basePath.toString())
        application.runReadAction {
            project.modules.forEach { module ->
                val rootManager = ModuleRootManager.getInstance(module)
                rootManager.fileIndex.iterateContent { vfile ->
                    if (rootManager.fileIndex.isInContent(vfile) ||
                        rootManager.fileIndex.isInSourceContent(vfile) ||
                        rootManager.fileIndex.isInTestSourceContent(vfile)
                    ) {
                        listOfFiles.add(vfile.toNioPath().toString())
                    }
                    true
                }
            }
        }
        return@ifEmpty findRoots(listOfFiles)
    }.ifEmpty { listOf(project.basePath) }
    val url = lsp.url.resolve("/v1/lsp-initialize")
    val data = Gson().toJson(
        mapOf(
            "project_roots" to projectRoots,
        )
    )

    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.CONNECTED
        InferenceGlobalContext.lastErrorMsg = null
    }, failedDataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        if (it != null) {
            InferenceGlobalContext.lastErrorMsg = it.message
        }
    })
}

fun lspDocumentDidChanged(project: Project, docUrl: String, text: String) {
    val url = getLSPProcessHolder(project)?.url?.resolve("/v1/lsp-did-changed") ?: return
    val data = Gson().toJson(
        mapOf(
            "uri" to docUrl,
            "text" to text,
        )
    )

    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.CONNECTED
        InferenceGlobalContext.lastErrorMsg = null
    }, failedDataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        if (it != null) {
            InferenceGlobalContext.lastErrorMsg = it.message
        }
    })
}

private fun getVirtualFile(editor: Editor): VirtualFile? {
    return FileDocumentManager.getInstance().getFile(editor.document)
}

fun lspSetActiveDocument(editor: Editor) {
    val project = editor.project ?: return
    val vFile = getVirtualFile(editor) ?: return
    if (!vFile.exists()) return

    val url = getLSPProcessHolder(project)?.url?.resolve("/v1/lsp-set-active-document") ?: return
    val data = Gson().toJson(
        mapOf(
            "uri" to vFile.url,
        )
    )

    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.CONNECTED
        InferenceGlobalContext.lastErrorMsg = null
    }, failedDataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        if (it != null) {
            InferenceGlobalContext.lastErrorMsg = it.message
        }
    })
}


fun lspGetCodeLens(editor: Editor): String {
    val project = editor.project!!
    val url = getLSPProcessHolder(project)?.url?.resolve("/v1/code-lens") ?: return ""
    val data = Gson().toJson(
        mapOf(
            "uri" to editor.virtualFile.url,
        )
    )

    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.CONNECTED
        InferenceGlobalContext.lastErrorMsg = null
    }, failedDataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        if (it != null) {
            InferenceGlobalContext.lastErrorMsg = it.message
        }
    }).let {
        val res = it.get()!!.get() as String
        return res
    }
}

fun lspGetCommitMessage(project: Project, diff: String, currentMessage: String): String {
    val url = getLSPProcessHolder(project)?.url?.resolve("/v1/commit-message-from-diff") ?: return ""
    val data = Gson().toJson(
        mapOf(
            "diff" to diff,
            "text" to currentMessage,
        )
    )
    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.CONNECTED
        InferenceGlobalContext.lastErrorMsg = null
    }, failedDataReceiveEnded = {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        if (it != null) {
            InferenceGlobalContext.lastErrorMsg = it.message
        }
    }).let {
        val res = it.get()!!.get() as String
        return res
    }
}
