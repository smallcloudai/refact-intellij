package com.smallcloud.refactai.lsp

import com.google.gson.annotations.SerializedName

data class LSPScratchpadInfo(
        @SerializedName("default_system_message") var defaultSystemMessage: String
)

data class LSPModelInfo(
        @SerializedName("default_scratchpad") var defaultScratchpad: String,
        @SerializedName("n_ctx") var nCtx: Int,
        @SerializedName("similar_models") var similarModels: List<String>,
        @SerializedName("supports_scratchpads") var supportsScratchpads: Map<String, LSPScratchpadInfo?>,
        @SerializedName("supports_stop") var supportsStop: Boolean,
        @SerializedName("supports_tools") var supportsTools: Boolean?,
)

data class LSPCapabilities(
        @SerializedName("cloud_name") var cloudName: String = "",
        @SerializedName("code_chat_default_model") var codeChatDefaultModel: String = "",
        @SerializedName("code_chat_models") var codeChatModels: Map<String, LSPModelInfo> = mapOf(),
        @SerializedName("code_completion_default_model") var codeCompletionDefaultModel: String = "",
        @SerializedName("code_completion_models") var codeCompletionModels: Map<String, LSPModelInfo> = mapOf(),
        @SerializedName("endpoint_style") var endpointStyle: String = "",
        @SerializedName("endpoint_template") var endpointTemplate: String = "",
        @SerializedName("running_models") var runningModels: List<String> = listOf(),
        @SerializedName("telemetry_basic_dest") var telemetryBasicDest: String = "",
        @SerializedName("tokenizer_path_template") var tokenizerPathTemplate: String = "",
        @SerializedName("tokenizer_rewrite_path") var tokenizerRewritePath: Map<String, String> = mapOf(),
)