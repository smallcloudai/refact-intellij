package com.smallcloud.refactai.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import java.net.URI

class ConnectionManager: Disposable {
    private val connections = mutableMapOf<URI, Disposable>()

    private fun addConnection(uri: URI, isCustomURI: Boolean = false) {
        if (connections[uri] != null) return
        connections[uri] = AsyncConnection(uri, isCustomURI)
    }

    fun getAsyncConnection(uri: URI,
                           createIfNeed: Boolean = true): AsyncConnection? {
        if (createIfNeed) {
            addConnection(uri, true)
        }
        return connections[uri] as AsyncConnection?
    }

    override fun dispose() {
        connections.forEach {
            it.value.dispose()
        }
    }

    companion object {
        @JvmStatic
        val instance: ConnectionManager
            get() = ApplicationManager.getApplication().getService(ConnectionManager::class.java)
    }
}