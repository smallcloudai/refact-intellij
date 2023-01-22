package com.smallcloud.codify.io


import com.intellij.openapi.Disposable
import com.intellij.util.text.findTextRange
import com.smallcloud.codify.UsageStats.Companion.addStatistic
import com.smallcloud.codify.account.inferenceLogin
import io.ktor.utils.io.streams.*
import org.apache.http.HttpException
import org.apache.http.HttpResponse
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpRequestBase
import org.apache.http.config.ConnectionConfig
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy
import org.apache.http.impl.execchain.RequestAbortedException
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.impl.nio.reactor.IOReactorConfig
import org.apache.http.nio.IOControl
import org.apache.http.nio.client.methods.AsyncByteConsumer
import org.apache.http.nio.client.methods.HttpAsyncMethods
import org.apache.http.protocol.HttpContext
import java.io.IOException
import java.net.SocketException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture


class AsyncConnection(uri: URI) : Disposable {
    private val ioConfig = IOReactorConfig.custom()
        .setIoThreadCount(4)
        .setConnectTimeout(100000)
        .setSoTimeout(100000)
        .build()
    private val requestConfig = RequestConfig.custom()
        .setSocketTimeout(100000)
        .setConnectTimeout(100000)
        .build()
    private val connectionConfig = ConnectionConfig.custom()
        .setBufferSize(1024)
        .build()
    private val client: CloseableHttpAsyncClient = HttpAsyncClients.custom()
        .setDefaultIOReactorConfig(ioConfig)
        .setDefaultConnectionConfig(connectionConfig)
        .setDefaultRequestConfig(requestConfig)
        .setMaxConnTotal(4000)
        .setMaxConnPerRoute(1000)
        .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
        .build()

    init {
        client.start()
    }

    fun post(
        uri: URI,
        body: String? = null,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null,
        needVerify: Boolean = false,
        scope: String = "",
        onDataReceiveEnded: () -> Unit,
        onDataReceived: (String) -> Unit
    ): RequestJob {
        val post = HttpPost(uri)
        post.entity = StringEntity(body, Charset.forName("UTF-16"))
        return send(
            post, headers, requestProperties,
            needVerify = needVerify, scope = scope,
            onDataReceiveEnded = onDataReceiveEnded, onDataReceived = onDataReceived
        )
    }

    private fun send(
        req: HttpRequestBase,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null,
        needVerify: Boolean = false,
        scope: String = "",
        onDataReceiveEnded: () -> Unit,
        onDataReceived: (String) -> Unit
    ): RequestJob {
        headers?.forEach { req.addHeader(it.key, it.value) }
        requestProperties?.forEach { req.addHeader(it.key, it.value) }

        val future = CompletableFuture.supplyAsync {
            if (needVerify) inferenceLogin()
        }.thenApplyAsync {
            try {
                client.execute(
                    HttpAsyncMethods.create(req),
                    object : AsyncByteConsumer<Void?>() {
                        var bufferStr = ""

                        @Throws(IOException::class)
                        override fun onByteReceived(buffer: ByteBuffer, ioctrl: IOControl?) {
                            bufferStr += Charset.forName("UTF-8").decode(buffer)
                            val (dataPieces, maybeLeftOverBuffer) = lookForCompletedDataInStreamingBuf(bufferStr)
                            dataPieces.forEach { onDataReceived(it) }
                            if (maybeLeftOverBuffer == null) {
                                return
                            } else {
                                bufferStr = maybeLeftOverBuffer
                            }
                        }

                        @Throws(HttpException::class, IOException::class)
                        override fun onResponseReceived(response: HttpResponse) {
                        }

                        @Throws(java.lang.Exception::class)
                        override fun buildResult(context: HttpContext?): Void? {
                            onDataReceiveEnded()
                            return null
                        }
                    },
                    null
                ).get()
            } catch (e: SocketException) {
                // request aborted, it's ok for small files
                throw e
            } catch (e: RequestAbortedException) {
                // request aborted, it's ok
                throw e
            } catch (e: Exception) {
                addStatistic(false, scope, req.uri.toString(), e.toString())
                throw e
            } finally {
                req.releaseConnection()
            }
        }
        return RequestJob(future, req)
    }


    private fun lookForCompletedDataInStreamingBuf(streamingBuffer: String): Pair<List<String>, String?> {
        var outputStreamingBuffer = streamingBuffer
        val dataPieces: MutableList<String> = mutableListOf()
        while (true) {
            val offset = outputStreamingBuffer.findTextRange("\n\n")?.startOffset ?: break
            var currentBuffer = outputStreamingBuffer.substring(0, offset)
            outputStreamingBuffer = outputStreamingBuffer.substring(offset + 2)
            assert(currentBuffer.substring(0, 6) == "data: ")
            currentBuffer = currentBuffer.substring(6)
            if (currentBuffer == "[DONE]") {
                return dataPieces to null
            }
            dataPieces.add(currentBuffer)
        }
        return dataPieces to outputStreamingBuffer
    }

    override fun dispose() {
        client.close()
    }
}
