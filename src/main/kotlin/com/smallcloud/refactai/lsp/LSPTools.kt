package com.smallcloud.refactai.lsp

data class ToolFunctionParameters(
    val properties: Map<String, Map<String, String>>,
    val type: String,
    val required: Array<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ToolFunctionParameters

        if (properties != other.properties) return false
        if (type != other.type) return false
        if (!required.contentEquals(other.required)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = properties.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + required.contentHashCode()
        return result
    }
}

data class ToolFunction(val description: String, val name: String, val parameters: ToolFunctionParameters)
data class Tool(val function: ToolFunction, val type: String);