package com.smallcloud.refactai.lsp

import com.google.gson.Gson
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.instance as LSPProcessHolder

fun lspProjectInitialize(projectRoots: List<String>) {
    val url = LSPProcessHolder.url.resolve("/v1/lsp-initialize")
    val data = Gson().toJson(
        mapOf(
            "project_roots" to projectRoots,
        )
    )

    InferenceGlobalContext.connection.post(url, data)
}

fun lspDocumentDidChanged(docUrl: String, text: String) {
    val url = LSPProcessHolder.url.resolve("/v1/lsp-did-changed")
    val data = Gson().toJson(
        mapOf(
            "uri" to docUrl,
            "text" to text,
        )
    )

    InferenceGlobalContext.connection.post(url, data)
}