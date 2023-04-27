package com.smallcloud.refactai.statistic

import java.lang.System.currentTimeMillis



private const val highBorder: Long = 1200
private const val step: Long = 600
private fun timeRange(time: Long) : Long {
    for (t in 600..highBorder step step) {
        if (time < t) {
            return t - step
        }
    }
    return highBorder
}

class CompletionStatistic {
    data class Statistic(val name: String,
                         val timeout: Long,
                         val reason: String? = null,
                         val fileExtension: String? = null)

    private val statistics: MutableList<Statistic> = mutableListOf()
    private var now = currentTimeMillis()

    fun addStatistic(name: String, reason: String? = null, fileExtension: String? = null) {
        val newNow = currentTimeMillis()
        statistics.add(Statistic(name, newNow - now, reason, fileExtension))
        now = newNow
    }

    fun getMetrics() : List<String> {
        val res = mutableListOf<String>()
        if (this.toString().contains("cacheRendered"))
            return res
        val lastElem = statistics.last()
        var suffix = when (lastElem.reason) {
            "esc" -> "esc"
            "tab" -> "tab"
            else -> "moveaway"
        }
        if (lastElem.fileExtension != null) {
            suffix += ":" + lastElem.fileExtension
        }
        res.add("metric0ms_${suffix}")
        if (lastElem.timeout > 600)
            res.add("metric600ms_${suffix}")
        if (lastElem.timeout > 1200)
            res.add("metric1200ms_${suffix}")
        return res
    }

    override fun toString(): String {
        val strings = mutableListOf<String>()
        statistics.forEach {
            var res = "${it.name}${timeRange(it.timeout)}ms"
            if (it.reason != null) {
                res += "(${it.reason})"
            }
            strings.add(res)
        }
        return strings.joinToString("/")
    }
}
