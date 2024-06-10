package com.smallcloud.refactai.lsp

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class LspToolsTest {
    @Test
    fun parseResponse() {
        val query = """{"description": "Single line, paragraph or code sample.", "type": "string"}"""
        val parameters = """{"properties": {"query":$query}, "required": ["query"], "type": "object"}"""
        val tool = """{"description": "Find similar pieces of code using vector database","name": "workspace","parameters":$parameters}"""
        val res = """[{"function": $tool,"type": "function"}]"""

        val expectedParameters = ToolFunctionParameters(
            properties = mapOf("query" to mapOf("description" to "Single line, paragraph or code sample.", "type" to "string")),
            type = "object",
            required = arrayOf("query")
        )

        val expectFunction = ToolFunction(
            description = "Find similar pieces of code using vector database",
            name = "workspace",
            parameters = expectedParameters
        )
        val expectedTool = Tool(function = expectFunction, type="function")

        print(res)

        val result = Gson().fromJson(res, Array<Tool>::class.java)

        assertEquals(expectedTool, result.first())


    }
}
