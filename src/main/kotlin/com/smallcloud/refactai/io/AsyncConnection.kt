package com.smallcloud.refactai.io


import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.Disposable
import com.intellij.util.text.findTextRange
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.SMCExceptions
import org.apache.hc.client5.http.async.methods.AbstractBinResponseConsumer
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.http.*
import org.apache.hc.core5.http.message.BasicHeader
import org.apache.hc.core5.http.nio.AsyncRequestProducer
import org.apache.hc.core5.http.nio.entity.AsyncEntityProducers
import org.apache.hc.core5.http.nio.support.BasicRequestProducer
import org.apache.hc.core5.http.ssl.TLS
import org.apache.hc.core5.http.support.BasicRequestBuilder
import org.apache.hc.core5.reactor.IOReactorConfig
import org.apache.hc.core5.ssl.SSLContexts
import org.apache.hc.core5.util.TimeValue
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.statistic.UsageStats.Companion.instance as UsageStats


private const val STREAMING_PREFIX = "data: "

class AsyncConnection : Disposable {
    private val client: CloseableHttpAsyncClient = HttpAsyncClients.custom()
            .setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
                    .setTlsStrategy(ClientTlsStrategyBuilder.create()
                            .setSslContext(SSLContexts.custom().loadTrustMaterial(TrustSelfSignedStrategy()).build())
                            .setTlsVersions(TLS.V_1_3, TLS.V_1_2)
                            .build())
                    .build())
            .setIOReactorConfig(
                    IOReactorConfig.custom()
                            .setIoThreadCount(8)
                            .setSoTimeout(30, TimeUnit.SECONDS)
                            .setSelectInterval(TimeValue.ofMilliseconds(5000))
                            .setTcpNoDelay(true)
                            .build()
            )
            .setDefaultRequestConfig(
                    RequestConfig.custom()
                            .setConnectionKeepAlive(TimeValue.ofSeconds(1))
                            .setHardCancellationEnabled(true)
                            .setConnectionRequestTimeout(30, TimeUnit.SECONDS)
                            .setResponseTimeout(30, TimeUnit.SECONDS)
                            .build()
            )
            .setDefaultHeaders(
                    listOf<Header>(
                            BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"),
                            BasicHeader(HttpHeaders.CACHE_CONTROL, "no-cache"),
                            BasicHeader(HttpHeaders.REFERER, "no-referrer"),
                    )
            )
            .build()

    init {
        client.start()
    }

    fun ping(uri: URI) {
        client.execute(SimpleHttpRequest.create(Method.GET, uri), null).get(5, TimeUnit.SECONDS)
    }

    fun get(
            uri: URI,
            body: String? = null,
            headers: Map<String, String>? = null,
            requestProperties: Map<String, String>? = null,
            stat: UsageStatistic = UsageStatistic(),
            dataReceiveEnded: (String) -> Unit = {},
            dataReceived: (String, String) -> Unit =  { _: String, _: String -> },
            errorDataReceived: (JsonObject) -> Unit = {},
            failedDataReceiveEnded: (Throwable?) -> Unit = {},
            requestId: String = ""
    ): CompletableFuture<Future<*>> {
        val requestProducer: AsyncRequestProducer = BasicRequestProducer(
                BasicRequestBuilder
                        .get(uri)
                        .also { builder ->
                            headers?.forEach { builder.addHeader(it.key, it.value) }
                            requestProperties?.forEach { builder.addHeader(it.key, it.value) }
                        }
                        .build(),
                body?.let { AsyncEntityProducers.create(body, ContentType.APPLICATION_JSON) }
        )

        return send(
                requestProducer, uri,
                stat = stat,
                dataReceiveEnded = dataReceiveEnded, dataReceived = dataReceived,
                errorDataReceived = errorDataReceived, failedDataReceiveEnded = failedDataReceiveEnded,
                requestId=requestId
        )
    }

    fun post(
        uri: URI,
        body: String? = null,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null,
        stat: UsageStatistic,
        dataReceiveEnded: (String) -> Unit = {},
        dataReceived: (String, String) -> Unit = { _: String, _: String -> },
        errorDataReceived: (JsonObject) -> Unit = {},
        failedDataReceiveEnded: (Throwable?) -> Unit = {},
        requestId: String = ""
    ): CompletableFuture<Future<*>> {
        val requestProducer: AsyncRequestProducer = BasicRequestProducer(
                BasicRequestBuilder
                        .post(uri)
                        .also { builder ->
                            headers?.forEach { builder.addHeader(it.key, it.value) }
                            requestProperties?.forEach { builder.addHeader(it.key, it.value) }
                        }
                        .build(),
                AsyncEntityProducers.create(body, ContentType.APPLICATION_JSON)
        )
        return send(
                requestProducer, uri,
                stat = stat,
                dataReceiveEnded = dataReceiveEnded, dataReceived = dataReceived,
                errorDataReceived = errorDataReceived, failedDataReceiveEnded = failedDataReceiveEnded,
                requestId=requestId
        )
    }

    private fun send(
            requestProducer: AsyncRequestProducer,
            uri: URI,
            stat: UsageStatistic,
            dataReceiveEnded: (String) -> Unit,
            dataReceived: (String, String) -> Unit,
            errorDataReceived: (JsonObject) -> Unit,
            failedDataReceiveEnded: (Throwable?) -> Unit = {},
            requestId: String = ""
    ): CompletableFuture<Future<*>> {
        return CompletableFuture.supplyAsync {
            return@supplyAsync client.execute(
                    requestProducer,
                    object : AbstractBinResponseConsumer<String>() {
                        private var bufferStr = ""

                        override fun releaseResources() {
                        }

                        override fun capacityIncrement(): Int {
                            return 128
                        }

                        override fun informationResponse(
                                response: HttpResponse?,
                                context: org.apache.hc.core5.http.protocol.HttpContext?
                        ) {
                        }

                        override fun data(src: ByteBuffer?, endOfStream: Boolean) {
                            src ?: return
                            val part = Charset.forName("UTF-8").decode(src)
                            bufferStr += part
                            if (part.startsWith(STREAMING_PREFIX)) {
                                try {
                                    val data = Gson().fromJson(bufferStr, JsonObject::class.java)
                                    errorDataReceived(data)
                                    return
                                } catch (_: JsonSyntaxException) {
                                }
                                val (dataPieces, maybeLeftOverBuffer) = lookForCompletedDataInStreamingBuf(bufferStr)
                                dataPieces.forEach { dataReceived(it, requestId) }
                                if (maybeLeftOverBuffer == null) {
                                    return
                                } else {
                                    bufferStr = maybeLeftOverBuffer
                                }
                            }
                        }

                        override fun start(response: HttpResponse?, contentType: ContentType?) {
                        }

                        override fun buildResult(): String {
                            return bufferStr
                        }

                        override fun failed(cause: Exception?) {}
                    },
                    object : FutureCallback<String> {
                        override fun completed(result: String) {
                            dataReceiveEnded(result)
                        }

                        override fun failed(ex: java.lang.Exception?) {
                            if (ex !is SMCExceptions)
                                UsageStats.addStatistic(false, stat, uri.toString(), ex.toString())
                            if (ex is java.net.SocketException ||
                                    ex is java.net.UnknownHostException) {
                                InferenceGlobalContext.status = ConnectionStatus.DISCONNECTED
                            }
                            failedDataReceiveEnded(ex)
                        }

                        override fun cancelled() {}
                    }
            )
        }
    }


    private fun lookForCompletedDataInStreamingBuf(streamingBuffer: String): Pair<List<String>, String?> {
        var outputStreamingBuffer = streamingBuffer
        val dataPieces: MutableList<String> = mutableListOf()
        while (true) {
            val offset = outputStreamingBuffer.findTextRange("\n\n")?.startOffset ?: break
            var currentBuffer = outputStreamingBuffer.substring(0, offset)
            outputStreamingBuffer = outputStreamingBuffer.substring(offset + 2)
            assert(currentBuffer.startsWith(STREAMING_PREFIX))
            currentBuffer = currentBuffer.substring(6)
            if (currentBuffer == "[ERROR]") {
                throw Exception("[ERROR]")
            }
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
