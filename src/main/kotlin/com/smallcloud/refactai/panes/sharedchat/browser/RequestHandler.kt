package com.smallcloud.refactai.panes.sharedchat.browser

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.smallcloud.refactai.utils.ResourceCache
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.callback.CefSchemeHandlerFactory
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.IOException
import java.io.InputStream
import java.net.URLConnection

class RequestHandlerFactory : CefSchemeHandlerFactory {
    override fun create(
        browser: CefBrowser?,
        frame: CefFrame?,
        schemeName: String,
        request: CefRequest
    ): CefResourceHandler {
        return RefactChatResourceHandler()
    }
}

data object ClosedConnection : ResourceHandlerState() {
    override fun getResponseHeaders(
        cefResponse: CefResponse,
        responseLength: IntRef,
        redirectUrl: StringRef
    ) {
        cefResponse.status = 404
    }
}

sealed class ResourceHandlerState {
    open fun getResponseHeaders(
        cefResponse: CefResponse,
        responseLength: IntRef,
        redirectUrl: StringRef
    ) {
    }

    open fun readResponse(
        dataOut: ByteArray,
        bytesToRead: Int,
        bytesRead: IntRef,
        callback: CefCallback
    ): Boolean = false

    open fun close() {}
}

class OpenedConnection(private val connection: URLConnection?) :
    ResourceHandlerState() {

    private val inputStream: InputStream? by lazy {
        connection?.inputStream
    }

    override fun getResponseHeaders(
        cefResponse: CefResponse,
        responseLength: IntRef,
        redirectUrl: StringRef
    ) {
        try {
            if (connection != null) {
                val url = connection.url.toString()
                when {
                    url.contains(".css") -> cefResponse.mimeType = "text/css"
                    url.contains(".js") -> cefResponse.mimeType = "text/javascript"
                    url.contains(".html") -> cefResponse.mimeType = "text/html"
                    else -> cefResponse.mimeType = connection.contentType
                }
                val contentLength = connection.contentLength
                responseLength.set(if (contentLength >= 0) contentLength else -1)
                cefResponse.status = 200
            } else {
                cefResponse.error = CefLoadHandler.ErrorCode.ERR_FAILED
                cefResponse.statusText = "Connection is null"
                cefResponse.status = 500
            }
        } catch (e: IOException) {
            cefResponse.error = CefLoadHandler.ErrorCode.ERR_FILE_NOT_FOUND
            cefResponse.statusText = e.localizedMessage
            cefResponse.status = 404
        }
    }

    override fun readResponse(
        dataOut: ByteArray,
        bytesToRead: Int,
        bytesRead: IntRef,
        callback: CefCallback
    ): Boolean {
        return inputStream?.let { stream ->
            val realBytesRead = stream.read(dataOut, 0, bytesToRead)
            return if (realBytesRead > 0) {
                bytesRead.set(realBytesRead)
                true
            } else {
                stream.close()
                false
            }
        } ?: false
    }

    override fun close() {
        inputStream?.close()
    }
}

class CachedResourceState(private val cached: ResourceCache.CachedResource, private val url: String) :
    ResourceHandlerState() {
    private val logger = Logger.getInstance(CachedResourceState::class.java)
    private val inputStream = cached.createInputStream()

    override fun getResponseHeaders(
        cefResponse: CefResponse,
        responseLength: IntRef,
        redirectUrl: StringRef
    ) {
        cefResponse.mimeType = cached.mimeType
        responseLength.set(cached.data.size)
        cefResponse.status = 200
        logger.debug("Serving cached $url (${cached.data.size} bytes)")
    }

    override fun readResponse(
        dataOut: ByteArray,
        bytesToRead: Int,
        bytesRead: IntRef,
        callback: CefCallback
    ): Boolean {
        return try {
            val read = inputStream.read(dataOut, 0, bytesToRead)
            if (read > 0) {
                bytesRead.set(read)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.warn("Failed to read from cached stream: $e")
            false
        }
    }

    override fun close() {
        inputStream.close()
    }
}

class OpenedStream(private val inputStream: InputStream, private val url: String) :
    ResourceHandlerState() {
    private val logger = Logger.getInstance(OpenedStream::class.java)

    override fun getResponseHeaders(
        cefResponse: CefResponse,
        responseLength: IntRef,
        redirectUrl: StringRef
    ) {
        try {
            cefResponse.mimeType = when {
                url.endsWith(".css") -> "text/css"
                url.endsWith(".js") || url.endsWith(".cjs") -> "text/javascript"
                url.endsWith(".html") -> "text/html"
                url.endsWith(".json") -> "application/json"
                else -> URLConnection.guessContentTypeFromName(url) ?: "application/octet-stream"
            }
            responseLength.set(-1)
            cefResponse.status = 200
            logger.debug("Serving $url with MIME ${cefResponse.mimeType}")
        } catch (e: Exception) {
            logger.warn("Failed to set headers for $url: $e")
            cefResponse.status = 500
        }
    }

    override fun readResponse(
        dataOut: ByteArray,
        bytesToRead: Int,
        bytesRead: IntRef,
        callback: CefCallback
    ): Boolean {
        return try {
            val read = inputStream.read(dataOut, 0, bytesToRead)
            if (read > 0) {
                bytesRead.set(read)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.warn("Failed to read from stream: $e")
            false
        }
    }

    override fun close() {
        try {
            inputStream.close()
        } catch (e: Exception) {
            logger.warn("Failed to close stream: $e")
        }
    }
}

class RefactChatResourceHandler : CefResourceHandler, DumbAware {
    private val logger = Logger.getInstance(RefactChatResourceHandler::class.java)
    private var state: ResourceHandlerState = ClosedConnection
    private var currentUrl: String? = null

    override fun processRequest(
        cefRequest: CefRequest,
        cefCallback: CefCallback
    ): Boolean {
        val url = cefRequest.url ?: run {
            logger.warn("Request URL is null")
            return false
        }

        val path = url.removePrefix("http://refactai/")
        val resourcePath = if (path.startsWith("dist/")) path else "webview/$path"

        val cached = ResourceCache.getOrLoad(resourcePath) {
            javaClass.classLoader.getResourceAsStream(resourcePath)
        }

        state = if (cached != null) {
            CachedResourceState(cached, url)
        } else {
            val fallbackUrl = javaClass.classLoader.getResource("webview/$path")
            if (fallbackUrl != null) {
                OpenedConnection(fallbackUrl.openConnection())
            } else {
                logger.debug("Resource not found: $path")
                ClosedConnection
            }
        }

        currentUrl = url
        cefCallback.Continue()
        return true
    }

    override fun getResponseHeaders(
        cefResponse: CefResponse,
        responseLength: IntRef,
        redirectUrl: StringRef
    ) {
        if (currentUrl != null) {
            cefResponse.mimeType = when {
                currentUrl!!.endsWith(".css") -> "text/css"
                currentUrl!!.endsWith(".js") || currentUrl!!.endsWith(".cjs") -> "text/javascript"
                currentUrl!!.endsWith(".html") -> "text/html"
                currentUrl!!.endsWith(".json") -> "application/json"
                else -> "application/octet-stream"
            }
        }
        state.getResponseHeaders(cefResponse, responseLength, redirectUrl)
    }

    override fun readResponse(
        dataOut: ByteArray,
        bytesToRead: Int,
        bytesRead: IntRef,
        callback: CefCallback
    ): Boolean {
        return state.readResponse(dataOut, bytesToRead, bytesRead, callback)
    }

    override fun cancel() {
        state.close()
        state = ClosedConnection
    }
}
