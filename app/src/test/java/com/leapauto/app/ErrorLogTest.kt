package com.leapauto.app

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

class ErrorLogTest {
    private fun entry(t: Long, category: ErrorLogCategory, msg: String = "原文 token-like text") =
        ErrorLogEntry(t, category, "stage", 500, 12, 1, "1.5.50", msg)

    @Test fun ttlCapacityAndOrdering() {
        val now = AtomicLong(3_600_000)
        val repo = ErrorLogRepository(now::get, ttlMs = 100, capacity = 2)
        repo.record(entry(3_499_999, ErrorLogCategory.API_FAILURE))
        repo.record(entry(3_599_950, ErrorLogCategory.PARSE_FAILURE, "new"))
        repo.record(entry(3_599_960, ErrorLogCategory.PAGE_ERROR, "newer"))
        assertEquals(listOf("newer", "new"), repo.list().map { it.message })
        now.set(3_600_200)
        assertTrue(repo.list().isEmpty())
    }

    @Test fun concurrentRecordsAreBounded() {
        val repo = ErrorLogRepository(capacity = 200)
        val pool = Executors.newFixedThreadPool(4); val latch = CountDownLatch(4)
        repeat(4) { pool.execute { repeat(100) { repo.record(entry(System.currentTimeMillis(), ErrorLogCategory.CONTROL_FAILURE)) }; latch.countDown() } }
        latch.await(); pool.shutdown()
        assertEquals(200, repo.list().size)
    }

    @Test fun allCategoriesAndCopyPreserveOriginal() {
        val repo = ErrorLogRepository()
        val now = System.currentTimeMillis()
        ErrorLogCategory.values().forEachIndexed { i, c -> repo.record(entry(now + i, c, "完整错误正文-$i")) }
        val text = repo.copyText()
        ErrorLogCategory.values().forEach { assertTrue(text.contains(it.name)) }
        assertTrue(text.contains("完整错误正文-4"))
        repo.clear(); assertTrue(repo.list().isEmpty())
    }

    @Test fun pageErrorAndStructuredFieldsAreRetained() {
        val now = System.currentTimeMillis()
        val repo = ErrorLogRepository({ now })
        repo.record(ErrorLogEntry(now, ErrorLogCategory.PAGE_ERROR, "vehicle_status_page", 503, 321, 2, "1.5.50", "页面失败正文"))
        val item = repo.list().single()
        assertEquals(ErrorLogCategory.PAGE_ERROR, item.category)
        assertEquals(503, item.httpStatus)
        assertEquals(321L, item.durationMs)
        assertEquals(2, item.retryCount)
        assertTrue(repo.copyText().contains("页面失败正文"))
    }
}
