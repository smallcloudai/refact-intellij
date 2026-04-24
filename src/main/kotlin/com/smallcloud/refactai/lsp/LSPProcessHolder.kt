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
import java.net.ServerSocket
import java.net.URI
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
    private val lifecycleScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCLSPLifecycleScheduler", 1
    )
    private val lifecycleWorkerRunning = AtomicBoolean(false)
    private val lifecycleStartRequested = AtomicBoolean(false)
    private val lifecycleRestartRequested = AtomicBoolean(false)
    private val lifecycleReason = AtomicReference("initial")
    @Volatile
    private var customizationCache: JsonObject? = null

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

    private fun logIfBlockingOperationOnEdt(operation: String) {
        if (ApplicationManager.getApplication().isDispatchThread) {
            logger.error("LSP blocking operation '$operation' called on EDT")
        }
    }

    private fun isCustomPortConfigured(): Boolean {
        return InferenceGlobalContext.xDebugLSPPort != null
    }

    private fun shouldAbortLifecycleWork(): Boolean {
        return isDisposed || project.isDisposed
    }

    private fun requestLifecycleWork(reason: String, restart: Boolean) {
        try {
            if (isDisposed || project.isDisposed) {
                logger.info("Skipping lifecycle work for disposed LSPProcessHolder or project")
                return
            }

            lifecycleStartRequested.set(true)
            if (restart) {
                lifecycleRestartRequested.set(true)
            }
            lifecycleReason.set(reason)
            scheduleLifecycleWorkerIfNeeded()
        } catch (e: RejectedExecutionException) {
            if (e.message?.contains("Already shutdown") == true) {
                logger.info("Ignoring RejectedExecutionException during lifecycle scheduling: ${e.message}")
            } else {
                throw e
            }
        }
    }

    private fun scheduleLifecycleWorkerIfNeeded() {
        if (!lifecycleWorkerRunning.compareAndSet(false, true)) return

        try {
            lifecycleScheduler.submit {
                runLifecycleWorker()
            }
        } catch (e: RejectedExecutionException) {
            lifecycleWorkerRunning.set(false)
            if (e.message?.contains("Already shutdown") == true) {
                logger.info("Ignoring RejectedExecutionException during lifecycle startup: ${e.message}")
            } else {
                throw e
            }
        }
    }

    private fun runLifecycleWorker() {
        try {
            while (!isDisposed && !project.isDisposed) {
                val shouldRestart = lifecycleRestartRequested.getAndSet(false)
                val shouldStart = lifecycleStartRequested.getAndSet(false)
                if (!shouldRestart && !shouldStart) {
                    break
                }

                val reason = lifecycleReason.getAndSet("coalesced")
                logger.debug("Lifecycle worker run: restart=$shouldRestart start=$shouldStart reason=$reason")
                if (shouldRestart) {
                    applySettingsChangeBlocking(reason)
                } else {
                    ensureStartedBlocking(reason)
                }
            }
        } catch (e: Exception) {
            logger.warn("Exception during lifecycle worker: ${e.message}")
        } finally {
            lifecycleWorkerRunning.set(false)
            if (!isDisposed && !project.isDisposed && (lifecycleStartRequested.get() || lifecycleRestartRequested.get())) {
                scheduleLifecycleWorkerIfNeeded()
            }
        }
    }

    private fun applySettingsChangeBlocking(reason: String) {
        if (shouldAbortLifecycleWork()) {
            logger.info("Skipping settings change for disposed LSPProcessHolder or project")
            return
        }

        initialize()
        logger.info("Applying LSP settings change: $reason")
        customizationCache = null

        if (isCustomPortConfigured()) {
            terminate()
            capabilities = getCaps()
            isWorking = true
            lspProjectInitialize(this, project)
            return
        }

        startProcess()
    }

    protected open fun ensureStartedBlocking(reason: String) {
        if (shouldAbortLifecycleWork()) {
            logger.info("Skipping ensure-started for disposed LSPProcessHolder or project")
            return
        }

        initialize()
        logger.debug("Ensuring LSP is started: $reason")

        if (isCustomPortConfigured()) {
            if (!isWorking) {
                capabilities = getCaps()
                isWorking = true
                lspProjectInitialize(this, project)
            }
            return
        }

        if (!isWorking || process?.isAlive != true || lastConfig == null) {
            startProcess()
        }
    }

    open fun ensureStartedAsync(reason: String = "external-request") {
        requestLifecycleWork(reason, restart = false)
    }

    fun hasPendingLifecycleWork(): Boolean {
        return lifecycleStartRequested.get() || lifecycleRestartRequested.get() || lifecycleWorkerRunning.get()
    }

    fun ensureStartedIfNeeded(reason: String = "external-request") {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            ensureStartedAsync(reason)
        } else {
            ensureStartedBlocking(reason)
        }
    }

    init {
        messageBus.connect(this).subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
            override fun apiKeyChanged(newApiKey: String?) {
                settingsChanged("account-api-key-changed")
            }

            override fun planStatusChanged(newPlan: String?) {
                settingsChanged("account-plan-status-changed")
            }
        })
        messageBus.connect(this)
            .subscribe(InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                override fun userInferenceUriChanged(newUrl: String?) {
                    settingsChanged("inference-uri-changed")
                }

                override fun astFlagChanged(newValue: Boolean) {
                    settingsChanged("ast-flag-changed")
                }

                override fun astFileLimitChanged(newValue: Int) {
                    settingsChanged("ast-file-limit-changed")
                }

                override fun vecdbFlagChanged(newValue: Boolean) {
                    settingsChanged("vecdb-flag-changed")
                }

                override fun vecdbFileLimitChanged(newValue: Int) {
                    settingsChanged("vecdb-file-limit-changed")
                }

                override fun xDebugLSPPortChanged(newPort: Int?) {
                    settingsChanged("debug-port-changed")
                }

                override fun insecureSSLChanged(newValue: Boolean) {
                    settingsChanged("insecure-ssl-changed")
                }

                override fun experimentalLspFlagEnabledChanged(newValue: Boolean) {
                    settingsChanged("experimental-flag-changed")
                }
            })

        Runtime.getRuntime().addShutdownHook(exitThread)

        healthCheckerScheduler.scheduleWithFixedDelay({
            try {
                // Check if we're already disposed before proceeding
                if (isDisposed || project.isDisposed) {
                    logger.info("Skipping health check for disposed LSPProcessHolder or project")
                    return@scheduleWithFixedDelay
                }

                if (lastConfig == null) return@scheduleWithFixedDelay
                if (isCustomPortConfigured()) return@scheduleWithFixedDelay
                if (process?.isAlive == false || !isWorking) {
                    ensureStartedAsync("health-check-process-dead-or-unready")
                }
            } catch (e: RejectedExecutionException) {
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

    open fun settingsChanged(reason: String = "settings-changed") {
        requestLifecycleWork(reason, restart = true)
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
        logIfBlockingOperationOnEdt("startProcess")
        val startedAt = System.currentTimeMillis()
        if (shouldAbortLifecycleWork()) return
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

        if (newConfig.sameRuntimeSettings(lastConfig) && processIsAlive && isWorking) return

        capabilities = LSPCapabilities()
        terminate()
        if (!newConfig.isValid) return
        var attempt = 0
        while (attempt < 5) {
            if (shouldAbortLifecycleWork()) {
                logger.info("Aborting LSP startup during spawn loop: disposed")
                return
            }
            val bin = BIN_PATH
            if (bin == null) {
                logger.warn("LSP start_process BIN_PATH is null")
                return
            }
            val port = allocateFreePort()
            if (port == null) {
                logger.warn("LSP start_process could not allocate a free port")
                attempt++
                continue
            }
            newConfig.port = port
            logger.debug("LSP start_process $bin ${newConfig.toSafeLogString()}")
            val spawnedProcess = try {
                GeneralCommandLine(listOf(bin) + newConfig.toArgs()).withRedirectErrorStream(true).createProcess()
            } catch (e: Exception) {
                attempt++
                logger.warn("LSP start_process spawn failed attempt=$attempt: ${e.message}")
                if (attempt == 5) {
                    logger.error("LSP process failed to start after 5 attempts", e)
                    isWorking = false
                }
                continue
            }

            val outputLines = ArrayDeque<String>(200)
            val gobbler = loggerScheduler.submit {
                try {
                    spawnedProcess.inputStream.bufferedReader().forEachLine { line ->
                        logger.debug(line)
                        synchronized(outputLines) {
                            if (outputLines.size >= 200) outputLines.removeFirst()
                            outputLines.addLast(line)
                        }
                    }
                } catch (_: Exception) {}
            }

            Thread.sleep(500)
            if (spawnedProcess.isAlive) {
                process = spawnedProcess
                spawnedProcess.onExit().thenAcceptAsync { p ->
                    if (p.exitValue() != 0) logger.warn("LSP process exited with code ${p.exitValue()}")
                }
                loggerTask = gobbler
                break
            }

            gobbler.cancel(false)
            val exitCode = runCatching { spawnedProcess.exitValue() }.getOrDefault(-1)
            val captured = synchronized(outputLines) { outputLines.joinToString("\n") }
            attempt++
            logger.warn(
                "LSP start_process didn't start attempt=$attempt " +
                "(exit=$exitCode binary=$bin port=$port)\n$captured"
            )
            if (attempt == 5) {
                logger.error("LSP process failed to start after 5 attempts")
                isWorking = false
                return
            }
        }

        val startupUrl = URI("http://127.0.0.1:${newConfig.port}/")
        attempt = 0
        while (attempt < 5) {
            if (shouldAbortLifecycleWork()) {
                logger.info("Aborting LSP startup during readiness loop: disposed")
                terminate()
                return
            }
            try {
                InferenceGlobalContext.connection.ping(startupUrl)
                lastConfig = newConfig
                isWorking = true
                buildInfo = getBuildInfo()
                logger.warn("LSP binary build info $buildInfo")
                capabilities = getCaps()
                fetchCustomizationFromServer()?.also { customizationCache = it }
                break
            } catch (e: Exception) {
                logger.warn("LSP bad_things_happened " + e.message)
            }
            attempt++
            Thread.sleep(3000)
        }
        if (!isWorking) {
            logger.warn("LSP readiness probe failed after 5 attempts, terminating process")
            terminate()
            return
        }
        if (shouldAbortLifecycleWork()) {
            terminate()
            return
        }
        lspProjectInitialize(this, project)
        logger.info("LSP startProcess finished in ${System.currentTimeMillis() - startedAt}ms (working=$isWorking)")
    }

    open fun fetchCustomization(): JsonObject? {
        logIfBlockingOperationOnEdt("fetchCustomization")
        customizationCache?.let { return it }
        if (!isWorking) {
            val direct = getCustomizationDirectly()
            customizationCache = direct
            return direct
        }
        val server = fetchCustomizationFromServer()
        customizationCache = server
        return server
    }

    fun fetchCustomizationDirectly(): JsonObject? {
        logIfBlockingOperationOnEdt("fetchCustomizationDirectly")
        val direct = getCustomizationDirectly()
        customizationCache = direct
        return direct
    }

    fun getCachedCustomization(): JsonObject? {
        return customizationCache
    }

    private fun fetchCustomizationFromServer(): JsonObject? {
        val baseUrl = baseUrlOrNull() ?: return null
        try {
            val config = InferenceGlobalContext.connection.get(baseUrl.resolve("/v1/customization"), dataReceiveEnded = {
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
            if (ragStatusCheckerScheduler.isShutdown || ragStatusCheckerScheduler.isTerminated || project.isDisposed || isDisposed) {
                return
            }
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
                ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 700, TimeUnit.MILLISECONDS)
            } else {
                ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 5000, TimeUnit.MILLISECONDS)
            }
        } catch (_: Exception) {
            try {
                if (!ragStatusCheckerScheduler.isShutdown && !ragStatusCheckerScheduler.isTerminated) {
                    ragStatusCheckerScheduler.schedule({ lspRagStatusSync() }, 5000, TimeUnit.MILLISECONDS)
                }
            } catch (_: Exception) {
                // scheduler shut down between check and schedule, ignore
            }
        }
    }


    private fun safeTerminate() {
        val port = lastConfig?.port ?: return
        runCatching {
            InferenceGlobalContext.connection.get(URI("http://127.0.0.1:$port/v1/graceful-shutdown")).get()?.get()
        }
    }

    private fun terminate() {
        if (!isDisposed) {
            logIfBlockingOperationOnEdt("terminate")
        }
        isWorking = false
        val p = process ?: return
        process = null
        try {
            safeTerminate()
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroy()
                if (!p.waitFor(2, TimeUnit.SECONDS)) {
                    p.destroyForcibly()
                }
            }
        } catch (e: Exception) {
            logger.debug("Exception during LSP terminate", e)
            runCatching { p.destroyForcibly() }
        } finally {
            lastConfig = null
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
            lifecycleScheduler.shutdown()
            loggerScheduler.shutdown()
            Runtime.getRuntime().removeShutdownHook(exitThread)
        } catch (e: Exception) {
            // Log any exceptions during disposal but don't let them propagate
            logger.warn("Exception during LSPProcessHolder disposal: ${e.message}")
        }
    }

    private fun getBuildInfo(): String {
        logIfBlockingOperationOnEdt("getBuildInfo")
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
            val base = baseUrlOrNull() ?: return URI("")
            return base
        }

    open fun baseUrlOrNull(): URI? {
        val debugPort = InferenceGlobalContext.xDebugLSPPort
        if (debugPort != null && debugPort > 0) {
            return URI("http://127.0.0.1:${debugPort}/")
        }

        if (!isWorking || process?.isAlive != true) return null

        val port = lastConfig?.port ?: return null
        if (port <= 0) return null
        return URI("http://127.0.0.1:${port}/")
    }

    open fun getCaps(): LSPCapabilities {
        logIfBlockingOperationOnEdt("getCaps")
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
        logIfBlockingOperationOnEdt("getRagStatus")
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
        @Volatile
        var BIN_PATH: String? = null
        private var TMP_BIN_PATH: String? = null

        private fun allocateFreePort(): Int? {
            return try {
                ServerSocket(0).use { it.localPort }
            } catch (_: Exception) {
                null
            }
        }

        @JvmStatic
        fun getInstance(project: Project): LSPProcessHolder = project.service()

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

        @Synchronized
        fun initialize() {
            logger.warn("LSP initialize start")
            if (initialized.get()) return

            val input: InputStream? = Companion::class.java.getResourceAsStream(
                "/bin/${binPrefix}/refact-lsp${getExeSuffix()}"
            )
            if (input == null) {
                emitError("LSP server is not found for host operating system, please contact support")
                logger.warn("LSP initialize finished")
                return
            }
            input.use {
                val tmpFile = Path(getTempDirectory(), "${UUID.randomUUID()}${getExeSuffix()}").toFile()
                val hash = try {
                    generateMD5HexAndWriteInTmpFile(input, tmpFile)
                } catch (e: Exception) {
                    logger.warn("LSP initialize: failed to write temp binary: ${e.message}")
                    tmpFile.delete()
                    return
                }

                val targetName = ApplicationInfo.getInstance().build.toString()
                    .replace(Regex("[^A-Za-z0-9 ]"), "_") + "_refact_lsp_${hash}${getExeSuffix()}"
                val targetPath = Paths.get(getTempDirectory(), targetName)
                val targetFile = targetPath.toFile()

                var resolvedPath: String? = null

                for (attempt in 1..5) {
                    try {
                        targetPath.parent.toFile().mkdirs()
                        if (targetFile.exists()) {
                            if (targetFile.canExecute()) {
                                resolvedPath = targetFile.canonicalPath
                                break
                            }
                            setExecutable(targetFile)
                            if (targetFile.canExecute()) {
                                resolvedPath = targetFile.canonicalPath
                                break
                            }
                        }
                        java.nio.file.Files.move(
                            tmpFile.toPath(), targetPath,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        )
                        setExecutable(targetFile)
                        if (targetFile.exists() && targetFile.canExecute()) {
                            resolvedPath = targetFile.canonicalPath
                            break
                        }
                        logger.warn("LSP initialize: move succeeded but binary not ready (attempt $attempt)")
                    } catch (e: Exception) {
                        logger.warn("LSP initialize: attempt $attempt failed to install binary: ${e.message}")
                    }
                }

                if (resolvedPath == null) {
                    setExecutable(tmpFile)
                    if (tmpFile.exists() && tmpFile.canExecute()) {
                        logger.warn("LSP initialize: using temp path as fallback")
                        resolvedPath = tmpFile.canonicalPath
                        TMP_BIN_PATH = resolvedPath
                    } else {
                        logger.warn("LSP initialize: binary could not be installed or made executable — giving up")
                        tmpFile.delete()
                        return
                    }
                } else {
                    if (tmpFile.exists()) tmpFile.deleteOnExit()
                }

                BIN_PATH = resolvedPath
                initialized.set(true)
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
            val out = process.inputStream.bufferedReader().use { it.readText() }
            if (isExit) {
                if (process.exitValue() != 0) {
                    logger.warn("LSP bad_things_happened $out")
                    return null
                }
            } else {
                process.destroy()
                return null
            }
            val trimmed = out.trim()
            val jsonStart = trimmed.indexOf('{')
            val jsonEnd = trimmed.lastIndexOf('}')
            if (jsonStart < 0 || jsonEnd < 0 || jsonEnd <= jsonStart) {
                logger.warn("LSP customization output does not contain valid JSON: $trimmed")
                return null
            }
            val customizationStr = trimmed.substring(jsonStart, jsonEnd + 1)
            return try {
                Gson().fromJson(customizationStr, JsonObject::class.java)
            } catch (e: Exception) {
                logger.warn("LSP can not parse json string: ${e.message}")
                null
            }
        }
    }
}
