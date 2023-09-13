package com.smallcloud.refactai.statistic

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.io.sendRequest
import com.smallcloud.refactai.statistic.utils.parse
import com.smallcloud.refactai.struct.DeploymentMode
import com.smallcloud.refactai.utils.getExtension
import dev.gitlive.difflib.DiffUtils
import dev.gitlive.difflib.patch.DeltaType
import git4idea.repo.GitRepository
import io.github.kezhenxu94.cache.lite.impl.LRUCache
import io.github.kezhenxu94.cache.lite.impl.PerpetualCache
import java.security.MessageDigest
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

private data class Stat(val text: String, val startCursor: Int, val finishCursor: Int, val completion: String)
private data class Event(val project: String, val lang: String, val model: String,
                         val robotNumber: Int, val humanNumber: Int) {
    val score: Float = robotNumber.toFloat() / (robotNumber + humanNumber).toFloat()
}

private fun lruCache(func: (rep: Repository) -> String?): (rep: Repository) -> String? {
    val cache = LRUCache(PerpetualCache<Repository, String?>())
    fun wrapper(rep: Repository): String? {
        val lCache = cache[rep]
        if (lCache != null) {
            return lCache
        }
        val nCache = func(rep)
        cache[rep] = nCache
        return nCache
    }
    return ::wrapper
}

fun toSHA256(text: String): String {
    return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
        "%02x".format(it)
    }.slice(0..15)
}


