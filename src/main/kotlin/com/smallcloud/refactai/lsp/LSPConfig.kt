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
            params.add(address)
        }
        if (port != null) {
            params.add("--http-port")
            params.add("$port")
        }
        apiKey?.let {
            params.add("--api-key")
            params.add(it)
        }
        return params + toCommonArgs()
    }

    fun toSafeLogString(): String {
        val safe = mutableListOf<String>()
        address?.let { safe.add("--address-url $it") }
        port?.let { safe.add("--http-port $it") }
        if (apiKey != null) safe.add("--api-key ***")
        return (safe + toCommonArgs()).joinToString(" ")
    }

    private fun toCommonArgs(): List<String> {
        val params = mutableListOf<String>()
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

    fun sameRuntimeSettings(other: LSPConfig?): Boolean {
        if (other == null) return false
        return address == other.address
            && apiKey == other.apiKey
            && clientVersion == other.clientVersion
            && useTelemetry == other.useTelemetry
            && deployment == other.deployment
            && ast == other.ast
            && vecdb == other.vecdb
            && astFileLimit == other.astFileLimit
            && vecdbFileLimit == other.vecdbFileLimit
            && insecureSSL == other.insecureSSL
            && experimental == other.experimental
    }

    val isValid: Boolean
        get() {
            return address != null
                && port != null
                && clientVersion != null
                && (!ast || (astFileLimit != null && astFileLimit!! > 0))
                && (!vecdb || (vecdbFileLimit != null && vecdbFileLimit!! > 0))
                // token must be present unless self-hosted
                && (deployment == DeploymentMode.SELF_HOSTED ||
                (apiKey != null && (deployment == DeploymentMode.CLOUD || deployment == DeploymentMode.HF)))
        }
}
