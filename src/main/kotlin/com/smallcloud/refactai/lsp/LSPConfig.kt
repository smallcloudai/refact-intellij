package com.smallcloud.refactai.lsp

import com.smallcloud.refactai.struct.DeploymentMode

data class LSPConfig(
    val address: String? = null,
    var port: Int? = null,
    var apiKey: String? = null,
    var clientVersion: String? = null,
    var useTelemetry: Boolean = false,
    var deployment: DeploymentMode = DeploymentMode.CLOUD,
    var ast: Boolean = true,
    var astFileLimit: Int? = null,
    var vecdb: Boolean = true,
    var vecdbFileLimit: Int? = null,
    var insecureSSL: Boolean = false,
    val experimental: Boolean = false
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
        if (ast && astFileLimit != null) {
            params.add("--ast-max-files")
            params.add("$astFileLimit")
        }
        if (vecdb) {
            params.add("--vecdb")
        }
        if (vecdb && vecdbFileLimit != null) {
            params.add("--vecdb-max-files")
            params.add("$vecdbFileLimit")
        }
        if (insecureSSL) {
            params.add("--insecure")
        }
        if (experimental) {
            params.add("--experimental")
        }
        return params
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LSPConfig

        if (address != other.address) return false
        if (apiKey != other.apiKey) return false
        if (clientVersion != other.clientVersion) return false
        if (useTelemetry != other.useTelemetry) return false
        if (deployment != other.deployment) return false
        if (ast != other.ast) return false
        if (vecdb != other.vecdb) return false
        if (astFileLimit != other.astFileLimit) return false
        if (vecdbFileLimit != other.vecdbFileLimit) return false
        if (experimental != other.experimental) return false

        return true
    }

    val isValid: Boolean
        get() {
            return address != null
                && port != null
                && clientVersion != null
                && (astFileLimit != null && astFileLimit!! > 0)
                && (vecdbFileLimit != null && vecdbFileLimit!! > 0)
                // token must be if we are not selfhosted
                && (deployment == DeploymentMode.SELF_HOSTED ||
                (apiKey != null && (deployment == DeploymentMode.CLOUD || deployment == DeploymentMode.HF)))
        }
}
