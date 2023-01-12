package com.smallcloud.codify.io


import com.intellij.openapi.Disposable
import com.intellij.util.messages.Topic
import com.smallcloud.codify.UsageStats.Companion.addStatistic
import com.smallcloud.codify.account.inferenceLogin
import org.apache.http.HttpClientConnection
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy
import org.apache.http.impl.client.HttpClients
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.impl.execchain.RequestAbortedException
import org.apache.http.util.EntityUtils
import java.net.SocketException
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

interface ConnectionChangedNotifier {
    fun statusChanged(newStatus: ConnectionStatus) {}
    fun lastErrorMsgChanged(newMsg: String?) {}

    companion object {
        val TOPIC = Topic.create(
            "Connection Changed Notifier",
            ConnectionChangedNotifier::class.java
        )
    }
}

enum class ConnectionStatus {
    CONNECTED,
    PENDING,
    DISCONNECTED,
    ERROR
}

data class RequestJob(var future: CompletableFuture<*>, val request: HttpRequestBase?)

class Connection(uri: URI): Disposable {
    private val route: HttpRoute = HttpRoute(HttpHost(uri.host, uri.port))
    private var context: HttpClientContext = HttpClientContext.create()
    private val connManager: PoolingHttpClientConnectionManager = PoolingHttpClientConnectionManager()
    private val conn: HttpClientConnection = connManager.requestConnection(route, null).get(10, TimeUnit.SECONDS)

    init {
        connManager.maxTotal = 5
        connManager.defaultMaxPerRoute = 4
        connManager.validateAfterInactivity = 5_000
        connManager.connect(conn, route, 10000, context)
        connManager.setMaxPerRoute(route, 5)
    }

    private val client: HttpClient = HttpClients.custom()
        .setConnectionManager(connManager)
        .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
        .build()

    fun get(
        uri: URI,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null,
        needVerify: Boolean = false
    ): RequestJob {
        val get = HttpGet(uri)
        return send(get, headers, requestProperties, needVerify=needVerify)
    }

    fun post(
        uri: URI,
        body: String? = null,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null,
        needVerify: Boolean = false
    ): RequestJob {
        val post = HttpPost(uri)
        post.entity = StringEntity(body)
        return send(post, headers, requestProperties, needVerify=needVerify)
    }

    private fun send(
        req: HttpRequestBase,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null,
        needVerify: Boolean = false
    ): RequestJob {
        headers?.forEach {
            req.addHeader(it.key, it.value)
        }

        requestProperties?.forEach {
            req.addHeader(it.key, it.value)
        }

        val future = CompletableFuture.supplyAsync {
            if (needVerify) inferenceLogin()
        }.thenApplyAsync {
            try {
                val response: HttpResponse = client.execute(req)
                val statusCode: Int = response.statusLine.statusCode
                val responseBody: String = EntityUtils.toString(response.entity)
                return@thenApplyAsync responseBody
            } catch (e: SocketException) {
                // request aborted, it's ok for small files
                throw e
            } catch (e: RequestAbortedException) {
                // request aborted, it's ok
                throw e
            } catch (e: Exception) {
                addStatistic(false, "ApacheHttpClients", req.uri.toString(), e.toString())
                throw e
            } finally {
                req.releaseConnection()
            }
        }
        return RequestJob(future, req)
    }

    override fun dispose() {
        conn.close()
    }
}
