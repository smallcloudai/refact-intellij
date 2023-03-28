package com.smallcloud.refactai.io


import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.Disposable
import com.intellij.util.text.findTextRange
import com.smallcloud.refactai.account.inferenceLogin
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.SMCExceptions
import org.apache.hc.client5.http.async.methods.AbstractBinResponseConsumer
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.client5.http.impl.async.HttpAsyncClients
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
import com.smallcloud.refactai.statistic.UsageStats.Companion.instance as UsageStats


class AsyncConnection(uri: URI, isCustomUrl: Boolean = false) : Disposable {
    private val client: CloseableHttpAsyncClient = HttpAsyncClients.customHttp2()
        .setTlsStrategy(
            ClientTlsStrategyBuilder.create()
                .setSslContext(if (isCustomUrl)
                    SSLContexts.custom().loadTrustMaterial(TrustSelfSignedStrategy()).build() else
                        SSLContexts.createSystemDefault())
                .setTlsVersions(TLS.V_1_3, TLS.V_1_2)
                .build()
        )
        .setIOReactorConfig(
            IOReactorConfig.custom()
                .setIoThreadCount(8)
                .setSoTimeout(30, TimeUnit.SECONDS)
                .setSelectInterval(TimeValue.ofMilliseconds(5))
                .setTcpNoDelay(true)
                .build()
        )
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectionKeepAlive(TimeValue.ofSeconds(1))
                .setHardCancellationEnabled(true)
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
        client.execute(SimpleHttpRequest.create(Method.GET, uri), null).get()
    }

    fun post(
        uri: URI,
        body: String? = null,
        headers: Map<String, String>? = null,
        requestProperties: Map<String, String>? = null,
        needVerify: Boolean = false,
        stat: UsageStatistic,
        dataReceiveEnded: () -> Unit,
        dataReceived: (String) -> Unit,
        errorDataReceived: (JsonObject) -> Unit,
        failedDataReceiveEnded: (Throwable?) -> Unit = {},
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
            needVerify = needVerify, stat = stat,
            dataReceiveEnded = dataReceiveEnded, dataReceived = dataReceived,
            errorDataReceived = errorDataReceived, failedDataReceiveEnded=failedDataReceiveEnded
        )
    }

    private fun send(
        requestProducer: AsyncRequestProducer,
        uri: URI,
        needVerify: Boolean = false,
        stat: UsageStatistic,
        dataReceiveEnded: () -> Unit,
        dataReceived: (String) -> Unit,
        errorDataReceived: (JsonObject) -> Unit,
        failedDataReceiveEnded: (Throwable?) -> Unit = {},
    ): CompletableFuture<Future<*>> {
        return CompletableFuture.supplyAsync {
            if (needVerify) inferenceLogin()
        }.thenApply {
            return@thenApply client.execute(
                requestProducer,
                object : AbstractBinResponseConsumer<Void?>() {
                    var bufferStr = ""

                    override fun releaseResources() {
                    }

                    override fun capacityIncrement(): Int {
                        return 128
                    }

                    override fun informationResponse(
                        response: HttpResponse?,
                        context: org.apache.hc.core5.http.protocol.HttpContext?
                    ) {}

                    override fun data(src: ByteBuffer?, endOfStream: Boolean) {
                        src ?: return
                        if (endOfStream) return
                        bufferStr += Charset.forName("UTF-8").decode(src)
                        try {
                            val data = Gson().fromJson(bufferStr, JsonObject::class.java)
                            errorDataReceived(data)
                        } catch (_: JsonSyntaxException) {
                        }
                        val (dataPieces, maybeLeftOverBuffer) = lookForCompletedDataInStreamingBuf(bufferStr)
                        dataPieces.forEach { dataReceived(it) }
                        if (maybeLeftOverBuffer == null) {
                            return
                        } else {
                            bufferStr = maybeLeftOverBuffer
                        }
                    }

                    override fun start(response: HttpResponse?, contentType: ContentType?) {
                    }

                    override fun buildResult(): Void? {
                        return null
                    }

                    override fun failed(cause: Exception?) {}
                },
                object : FutureCallback<Void?> {
                    override fun completed(result: Void?) {
                        dataReceiveEnded()
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
            assert(currentBuffer.substring(0, 6) == "data: ")
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
