package com.leapauto.app

import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

enum class ErrorLogCategory { API_FAILURE, CONTROL_FAILURE, LOGIN_FAILURE, SESSION_EXPIRED, PARSE_FAILURE, PAGE_ERROR }

data class ErrorLogEntry(
    val timestampMs: Long,
    val category: ErrorLogCategory,
    val stage: String,
    val httpStatus: Int? = null,
    val durationMs: Long? = null,
    val retryCount: Int = 0,
    val appVersion: String,
    val message: String
) {
    fun format(): String = buildString {
        appendLine("时间: ${Instant.ofEpochMilli(timestampMs)}")
        appendLine("错误类别: ${category.name}")
        appendLine("接口阶段: $stage")
        appendLine("HTTP状态: ${httpStatus ?: "-"}")
        appendLine("耗时: ${durationMs?.let { "${it} ms" } ?: "-"}")
        appendLine("重试次数: $retryCount")
        appendLine("应用版本: $appVersion")
        append("错误正文:\n$message")
    }
}

/** In-memory diagnostic queue. It never persists or uploads raw error text. */
class ErrorLogRepository(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val ttlMs: Long = 30 * 60 * 1000L,
    private val capacity: Int = 200
) {
    private val lock = ReentrantReadWriteLock()
    private val entries = ArrayDeque<ErrorLogEntry>()

    fun record(entry: ErrorLogEntry) {
        lock.write {
            purgeLocked()
            entries.addLast(entry)
            while (entries.size > capacity) entries.removeFirst()
        }
    }

    fun list(): List<ErrorLogEntry> = lock.write {
        purgeLocked()
        entries.toList().sortedByDescending { it.timestampMs }
    }

    fun copyText(): String = list().joinToString("\n\n") { it.format() }

    fun clear() = lock.write { entries.clear() }

    private fun purgeLocked() {
        val cutoff = clockMs() - ttlMs
        while (entries.isNotEmpty() && entries.first().timestampMs < cutoff) entries.removeFirst()
    }
}

object ErrorLogs {
    val repository = ErrorLogRepository()
}
