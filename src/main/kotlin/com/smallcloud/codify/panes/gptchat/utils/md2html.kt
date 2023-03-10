package com.smallcloud.codify.panes.gptchat.utils

import com.smallcloud.codify.panes.gptchat.structs.ParsedText
import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.profile.pegdown.Extensions
import com.vladsch.flexmark.profile.pegdown.PegdownOptionsAdapter
import com.vladsch.flexmark.util.ast.Node

fun md2html(text: String): List<ParsedText> {
    val options = PegdownOptionsAdapter.flexmarkOptions(
            Extensions.ALL
    )
    val parser = Parser.builder(options).build();
    val renderer: HtmlRenderer = HtmlRenderer.builder(options).build()
    val document: Node = parser.parse(text)
    val res = mutableListOf<ParsedText>()
    document.children.forEach {
        if (it is FencedCodeBlock) {
            res.add(ParsedText(it.lastChild?.chars.toString().dropLast(1), renderer.render(it as Node), true))
        } else {
            res.add(ParsedText(null, renderer.render(it as Node), false))
        }
    }

    return res.toList()
}