package com.smallcloud.refactai.lsp

import com.google.gson.Gson
import com.google.gson.JsonObject
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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
    fun ragStatusChanged(ragStatus: RagStatus) {}

    companion object {
        val TOPIC = Topic.create(
            "Refact.ai LSP Process Notifier", LSPProcessHolderChangedNotifier::class.java
        )
    }
}

open class LSPProcessHolder(val project: Project) : Disposable {
    // Flag to track if this instance has been disposed
    @Volatile
    private var isDisposed = false
    private var process: Process? = null
    private var lastConfig: LSPConfig? = null
    private val loggerScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCLSPLoggerScheduler", 1
    )
    private var loggerTask: Future<*>? = null
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus
    private var isWorking_ = false
    private val healthCheckerScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCLSPHealthCheckerScheduler", 1
    )
    var ragStatusCache: RagStatus? = null
    private val ragStatusCheckerScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCLSPRagStatusCheckerScheduler", 1
    )

    private val exitThread: Thread = Thread {
        terminate()
    }

    open var isWorking: Boolean
        get() = isWorking_
        set(newValue) {
            if (isWorking_ == newValue) return
            isWorking_ = newValue
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC).lspIsActive(newValue)
            }
        }

    init {
        initialize()
        messageBus.connect(this).subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
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
        messageBus.connect(this)
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

                override fun experimentalLspFlagEnabledChanged(newValue: Boolean) {
                    AppExecutorUtil.getAppScheduledExecutorService().submit {
                        settingsChanged()
                    }
                }
            })

        Runtime.getRuntime().addShutdownHook(exitThread)
        settingsChanged()

        healthCheckerScheduler.scheduleWithFixedDelay({
            try {
                // Check if we're already disposed before proceeding
                if (isDisposed || project.isDisposed) {
                    logger.info("Skipping health check for disposed LSPProcessHolder or project")
                    return@scheduleWithFixedDelay
                }

                if (lastConfig == null) return@scheduleWithFixedDelay
                if (InferenceGlobalContext.xDebugLSPPort != null) return@scheduleWithFixedDelay
                if (process?.isAlive == false) {
                    startProcess()
                }
            } catch (e: java.util.concurrent.RejectedExecutionException) {
                // This exception can occur during shutdown when schedulers are already closed
                if (e.message?.contains("Already shutdown") == true) {
                    logger.info("Ignoring RejectedExecutionException during health check: ${e.message}")
                } else {
                    // Log but don't rethrow other types of RejectedExecutionException
                    logger.warn("Unexpected RejectedExecutionException during health check: ${e.message}")
                }
            } catch (e: Exception) {
                // Log any other exceptions but don't let them crash the scheduler
                logger.warn("Exception during health check: ${e.message}")
            }
        }, 1, 1, TimeUnit.SECONDS)
        ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 1000, TimeUnit.MILLISECONDS)
    }

    fun settingsChanged() {
        try {
            // Check if we're already disposed before proceeding
            if (isDisposed || project.isDisposed) {
                logger.info("Skipping settings change for disposed LSPProcessHolder or project")
                return
            }

            synchronized(this) {
                // Double-check inside the synchronized block
                if (isDisposed || project.isDisposed) {
                    logger.info("Skipping settings change for disposed LSPProcessHolder or project")
                    return
                }

                terminate()
                if (InferenceGlobalContext.xDebugLSPPort != null) {
                    capabilities = getCaps()
                    isWorking = true
                    lspProjectInitialize(this, project)
                    return
                }
                startProcess()
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            // This exception can occur during shutdown when schedulers are already closed
            // but there are still pending tasks trying to use them
            if (e.message?.contains("Already shutdown") == true) {
                logger.info("Ignoring RejectedExecutionException during shutdown: ${e.message}")
            } else {
                // Rethrow other types of RejectedExecutionException
                throw e
            }
        }
    }

    open var capabilities: LSPCapabilities = LSPCapabilities()
        set(newValue) {
            if (newValue == field) return
            field = newValue
            if(!project.isDisposed) {
                project.messageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC).capabilitiesChanged(field)
            }
        }

    open fun startProcess() {
        val address = if (InferenceGlobalContext.inferenceUri == null) "Refact" else InferenceGlobalContext.inferenceUri
        val newConfig = LSPConfig(
            address = address,
            apiKey = AccountManager.apiKey,
            port = 0,
            clientVersion = "${Resources.client}-${Resources.version}/${Resources.jbBuildVersion}",
            useTelemetry = true,
            deployment = InferenceGlobalContext.deploymentMode,
            ast = InferenceGlobalContext.astIsEnabled,
            astFileLimit = InferenceGlobalContext.astFileLimit,
            vecdb = InferenceGlobalContext.vecdbIsEnabled,
            vecdbFileLimit = InferenceGlobalContext.vecdbFileLimit,
            insecureSSL = InferenceGlobalContext.insecureSSL,
            experimental = InferenceGlobalContext.experimentalLspFlagEnabled,
        )

        val processIsAlive = process?.isAlive == true

        if (newConfig == lastConfig && processIsAlive) return

        capabilities = LSPCapabilities()
        terminate()
        if (!newConfig.isValid) return
        var attempt = 0
        while (attempt < 5) {
            try {
                if (BIN_PATH == null) {
                    attempt = 0 // wait for initialize()
                    logger.warn("LSP start_process BIN_PATH is null; waiting...")
                    Thread.sleep(1000)
                    continue
                }
                newConfig.port = (32000..32199).random()
                logger.warn("LSP start_process " + BIN_PATH + " " + newConfig.toArgs())
                process = GeneralCommandLine(listOf(BIN_PATH) + newConfig.toArgs()).withRedirectErrorStream(true)
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
                logger.warn("LSP bad_things_happened " + process1.inputStream.bufferedReader().use { it.readText() })
            }
        }
        attempt = 0
        while (attempt < 5) {
            try {
                InferenceGlobalContext.connection.ping(url)
                buildInfo = getBuildInfo()
                logger.warn("LSP binary build info $buildInfo")
                capabilities = getCaps()
                fetchCustomization()
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

    fun fetchCustomization(): JsonObject? {
        if (!isWorking) return getCustomizationDirectly()
        try {
            val config = InferenceGlobalContext.connection.get(url.resolve("/v1/customization"), dataReceiveEnded = {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = null
            }, errorDataReceived = {}, failedDataReceiveEnded = {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                if (it != null) {
                    InferenceGlobalContext.lastErrorMsg = it.message
                }
            }).join().get()
            return Gson().fromJson(config as String, JsonObject::class.java)
        } catch (e: Exception) {
            logger.warn("LSP fetchCustomization error " + e.message)
            return null
        }
    }

    private fun lspRagStatusSync() {
        try {
            if (!isWorking) {
                ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 5000, TimeUnit.MILLISECONDS)
                return
            }
            val ragStatus = getRagStatus()
            if (ragStatus == null) {
                ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 5000, TimeUnit.MILLISECONDS)
                return
            }
            if (ragStatus != ragStatusCache) {
                ragStatusCache = ragStatus
                project.messageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC).ragStatusChanged(ragStatusCache!!)
            }

            if (ragStatus.ast != null && ragStatus.ast.astMaxFilesHit) {
                ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 5000, TimeUnit.MILLISECONDS)
                return
            }
            if (ragStatus.vecdb != null && ragStatus.vecdb.vecdbMaxFilesHit) {
                ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 5000, TimeUnit.MILLISECONDS)
                return
            }

            if ((ragStatus.ast != null && listOf("starting", "parsing", "indexing").contains(ragStatus.ast.state))
                || (ragStatus.vecdb != null && listOf("starting", "parsing").contains(ragStatus.vecdb.state))
            ) {
                logger.info("ast or vecdb is still indexing")
                ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 700, TimeUnit.MILLISECONDS)
            } else {
                logger.info("ast and vecdb status complete, slowdown poll")
                ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 5000, TimeUnit.MILLISECONDS)
            }
        } catch (e: Exception) {
            ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 5000, TimeUnit.MILLISECONDS)
        }
    }


    private fun safeTerminate() {
        InferenceGlobalContext.connection.get(
            URI(
                "http://127.0.0.1:${lastConfig!!.port}/v1/graceful-shutdown"
            )
        ).get().get()
    }

    private fun terminate() {
        isWorking = false
        process?.let {
            try {
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

    override fun dispose() {
        // Set the disposed flag to prevent race conditions
        isDisposed = true

        // Shutdown all schedulers and terminate the process
        try {
            ragStatusCheckerScheduler.shutdown()
            terminate()
            healthCheckerScheduler.shutdown()
            loggerScheduler.shutdown()
            Runtime.getRuntime().removeShutdownHook(exitThread)
        } catch (e: Exception) {
            // Log any exceptions during disposal but don't let them propagate
            logger.warn("Exception during LSPProcessHolder disposal: ${e.message}")
        }
    }

    private fun getBuildInfo(): String {
        var res = ""
        InferenceGlobalContext.connection.get(url.resolve("/build_info"), dataReceiveEnded = {
            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            InferenceGlobalContext.lastErrorMsg = null
        }, errorDataReceived = {}, failedDataReceiveEnded = {
            InferenceGlobalContext.status = ConnectionStatus.ERROR
            if (it != null) {
                InferenceGlobalContext.lastErrorMsg = it.message
            }
        }).also {
            try {
                res = it.get().get() as String
                logger.warn("build_info request finished")
            } catch (e: Exception) {
                logger.warn("build_info ${e.message}")
            }
        }
        return res
    }

    open val url: URI
        get() {
            val port = InferenceGlobalContext.xDebugLSPPort ?: lastConfig?.port ?: return URI("")

            return URI("http://127.0.0.1:${port}/")
        }

    open fun getCaps(): LSPCapabilities {
        var res = LSPCapabilities()
        InferenceGlobalContext.connection.get(url.resolve("/v1/caps"), dataReceiveEnded = {
            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            InferenceGlobalContext.lastErrorMsg = null
        }, errorDataReceived = {}, failedDataReceiveEnded = {
            if (it != null) {
                InferenceGlobalContext.lastErrorMsg = it.message
            }
        }).also {
            val requestFuture: ComplexFuture<*>?
            try {
                requestFuture = it.get() as ComplexFuture
                val out = requestFuture.get()
                logger.debug("LSP caps_received $out")
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
        InferenceGlobalContext.connection.get(url.resolve("/v1/rag-status"),
            requestProperties = mapOf("redirect" to "follow", "cache" to "no-cache", "referrer" to "no-referrer"),
            dataReceiveEnded = {
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

    companion object {
        var BIN_PATH: String? = null
        private var TMP_BIN_PATH: String? = null

        @JvmStatic
        fun getInstance(project: Project): LSPProcessHolder? = project.service()

        var buildInfo: String = ""
        private val initialized = AtomicBoolean(false)
        private val logger = Logger.getInstance("LSPProcessHolder")

        private fun generateMD5HexAndWriteInTmpFile(input: InputStream, tmpFileName: File): String {
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(1024)
            var bytesRead: Int
            val fileOut = FileOutputStream(tmpFileName)
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
                fileOut.write(buffer, 0, bytesRead)
            }
            fileOut.flush()
            fileOut.close()
            input.close()
            return digest.digest().joinToString("") { String.format("%02x", it) }
        }

        // only one time
        fun initialize() {
            logger.warn("LSP initialize start")
            val shouldInitialize = !initialized.getAndSet(true)
            if (!shouldInitialize) return

            Companion::class.java.getResourceAsStream(
                "/bin/${binPrefix}/refact-lsp${getExeSuffix()}"
            ).use { input ->
                if (input == null) {
                    emitError("LSP server is not found for host operating system, please contact support")
                } else {
                    val tmpFileName =
                        Path(getTempDirectory(), "${UUID.randomUUID().toString()}${getExeSuffix()}").toFile()
                    TMP_BIN_PATH = tmpFileName.toString()
                    val hash = generateMD5HexAndWriteInTmpFile(input, tmpFileName)
                    BIN_PATH = Path(
                        getTempDirectory(),
                        ApplicationInfo.getInstance().build.toString()
                            .replace(Regex("[^A-Za-z0-9 ]"), "_") + "_refact_lsp_${hash}${getExeSuffix()}"
                    ).toString()
                    var shouldUseTmp = false
                    for (i in 0..4) {
                        try {
                            val path = Paths.get(BIN_PATH!!)
                            path.parent.toFile().mkdirs()
                            if (tmpFileName.renameTo(path.toFile())) {
                                setExecutable(path.toFile())
                            }
                            shouldUseTmp = false
                            break
                        } catch (e: Exception) {
                            logger.warn("LSP bad_things_happened: can not save binary $BIN_PATH")
                            logger.warn("LSP bad_things_happened: error message - ${e.message}")
                            shouldUseTmp = true
                        }
                    }
                    if (shouldUseTmp) {
                        setExecutable(tmpFileName)
                        BIN_PATH = TMP_BIN_PATH
                    } else {
                        if (tmpFileName.exists()) {
                            tmpFileName.deleteOnExit()
                        }
                    }
                }
            }
            logger.warn("LSP initialize finished")
            logger.warn("LSP initialize BIN_PATH=$BIN_PATH")
        }

        // run after close application
        fun cleanup() {

        }

        fun getCustomizationDirectly(): JsonObject? {
            if (BIN_PATH == null) {
                return null
            }
            val process = GeneralCommandLine(listOf(BIN_PATH, "--print-customization")).withRedirectErrorStream(true)
                .createProcess()
            val isExit = process.waitFor(3, TimeUnit.SECONDS)
            if (isExit) {
                if (process.exitValue() != 0) {
                    logger.warn("LSP bad_things_happened " + process.inputStream.bufferedReader().use { it.readText() })
                    return null
                }
            } else {
                process.destroy() // win11 doesn't finish process safe
            }

            val out = process.inputStream.bufferedReader().use { it.readText() }
            val customizationStr = out.trim().lines().last()
            return try {
                Gson().fromJson(customizationStr, JsonObject::class.java)
            } catch (e: Exception) {
                logger.warn("LSP can not parse json string $customizationStr")
                logger.warn("LSP can not parse json string error = ${e.message}")
                null
            }
        }
    }
}