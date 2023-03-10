package com.smallcloud.codify.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import java.net.URI

class ConnectionManager: Disposable {
    private val connections = mutableMapOf<URI, Disposable>()

    private fun addConnection(uri: URI, isAsync: Boolean = false, isCustomURI: Boolean = false) {
        if (connections[uri] != null) return
        if (isAsync) {
            connections[uri] = AsyncConnection(uri, isCustomURI)
        } else {
            connections[uri] = Connection(uri, isCustomURI)
        }
    }
    fun getConnection(uri: URI,
                      isCustomURI: Boolean = false,
                      createIfNeed: Boolean = true): Connection? {
        if (createIfNeed) {
            addConnection(uri, false, isCustomURI)
        }
        return connections[uri] as Connection?
    }

    fun getAsyncConnection(uri: URI,
                           isCustomURI: Boolean = false,
                           createIfNeed: Boolean = true): AsyncConnection? {
        if (createIfNeed) {
            addConnection(uri, true, isCustomURI)
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