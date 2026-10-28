package com.leapauto.app

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One running task plus a bounded queue; duplicate keys join the existing flight. */
internal class BoundedTaskLane(capacity: Int, name: String) {
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(capacity), { runnable -> Thread(runnable, name) })
    private val tasks = mutableMapOf<String, FutureTask<Unit>>()

    @Synchronized
    fun submit(key: String, action: () -> Unit): Boolean {
        if (executor.isShutdown || tasks.containsKey(key)) return false
        val task = object : FutureTask<Unit>(java.util.concurrent.Callable { action(); Unit }) {
            override fun done() {
                synchronized(this@BoundedTaskLane) {
                    if (tasks[key] === this) tasks.remove(key)
                }
            }
        }
        tasks[key] = task
        return try {
            executor.execute(task)
            true
        } catch (_: RejectedExecutionException) {
            tasks.remove(key)
            false
        }
    }

    @Synchronized
    fun cancelAll() {
        tasks.values.toList().forEach { it.cancel(true) }
        tasks.clear()
        executor.queue.clear()
    }

    fun close() {
        cancelAll()
        executor.shutdownNow()
    }
}

/** Main-thread policy. A flight includes result delivery, so queued callbacks cannot accumulate. */
internal class TelemetryRefreshGate(private val intervalMs: Long = 1_000L) {
    private var sequence = 0L
    private var active: Long? = null
    private var lastStartedAt: Long? = null

    fun begin(nowMs: Long): Long? {
        if (active != null || lastStartedAt?.let { nowMs - it < intervalMs } == true) return null
        lastStartedAt = nowMs
        return (++sequence).also { active = it }
    }

    fun isCurrent(ticket: Long): Boolean = active == ticket

    fun finish(ticket: Long) {
        if (active == ticket) active = null
    }

    fun cancel() {
        active = null
        lastStartedAt = null
    }
}
