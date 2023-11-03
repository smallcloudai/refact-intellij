package com.smallcloud.refactai.lsp

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.SystemProperties.getUserHome
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.binPrefix
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.notifications.emitError
import com.smallcloud.refactai.settings.AppSettingsState
import org.apache.hc.core5.concurrent.ComplexFuture
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext


private fun getExeSuffix(): String {
    if (SystemInfo.isWindows) return ".exe"
    return ""
}
interface LSPProcessHolderChangedNotifier {
    fun capabilitiesChanged(newCaps: LSPCapabilities) {}

    fun xDebugLSPPortChanged(newPort: Int?) {}
    companion object {
        val TOPIC = Topic.create(
                "Connection Changed Notifier",
                LSPProcessHolderChangedNotifier::class.java
        )
    }
}

class LSPProcessHolder: Disposable {
    private var process: Process? = null
    private var lastConfig: LSPConfig? = null
    private val logger = Logger.getInstance("LSPProcessHolder")
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "SMCLSPLoggerScheduler", 1
    )
    private var loggerTask: Future<*>? = null
    private val schedulerCaps = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCLSPCapsRequesterScheduler", 1
    )
    private var capsTask: Future<*>? = null
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus


    var xDebugLSPPort: Int?
        get() { return AppSettingsState.instance.xDebugLSPPort }
        set(newValue) {
            if (newValue == AppSettingsState.instance.xDebugLSPPort) return
            messageBus
                .syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)
                .xDebugLSPPortChanged(newValue)
            settingsChanged()
        }

    fun startup() {
        messageBus
                .connect(this)
                .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                    override fun apiKeyChanged(newApiKey: String?) {
                        settingsChanged()
                    }
                })
        messageBus
                .connect(this)
                .subscribe(InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                    override fun userInferenceUriChanged(newUrl: String?) {
                        settingsChanged()
                    }
                })

        Companion::class.java.getResourceAsStream(
                "/bin/${binPrefix}/refact-lsp${getExeSuffix()}").use { input ->
            if (input == null) {
                emitError("LSP server is not found for host operating system, please contact support")
            } else {
                val path = Paths.get(BIN_PATH)
                path.parent.toFile().mkdirs()
                Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING)
                if (!SystemInfo.isWindows) {
                    GeneralCommandLine(listOf("chmod", "+x", BIN_PATH)).createProcess()
                }
            }
        }
        settingsChanged()


        capsTask = schedulerCaps.scheduleWithFixedDelay({
            capabilities = getCaps()
            if (capabilities.cloudName.isNotEmpty()) {
                capsTask?.cancel(true)
                schedulerCaps.scheduleWithFixedDelay({
                    capabilities = getCaps()
                }, 15, 15, TimeUnit.MINUTES)
            }
        }, 0, 3, TimeUnit.SECONDS)
    }

    private fun settingsChanged() {
        synchronized(this) {
            terminate()
            if (xDebugLSPPort != null) return
            val address = if (InferenceGlobalContext.inferenceUri == null) "Refact" else InferenceGlobalContext.inferenceUri
            val newConfig = LSPConfig(
                address = address,
                apiKey = AccountManager.apiKey,
                port = (32000..32199).random(),
                clientVersion = "${Resources.client}-${Resources.version}",
                useTelemetry = true,
            )
            startProcess(newConfig)
        }
    }

    val lspIsWorking: Boolean
        get() = xDebugLSPPort != null || process?.isAlive == true

    var capabilities: LSPCapabilities = LSPCapabilities()
        set(newValue) {
            if (newValue == field) return
            field = newValue
            ApplicationManager.getApplication()
                    .messageBus
                    .syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)
                    .capabilitiesChanged(field)
        }

    private fun startProcess(config: LSPConfig) {
        if (config == lastConfig) return

        lastConfig = config
        terminate()
        if (lastConfig == null || !lastConfig!!.isValid) return
        logger.warn("LSP start_process " + BIN_PATH + " " + lastConfig!!.toArgs())
        process = GeneralCommandLine(listOf(BIN_PATH) + lastConfig!!.toArgs())
                .withRedirectErrorStream(true)
                .createProcess()
        process!!.waitFor(3, TimeUnit.SECONDS)
        loggerTask = scheduler.submit {
            val reader = process!!.inputStream.bufferedReader()
            var line = reader.readLine()
            while (line != null) {
                logger.warn("\n$line")
                line = reader.readLine()
            }
        }
        process!!.onExit().thenAcceptAsync { process1 ->
            logger.warn("LSP bad_things_happened " +
                    process1.inputStream.bufferedReader().use { it.readText() })
        }
        try {
            InferenceGlobalContext.connection.ping(url)
            capabilities = getCaps()
        } catch (e: Exception) {
            logger.warn("LSP bad_things_happened " + e.message)
        }
    }

    private fun safeTerminate() {
        InferenceGlobalContext.connection.get(URI(
            "http://127.0.0.1:${lastConfig!!.port}/v1/graceful-shutdown")).get().get()
    }

    private fun terminate() {
        process?.let {
            try {
                safeTerminate()
                if (it.waitFor(3, TimeUnit.SECONDS)) {
                    logger.info("LSP SIGTERM")
                    it.destroy()
                }
                process = null
            } catch (_: Exception) {}
        }
    }

    companion object {
        private val BIN_PATH = Path(getUserHome(), ".refact", "bin",
                "refact-lsp${getExeSuffix()}").toString()
        @JvmStatic
        val instance: LSPProcessHolder
            get() = ApplicationManager.getApplication().getService(LSPProcessHolder::class.java)
    }

    override fun dispose() {
        terminate()
        scheduler.shutdown()
        schedulerCaps.shutdown()
    }

    val url: URI
        get() {
            val port = xDebugLSPPort?: lastConfig?.port ?: return URI("")

            return URI("http://127.0.0.1:${port}/")
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
                logger.warn("LSP caps_received " + out)
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