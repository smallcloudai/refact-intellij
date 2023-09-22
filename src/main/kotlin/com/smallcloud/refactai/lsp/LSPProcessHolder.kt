package com.smallcloud.refactai.lsp

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.UnixProcessManager.sendSignal
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties.getUserHome
import com.intellij.util.concurrency.AppExecutorUtil
import org.apache.hc.core5.concurrent.ComplexFuture
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext


private fun getExeSuffix(): String {
    if (SystemInfo.isWindows) return ".exe"
    return ""
}

class LSPProcessHolder: Disposable {
    private var process: Process? = null
    private var lastConfig: LSPConfig? = null
    private val logger = Logger.getInstance("LSPProcessHolder")
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "SMCLSPLoggerScheduler", 1
    )
    private var loggerTask: Future<*>? = null

    fun startup() {
        Companion::class.java.getResourceAsStream("/bin/code-scratchpads${getExeSuffix()}").use { input ->
            if (input != null) {
                val path = Paths.get(BIN_PATH)
                path.parent.toFile().mkdirs()
                Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING)
                GeneralCommandLine(listOf("chmod", "+x", BIN_PATH)).createProcess()
            }
        }
    }


    fun startProcess(config: LSPConfig) {
        if (config == lastConfig) return

        lastConfig = config
        terminate()
        if (lastConfig == null) return
        logger.warn("RUST start_process " + BIN_PATH + " " + lastConfig!!.toArgs())
        process = GeneralCommandLine(listOf(BIN_PATH) + lastConfig!!.toArgs())
                .withRedirectErrorStream(true)
                .createProcess()
        loggerTask = scheduler.submit {
            val reader = process!!.inputStream.bufferedReader()
            var line = reader.readLine()
            while (line != null) {
                logger.warn("\n$line")
                line = reader.readLine()
            }
        }
        process!!.onExit().thenAcceptAsync { process1 ->
            logger.warn("RUST bad_things_happened " +
                    process1.inputStream.bufferedReader().use { it.readText() })
        }
        try {
            InferenceGlobalContext.connection.ping(url)
            getCaps()
        } catch (e: Exception) {
            logger.warn("RUST bad_things_happened " + e.message)
        }
    }

    private fun terminate() {
        process?.let {
            if (SystemInfo.isUnix) {
                logger.info("RUST SIGINT")
                sendSignal(it.pid().toInt(), "SIGINT")
                it.waitFor(1, TimeUnit.SECONDS)
            }
            logger.info("RUST SIGTERM")
            it.destroy()
            process = null
        }
    }

    companion object {
        private val BIN_PATH = Path(getUserHome(), ".refact", "bin",
                "code-scratchpads${getExeSuffix()}").toString()
        @JvmStatic
        val instance: LSPProcessHolder
            get() = ApplicationManager.getApplication().getService(LSPProcessHolder::class.java)
    }

    override fun dispose() {
        terminate()
        scheduler.shutdown()
    }

    private val url: URI
        get() {
            if (lastConfig == null || lastConfig?.port == null) return URI("")
            return URI("http://127.0.0.1:${lastConfig!!.port}/")
        }
    private fun getCaps(): LSPCapabilities {
        var res = LSPCapabilities()
        InferenceGlobalContext.connection.get(url.resolve("/v1/caps"),
                dataReceiveEnded = {},
                dataReceived = {},
                errorDataReceived = {}).also {
            var requestFuture: ComplexFuture<*>? = null
            try {
                requestFuture = it.get() as ComplexFuture
                val out = requestFuture.get()
                logger.warn("RUST caps_received " + it)
                val gson = Gson()
                res = gson.fromJson(out as String, LSPCapabilities::class.java)
                logger.debug("caps_received request finished")
            } catch (e: Exception) {
                logger.debug("caps_received ${e.message}")
            }
            return res
        }
    }
}