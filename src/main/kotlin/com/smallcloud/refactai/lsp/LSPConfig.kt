package com.smallcloud.refactai.lsp

data class LSPConfig(
    var port: Int? = null,
    var ast: Boolean = true,
    var astFileLimit: Int? = null,
    var vecdb: Boolean = true,
    var vecdbFileLimit: Int? = null,
    var insecureSSL: Boolean = false,
    val experimental: Boolean = false
) {
    fun toArgs(): List<String> {
        val params = mutableListOf<String>()
        if (port != null) {
            params.add("--http-port")
            params.add("$port")
        }
        return params + toCommonArgs()
    }

    fun toSafeLogString(): String {
        val safe = mutableListOf<String>()
        port?.let { safe.add("--http-port $it") }
        return (safe + toCommonArgs()).joinToString(" ")
    }

    private fun toCommonArgs(): List<String> {
        val params = mutableListOf<String>()
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
        return ast == other.ast
            && vecdb == other.vecdb
            && astFileLimit == other.astFileLimit
            && vecdbFileLimit == other.vecdbFileLimit
            && insecureSSL == other.insecureSSL
            && experimental == other.experimental
    }

    val isValid: Boolean
        get() {
            return port != null
                && (!ast || (astFileLimit != null && astFileLimit!! > 0))
                && (!vecdb || (vecdbFileLimit != null && vecdbFileLimit!! > 0))
        }
}
