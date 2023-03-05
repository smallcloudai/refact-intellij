package com.smallcloud.codify.panes.gptchat.utils

import com.intellij.openapi.project.Project
import com.smallcloud.codify.utils.getLastUsedProject
import org.intellij.plugins.markdown.ui.preview.html.MarkdownUtil

object HtmlUtil {
    private fun html(content: String): String {
        var content: String? = content
        content = content ?: ""
        return "<html>$content</html>"
    }

    private fun head(content: String): String {
        var content: String? = content
        content = content ?: ""
        return "<head>$content</head>"
    }

    private fun body(content: String): String {
        var content: String? = content
        content = content ?: ""
        return "<body>$content</body>"
    }
//
//    private fun h1(content: String): String {
//        var content: String? = content
//        content = content ?: ""
//        return "<h1>$content</h1>"
//    }
//
//    private fun h2(content: String): String {
//        var content: String? = content
//        content = content ?: ""
//        return "<h2>$content</h2>"
//    }
//
//    private fun h3(content: String): String {
//        var content: String? = content
//        content = content ?: ""
//        return "<h3>$content</h3>"
//    }
//
//    private fun h4(content: String): String {
//        var content: String? = content
//        content = content ?: ""
//        return "<h4>$content</h4>"
//    }
//
//    private fun h5(content: String): String {
//        var content: String? = content
//        content = content ?: ""
//        return "<h5>$content</h5>"
//    }
//
    fun create(content: String): String {
        return html(head("") + body(content))
    }

    fun md2html(source: String): String {
        val project: Project = getLastUsedProject()
        return if (project.projectFile != null) {
            MarkdownUtil.generateMarkdownHtml(
                project.projectFile!!,
                source, project
            )
        } else if (project.workspaceFile != null) {
            MarkdownUtil.generateMarkdownHtml(
                project.workspaceFile!!,
                source, project
            )
        } else {
            create(source)
        }
    }
}