package com.smallcloud.refactai.lsp

import com.smallcloud.refactai.struct.DeploymentMode

data class LSPConfig(
        val address: String? = null,
        val port: Int? = null,
        var apiKey: String? = null,
        var clientVersion: String? = null,
        var useTelemetry: Boolean = false,
        var deployment: DeploymentMode = DeploymentMode.CLOUD,
        var ast: Boolean = false,
        var vecdb: Boolean = false
) {
    fun toArgs(): List<String> {
        val params = mutableListOf<String>()
        if (address != null) {
            params.add("--address-url")
            params.add("$address")
        }
        if (port != null) {
            params.add("--http-port")
            params.add("$port")
        }
        if (apiKey != null) {
            params.add("--api-key")
            params.add("$apiKey")
        }
        if (clientVersion != null) {
            params.add("--enduser-client-version")
            params.add("$clientVersion")
        }
        if (useTelemetry) {
            params.add("--basic-telemetry")
        }
        if (ast) {
            params.add("--ast")
        }
        if (vecdb) {
            params.add("--vecdb")
        }
        return params
    }

    val isValid: Boolean
        get() {
            return address != null
                && port != null
                && clientVersion != null
                // token must be if we are not selfhosted
                && (deployment == DeploymentMode.SELF_HOSTED ||
                (apiKey != null && (deployment == DeploymentMode.CLOUD || deployment == DeploymentMode.HF)))
        }
}