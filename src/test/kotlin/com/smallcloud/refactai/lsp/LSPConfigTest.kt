package com.smallcloud.refactai.lsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LSPConfigTest {
    @Test
    fun toArgsDoesNotIncludeUnsupportedClientVersionFlag() {
        val args = LSPConfig(port = 12345, ast = false, vecdb = false).toArgs()

        assertEquals(listOf("--http-port", "12345"), args)
        assertFalse(args.contains("--enduser-client-version"))
    }
}
