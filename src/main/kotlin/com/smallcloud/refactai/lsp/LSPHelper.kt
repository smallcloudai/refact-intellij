package com.smallcloud.refactai.lsp

import com.google.gson.Gson
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
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
    val url = getLSPProcessHolder(project).url.resolve("/v1/lsp-did-changed")
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