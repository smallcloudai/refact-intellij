package com.smallcloud.refactai.panes.gptchat.utils

import com.smallcloud.refactai.panes.gptchat.structs.ParsedText
import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.profile.pegdown.Extensions
import com.vladsch.flexmark.profile.pegdown.PegdownOptionsAdapter
import com.vladsch.flexmark.util.ast.Node


fun md2html(text: String): List<ParsedText> {
    val options = PegdownOptionsAdapter.flexmarkOptions(true,
            Extensions.ALL_WITH_OPTIONALS or Extensions.GITHUB_WIKI_COMPATIBLE
    )
    val parser = Parser.builder(options).build();
    val renderer: HtmlRenderer = HtmlRenderer.builder(options).build()
    val document: Node = parser.parse(text)
    val res = mutableListOf<ParsedText>()
    document.children.forEach {
        if (it is FencedCodeBlock) {
            val rawText = it.lastChild?.chars.toString().dropLast(1)
            val html = renderer.render(parser.parse(it.chars))
            if (res.isEmpty() || !res.last().isCode) {
                res.add(ParsedText(rawText, html, true))
            } else {
                res.last().rawText += "\n${rawText}"
                res.last().htmlText += html
            }
        } else {
            val rawText = it.chars.toString()
            val html = renderer.render(parser.parse(it.chars))
            if (res.isEmpty() || res.last().isCode) {
                res.add(ParsedText(rawText, html, false))
            } else {
                res.last().rawText += "\n${rawText}"
                res.last().htmlText += html
            }
        }
    }

    return res.toList()
}