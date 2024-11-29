package com.smallcloud.refactai.lsp

import com.google.gson.Gson
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance as getLSPProcessHolder

fun lspProjectInitialize(lsp: LSPProcessHolder, project: Project) {
    val projectRoots = ProjectRootManager.getInstance(project).contentRoots.map { it.toString() }
    val url = lsp.url.resolve("/v1/lsp-initialize")
    val data = Gson().toJson(
        mapOf(
            "project_roots" to projectRoots,
        )
    )

    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded={
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

    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded={
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

    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded={
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

    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded={
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
    InferenceGlobalContext.connection.post(url, data, dataReceiveEnded={
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
