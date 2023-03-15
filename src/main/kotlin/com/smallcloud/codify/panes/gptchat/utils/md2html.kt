package com.smallcloud.codify.panes.gptchat.utils

import com.smallcloud.codify.panes.gptchat.structs.ParsedText
import com.vladsch.flexmark.ast.FencedCodeBlock
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.profile.pegdown.Extensions
import com.vladsch.flexmark.profile.pegdown.PegdownOptionsAdapter
import com.vladsch.flexmark.util.ast.Node
import java.util.regex.Pattern


private const val PROTOCOL = "(?i:http|https|rtsp|ftp)://"
private const val USER_INFO = ("(?:[a-zA-Z0-9\\$\\-\\_\\.\\+\\!\\*\\'\\(\\)"
        + "\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,64}(?:\\:(?:[a-zA-Z0-9\\$\\-\\_"
        + "\\.\\+\\!\\*\\'\\(\\)\\,\\;\\?\\&\\=]|(?:\\%[a-fA-F0-9]{2})){1,25})?\\@")
private const val PUNYCODE_TLD = "xn\\-\\-[\\w\\-]{0,58}\\w"
private const val UCS_CHAR = "[" +
        "\u00A0-\uD7FF" +
        "\uF900-\uFDCF" +
        "\uFDF0-\uFFEF" +
        "\uD800\uDC00-\uD83F\uDFFD" +
        "\uD840\uDC00-\uD87F\uDFFD" +
        "\uD880\uDC00-\uD8BF\uDFFD" +
        "\uD8C0\uDC00-\uD8FF\uDFFD" +
        "\uD900\uDC00-\uD93F\uDFFD" +
        "\uD940\uDC00-\uD97F\uDFFD" +
        "\uD980\uDC00-\uD9BF\uDFFD" +
        "\uD9C0\uDC00-\uD9FF\uDFFD" +
        "\uDA00\uDC00-\uDA3F\uDFFD" +
        "\uDA40\uDC00-\uDA7F\uDFFD" +
        "\uDA80\uDC00-\uDABF\uDFFD" +
        "\uDAC0\uDC00-\uDAFF\uDFFD" +
        "\uDB00\uDC00-\uDB3F\uDFFD" +
        "\uDB44\uDC00-\uDB7F\uDFFD" +
        "&&[^\u00A0[\u2000-\u200A]\u2028\u2029\u202F\u3000]]"
private val TLD_CHAR = "a-zA-Z$UCS_CHAR"
private val TLD = "($PUNYCODE_TLD|[$TLD_CHAR]{2,63})"
private const val LABEL_CHAR = "a-zA-Z0-9$UCS_CHAR"
private val IRI_LABEL = "[$LABEL_CHAR" + "](?:[" + LABEL_CHAR + "_\\-]{0,61}[" + LABEL_CHAR + "]){0,1}"
private val HOST_NAME = "($IRI_LABEL\\.)+$TLD"
private const val IP_ADDRESS_STRING = ("((25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9])\\.(25[0-5]|2[0-4]"
        + "[0-9]|[0-1][0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1]"
        + "[0-9]{2}|[1-9][0-9]|[1-9]|0)\\.(25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}"
        + "|[1-9][0-9]|[0-9]))")
private val DOMAIN_NAME_STR = "($HOST_NAME|$IP_ADDRESS_STRING)"
private const val WORD_BOUNDARY = "(?:\\b|$|^)"
private const val PORT_NUMBER = "\\:\\d{1,5}"
private const val PATH_AND_QUERY = ("[/\\?](?:(?:[" + LABEL_CHAR
        + ";/\\?:@&=#~" // plus optional query params
        + "\\-\\.\\+!\\*'\\(\\),_\\$])|(?:%[a-fA-F0-9]{2}))*")

val WEB_URL: Pattern = Pattern.compile(((((("("
        + "("
        + "(?:" + PROTOCOL) + "(?:" + USER_INFO + ")?" + ")?"
        + "(?:" + DOMAIN_NAME_STR) + ")"
        + "(?:" + PORT_NUMBER) + ")?"
        + ")"
        + "(" + PATH_AND_QUERY) + ")?"
        + WORD_BOUNDARY
        ) + ")"
        + "(/)?"
)


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
            val m = WEB_URL.matcher("")
            val html = renderer.render(it as Node)
            m.reset(html)
            var fragment = ""
            while (m.find()) {
                fragment = m.replaceAll("<a href=\"$1\">$1</a>")
            }
            res.add(ParsedText(null, if (fragment.isEmpty()) html else fragment, false))
        }
    }

    return res.toList()
}