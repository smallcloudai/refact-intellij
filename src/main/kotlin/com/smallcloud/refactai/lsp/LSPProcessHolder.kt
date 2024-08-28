package com.smallcloud.refactai.lsp

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil.getTempDirectory
import com.intellij.openapi.util.io.FileUtil.setExecutable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.binPrefix
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.notifications.emitError
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
    fun lspIsActive(isActive: Boolean) {}

    companion object {
        val TOPIC = Topic.create(
            "Connection Changed Notifier",
            LSPProcessHolderChangedNotifier::class.java
        )
    }
}

class LSPProcessHolder(val project: Project) : Disposable {
    private var process: Process? = null
    private var lastConfig: LSPConfig? = null
    private val logger = Logger.getInstance("LSPProcessHolder")
    private val loggerScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCLSPLoggerScheduler", 1
    )
    private var loggerTask: Future<*>? = null
    private val schedulerCaps = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCLSPCapsRequesterScheduler", 1
    )
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus
    private var isWorking_ = false
    private val healthCheckerScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCLSHealthCheckerScheduler", 1
    )

    var isWorking: Boolean
        get() = isWorking_
        set(newValue) {
            if (isWorking_ == newValue) return
            if (!project.isDisposed) {
                project
                    .messageBus
                    .syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)
                    .lspIsActive(newValue)
            }
            isWorking_ = newValue
        }

    init {
        messageBus
            .connect(this)
            .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                override fun apiKeyChanged(newApiKey: String?) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }

                override fun planStatusChanged(newPlan: String?) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }
            })
        messageBus
            .connect(this)
            .subscribe(InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                override fun userInferenceUriChanged(newUrl: String?) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }

                override fun astFlagChanged(newValue: Boolean) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }

                override fun astFileLimitChanged(newValue: Int) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }

                override fun astLightModeChanged(newValue: Boolean) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }

                override fun vecdbFlagChanged(newValue: Boolean) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }

                override fun vecdbFileLimitChanged(newValue: Int) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }

                override fun xDebugLSPPortChanged(newPort: Int?) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }

                override fun insecureSSLChanged(newValue: Boolean) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }
            })

        Companion::class.java.getResourceAsStream(
            "/bin/${binPrefix}/refact-lsp${getExeSuffix()}"
        ).use { input ->
            if (input == null) {
                emitError("LSP server is not found for host operating system, please contact support")
            } else {
                for (i in 0..4) {
                    try {
                        val path = Paths.get(BIN_PATH)
                        path.parent.toFile().mkdirs()
                        Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING)
                        setExecutable(path.toFile())
                        break
                    } catch (e: Exception) {
                        logger.warn(e.message)
                    }
                }
            }
        }
        settingsChanged()

        healthCheckerScheduler.scheduleWithFixedDelay({
            if (lastConfig == null) return@scheduleWithFixedDelay
            if (InferenceGlobalContext.xDebugLSPPort != null) return@scheduleWithFixedDelay
            if (process?.isAlive == false) {
                startProcess()
            }
        }, 1, 1, TimeUnit.SECONDS)
    }


    private fun settingsChanged() {
        synchronized(this) {
            terminate()
            if (InferenceGlobalContext.xDebugLSPPort != null) {
                capabilities = getCaps()
                lspProjectInitialize(this, project)
                return
            }
            startProcess()
        }
    }

    var capabilities: LSPCapabilities = LSPCapabilities()
        set(newValue) {
            if (newValue == field) return
            field = newValue
            project
                .messageBus
                .syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)
                .capabilitiesChanged(field)
        }

    private fun startProcess() {
        val address = if (InferenceGlobalContext.inferenceUri == null) "Refact" else
            InferenceGlobalContext.inferenceUri
        val newConfig = LSPConfig(
            address = address,
            apiKey = AccountManager.apiKey,
            port = 0,
            clientVersion = "${Resources.client}-${Resources.version}/${Resources.jbBuildVersion}",
            useTelemetry = true,
            deployment = InferenceGlobalContext.deploymentMode,
            ast = InferenceGlobalContext.astIsEnabled,
            astFileLimit = InferenceGlobalContext.astFileLimit,
            astLightMode = InferenceGlobalContext.astLightMode,
            vecdb = InferenceGlobalContext.vecdbIsEnabled,
            vecdbFileLimit = InferenceGlobalContext.vecdbFileLimit,
            insecureSSL = InferenceGlobalContext.insecureSSL,
        )

        val processIsAlive = process?.isAlive == true

        if (newConfig == lastConfig && processIsAlive) return

        capabilities = LSPCapabilities()
        terminate()
        if (!newConfig.isValid) return
        var attempt = 0
        while (attempt < 5) {
            try {
                newConfig.port = (32000..32199).random()
                logger.warn("LSP start_process " + BIN_PATH + " " + newConfig.toArgs())
                process = GeneralCommandLine(listOf(BIN_PATH) + newConfig.toArgs())
                    .withRedirectErrorStream(true)
                    .createProcess()
                process!!.waitFor(5, TimeUnit.SECONDS)
                lastConfig = newConfig
                break
            } catch (e: Exception) {
                attempt++
                logger.warn("LSP start_process didn't start attempt=${attempt}")
                if (attempt == 5) {
                    throw e
                }
            }
        }
        loggerTask = loggerScheduler.submit {
            val reader = process!!.inputStream.bufferedReader()
            var line = reader.readLine()
            while (line != null) {
                logger.warn("\n$line")
                line = reader.readLine()
            }
        }
        process!!.onExit().thenAcceptAsync { process1 ->
            if (process1.exitValue() != 0) {
                logger.warn("LSP bad_things_happened " +
                    process1.inputStream.bufferedReader().use { it.readText() })
            }
        }
        attempt = 0
        while (attempt < 5) {
            try {
                InferenceGlobalContext.connection.ping(url)
                buildInfo = getBuildInfo()
                capabilities = getCaps()
                fetchToolboxConfig()
                isWorking = true
                break
            } catch (e: Exception) {
                logger.warn("LSP bad_things_happened " + e.message)
            }
            attempt++
            Thread.sleep(3000)
        }
        lspProjectInitialize(this, project)
    }

    private fun fetchToolboxConfig(): String {
        val config = InferenceGlobalContext.connection.get(url.resolve("/v1/customization"),
            dataReceiveEnded={
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = null
            },
            errorDataReceived = {},
            failedDataReceiveEnded = {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                if (it != null) {
                    InferenceGlobalContext.lastErrorMsg = it.message
                }
            }).join().get()
        return config as String
    }

    private fun safeTerminate() {
        InferenceGlobalContext.connection.get(
            URI(
                "http://127.0.0.1:${lastConfig!!.port}/v1/graceful-shutdown"
            )
        ).get().get()
    }

    private fun terminate() {
        process?.let {
            try {
                isWorking = false
                safeTerminate()
                if (it.waitFor(3, TimeUnit.SECONDS)) {
                    logger.info("LSP SIGTERM")
                    it.destroy()
                }
                process = null
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        val BIN_PATH = Path(
            getTempDirectory(),
            ApplicationInfo.getInstance().build.toString().replace(Regex("[^A-Za-z0-9 ]"), "_") +
                "_refact_lsp${getExeSuffix()}"
        ).toString()

        // here ?
        @JvmStatic
        fun getInstance(project: Project): LSPProcessHolder = project.service()

        var buildInfo: String = ""
    }

    override fun dispose() {
        terminate()
        loggerScheduler.shutdown()
        schedulerCaps.shutdown()
        healthCheckerScheduler.shutdown()
    }

    private fun getBuildInfo(): String {
        var res = ""
        InferenceGlobalContext.connection.get(url.resolve("/build_info"),
            dataReceiveEnded={
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = null
            },
            errorDataReceived = {},
            failedDataReceiveEnded = {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                if (it != null) {
                    InferenceGlobalContext.lastErrorMsg = it.message
                }
            }).also {
            try {
                res = it.get().get() as String
                logger.debug("build_info request finished")
            } catch (e: Exception) {
                logger.debug("build_info ${e.message}")
            }
        }
        return res
    }

    val url: URI
        get() {
            val port = InferenceGlobalContext.xDebugLSPPort ?: lastConfig?.port ?: return URI("")

            return URI("http://127.0.0.1:${port}/")
        }

    private fun getCaps(): LSPCapabilities {
        var res = LSPCapabilities()
        InferenceGlobalContext.connection.get(url.resolve("/v1/caps"),
            dataReceiveEnded={
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = null
            },
            errorDataReceived = {},
            failedDataReceiveEnded = {
                if (it != null) {
                    InferenceGlobalContext.lastErrorMsg = it.message
                }
            }
        ).also {
            val requestFuture: ComplexFuture<*>?
            try {
                requestFuture = it.get() as ComplexFuture
                val out = requestFuture.get()
                logger.warn("LSP caps_received $out")
                val gson = Gson()
                res = gson.fromJson(out as String, LSPCapabilities::class.java)
                logger.debug("caps_received request finished")
            } catch (e: Exception) {
                logger.debug("caps_received ${e.message}")
            }
            return res
        }
    }

    fun getRagStatus(): RagStatus? {
        InferenceGlobalContext.connection.get(
            url.resolve("/v1/rag-status"),
            requestProperties = mapOf("redirect" to "follow", "cache" to "no-cache", "referrer" to "no-referrer"),
            dataReceiveEnded={
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = null
            },
            errorDataReceived = {},
            failedDataReceiveEnded = {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                if (it != null) {
                    InferenceGlobalContext.lastErrorMsg = it.message
                }
            }).also {
            val requestFuture: ComplexFuture<*>?
            try {
                requestFuture = it.get() as ComplexFuture
                val out = requestFuture.get()
                val gson = Gson()
                return gson.fromJson(out as String, RagStatus::class.java)
            } catch (e: Exception) {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                InferenceGlobalContext.lastErrorMsg = e.message
                return null
            }
        }
    }

    fun attempingToReach(): String {
        val xDebug = InferenceGlobalContext.xDebugLSPPort
        if (xDebug != null) {
            return "debug rust binary on ports $xDebug"
        } else {
            if (InferenceGlobalContext.inferenceUri != null) {
                return InferenceGlobalContext.inferenceUri.toString()
            }
            return "<no-address-configured>"
        }
    }
}