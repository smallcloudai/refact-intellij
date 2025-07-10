package com.smallcloud.refactai.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery

/**
 * Manages JavaScript query objects to ensure proper disposal and prevent memory leaks.
 * This class tracks all created JS queries and provides centralized disposal.
 */
class JSQueryManager(private val browser: JBCefBrowser) : Disposable {
    private val logger = Logger.getInstance(JSQueryManager::class.java)
    private val queries = mutableListOf<JBCefJSQuery>()
    private var disposed = false

    fun createQuery(handler: (String) -> JBCefJSQuery.Response?): JBCefJSQuery {
        if (disposed) {
            throw IllegalStateException("JSQueryManager has been disposed")
        }

        try {
            val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
            query.addHandler(handler)
            synchronized(queries) {
                queries.add(query)
            }
            logger.info("Created JS query. Total queries: ${queries.size}")
            return query
        } catch (e: Exception) {
            logger.error("Failed to create JS query", e)
            throw e
        }
    }

    fun createStringQuery(handler: (String) -> Unit): JBCefJSQuery {
        return createQuery { msg ->
            try {
                handler(msg)
                null // No response needed
            } catch (e: Exception) {
                logger.warn("Error in JS query handler", e)
                null
            }
        }
    }

    override fun dispose() {
        if (disposed) {
            return
        }
        disposed = true
        synchronized(queries) {
            logger.info("Disposing JSQueryManager with ${queries.size} queries")
            queries.forEach { query ->
                try {
                    query.dispose()
                } catch (e: Exception) {
                    logger.warn("Error disposing JS query during cleanup", e)
                }
            }
            queries.clear()
            logger.info("JSQueryManager disposal completed")
        }
    }

    fun isDisposed(): Boolean = disposed
}
