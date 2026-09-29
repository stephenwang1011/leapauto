package com.leapauto.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AmapApiKeyManagerTest {

    @Before
    @After
    fun cleanup() {
        AmapApiKeyManager.resetExhaustedState()
    }

    @Test
    fun `isQuotaExhausted correctly identifies quota and invalid key errors`() {
        // 成功状态不属于配额超限
        assertFalse(AmapApiKeyManager.isQuotaExhausted("1", "10000"))

        // 常见配额与限制错误码
        assertTrue(AmapApiKeyManager.isQuotaExhausted("0", "10003")) // DAILY_QUERY_OVER_LIMIT
        assertTrue(AmapApiKeyManager.isQuotaExhausted("0", "10044")) // USER_DAILY_QUERY_OVER_LIMIT
        assertTrue(AmapApiKeyManager.isQuotaExhausted("0", "10014")) // QPS_HAS_EXCEEDED_THE_LIMIT
        assertTrue(AmapApiKeyManager.isQuotaExhausted("0", "10001")) // INVALID_USER_KEY
        assertTrue(AmapApiKeyManager.isQuotaExhausted("0", "10019")) // USER_KEY_RECYCLED

        // 普通业务或参数错误
        assertFalse(AmapApiKeyManager.isQuotaExhausted("0", "20000"))
        assertFalse(AmapApiKeyManager.isQuotaExhausted(null, null))
    }

    @Test
    fun `getCandidateKeys returns built-in keys when no custom key configured`() {
        val keys = AmapApiKeyManager.getCandidateKeys(null)
        assertTrue("Candidate keys should not be empty", keys.isNotEmpty())
        // 验证主备两枚 Key 均包含在内
        assertTrue("Should contain primary key", keys.contains("468e462adad376c2aa08d252ae20fcba"))
        assertTrue("Should contain secondary key", keys.contains("41c317c16afa9d53626a9d7c0513d956"))
    }

    @Test
    fun `markQuotaExhausted deprioritizes exhausted key to failover next key`() {
        val keysBefore = AmapApiKeyManager.getCandidateKeys(null)
        val primaryKey = keysBefore.first()

        // 标记主 Key 额度耗尽
        AmapApiKeyManager.markQuotaExhausted(primaryKey, "DAILY_QUERY_OVER_LIMIT")

        val keysAfter = AmapApiKeyManager.getCandidateKeys(null)
        // 主 Key 被冷冻排除，备用 Key 跃升为第一候选
        assertFalse("Exhausted key should be filtered out from active healthy candidates", keysAfter.contains(primaryKey))
        assertTrue("Secondary key should still be available", keysAfter.isNotEmpty())

        // 重置后完全恢复
        AmapApiKeyManager.resetExhaustedState()
        val keysRecovered = AmapApiKeyManager.getCandidateKeys(null)
        assertEquals(keysBefore, keysRecovered)
    }
}