class HumanRobotStatistic: Disposable {
    private val editorToPreviousStat: LinkedHashMap<Editor, Stat> = linkedMapOf()
    private val history: MutableList<Event> = mutableListOf()
    private val cron = AppExecutorUtil.createBoundedScheduledExecutorService(
            "SMCHumanRobotCron", 1
    )
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "SMCHumanRobotScheduler", 1
    )
    private var task: Future<*>? = null
    private fun createTask() : Future<*> {
        return cron.schedule({
            report()
            task = cron.scheduleWithFixedDelay({
                report()
            }, 1, 1, TimeUnit.HOURS)
        }, 1, TimeUnit.MINUTES)
    }

    init {
        if (InferenceGlobalContext.isCloud) {
            task = createTask()
        }
        ApplicationManager.getApplication().messageBus
                .connect(this).subscribe(
                        InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                    override fun deploymentModeChanged(newMode: DeploymentMode) {
                        if (task != null) {
                            if (newMode != DeploymentMode.CLOUD) {
                                task?.cancel(false)
                                task?.get()
                                task = null
                            }
                        } else {
                            if (newMode == DeploymentMode.CLOUD) {
                                task = createTask()
                            }
                        }
                    }
                }
                )
    }

    private fun report(): Future<*>? {
        if (history.isEmpty()) return null
        val token: String = AccountManager.instance.apiKey ?: return null

        val headers = mutableMapOf(
                "Content-Type" to "application/json",
                "Authorization" to "Bearer $token"
        )
        val url = Resources.defaultTabReportUrl
        var oldHistory: List<Event>
        synchronized(this) {
            oldHistory = history.toList()
            history.clear()
        }

        return AppExecutorUtil.getAppExecutorService().submit {
            try {
                val gson = Gson()
                val body = gson.toJson(
                        mapOf(
                                "client_version" to "${Resources.client}-${Resources.version}",
                                "ide_version" to Resources.jbBuildVersion,
                                "usage" to prepareHistory(oldHistory)
                        )
                )
                val res = sendRequest(url, "POST", headers, body)
                if (res.body.isNullOrEmpty()) return@submit

                val json = gson.fromJson(res.body, JsonObject::class.java)
                val retcode = if (json.has("retcode")) json.get("retcode").asString else null
                if (retcode != null && retcode != "OK") {
                    throw Exception(json.get("human_readable_message").asString)
                }
            } catch (e: Exception) {
                Logger.getInstance(instance::class.java).warn("report to $url failed: $e")
                history.addAll(oldHistory)
                UsageStats.instance.addStatistic(
                        false, UsageStatistic("robot/human usage stats report"),
                        url.toString(), e
                )
            }
        }
    }


    private fun prepareHistory(stats: List<Event>): String {
        val res = mutableMapOf<String, MutableMap<String, Any>>()
        for (event in stats) {
            val key = "${event.project}/${event.lang}/${event.model}"
            if (!res.containsKey(key)) {
                res[key] = mutableMapOf(
                        "project_hash" to event.project,
                        "file_ext" to event.lang,
                        "model_name" to event.model,
                        "edit_score" to mutableListOf(event.score),
                        "type_scores" to listOf(event.robotNumber, event.humanNumber)
                )
            } else {
                (res[key]?.get("edit_score") as MutableList<Float>).add(event.score)
                (res[key]?.get("type_scores") as MutableList<Int>)[0] += event.robotNumber
                (res[key]?.get("type_scores") as MutableList<Int>)[1] += event.humanNumber
            }
        }
        val gson = Gson()

        return gson.toJson(res.map { return@map mutableMapOf(
                "project_hash" to it.value["project_hash"],
                "file_ext" to it.value["file_ext"],
                "model_name" to it.value["model_name"],
                "edit_score" to (it.value["edit_score"] as MutableList<Float>).sum() /
                        (it.value["edit_score"] as MutableList<Float>).size,
                "count" to (it.value["edit_score"] as MutableList<Float>).size,
                "type_scores" to it.value["type_scores"]
        ) })
    }

    private fun getVirtualFile(editor: Editor): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(editor.document)
    }

    val getProject: (repository: Repository) -> String? = lruCache { repository ->
        if (repository is GitRepository) {
            var info = repository.info.remotes.filter { it.name == "origin" }.firstOrNull()
            if (info == null) {
                info = repository.info.remotes.firstOrNull()
            }
            if (info == null) {
                return@lruCache null
            }
            return@lruCache info.firstUrl?.let { parse(it).toHttps()?.let { it1 -> toSHA256(it1) } }
        }
        return@lruCache null
    }


    fun pushStat(editor: Editor, originalText: String, startCursor: Int, finishCursor: Int, completion: String) {
        scheduler.submit {
            if (!editorToPreviousStat.containsKey(editor)) {
                editorToPreviousStat[editor] = Stat(originalText, startCursor, finishCursor, completion)
                return@submit
            }

            val prevStat = editorToPreviousStat[editor]!!
            val vf = getVirtualFile(editor)!!
            val repoManager = VcsRepositoryManager.getInstance(editor.project!!)
            val repository = repoManager.repositories.firstOrNull {
                vf.toNioPath().toAbsolutePath()
                        .startsWith(it.root.toNioPath().toAbsolutePath())
            }
            val project = repository?.let { getProject(it) }
            try {
                val (robotNumber, humanNumber) = calculate(originalText, prevStat)
                history.add(Event(project ?: "", getExtension(vf.presentableName),
                        InferenceGlobalContext.lastAutoModel!!, robotNumber, humanNumber))
            } finally {
                editorToPreviousStat[editor] = Stat(originalText, startCursor, finishCursor, completion)
            }
        }
    }

    private fun calculate(newText: String, stat: Stat): Pair<Int, Int> {
        var robotNumber = stat.completion.length
        var humanNumber = 0

        val completedText = stat.text.replaceRange(stat.startCursor, stat.finishCursor, stat.completion)
        val completionPatch = DiffUtils.diff(stat.text.split("\n"), completedText.split("\n"))
        val hotLineIndexes = completionPatch.getDeltas().flatMap {d -> d.target.lines!!
                .mapIndexed { index, _ -> d.target.position + index} }
        val splitCompletedText = completedText.split("\n")
        val splitNewText = newText.split("\n")
        val linesCompletedAndNewPatch = DiffUtils.diff(splitCompletedText, splitNewText)
        linesCompletedAndNewPatch.getDeltas().forEach { lineDelta ->
            if (lineDelta.type == DeltaType.CHANGE) {
                val lPatch = DiffUtils.diff(
                        splitCompletedText[lineDelta.source.position].toList(),
                        splitNewText[lineDelta.target.position].toList(),
                )

                if (lineDelta.source.position in hotLineIndexes) {
                    lPatch.getDeltas().forEach {
                        if (it.type == DeltaType.INSERT) {
                            humanNumber += it.target.lines!!.size
                        } else if (it.type == DeltaType.CHANGE) {
                            robotNumber -= it.source.lines!!.size
                            humanNumber += it.target.lines!!.size
                        }
                    }
                } else {
                    lPatch.getDeltas().forEach {
                        if (it.type == DeltaType.CHANGE || it.type == DeltaType.INSERT) {
                            humanNumber += it.target.lines!!.size
                        }
                    }
                }
            } else if (lineDelta.type == DeltaType.INSERT) {
                lineDelta.target.lines?.forEach {
                    humanNumber += it.length
                }
            }
        }

        return Pair(maxOf(0, robotNumber), maxOf(0, humanNumber))
    }

    override fun dispose() {
        task?.cancel(true)
        task = null
    }

    companion object {
        @JvmStatic
        val instance: HumanRobotStatistic
            get() = ApplicationManager.getApplication().getService(HumanRobotStatistic::class.java)
    }
}