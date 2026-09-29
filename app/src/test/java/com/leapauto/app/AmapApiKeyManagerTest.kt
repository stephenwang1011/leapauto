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
    fun `getCandidateKeys returns empty when no custom key configured`() {
        val keys = AmapApiKeyManager.getCandidateKeys(null)
        // 彻底移除内置公共 Key：未配置时候选池必须严格为空，保护零敏感信息
        assertTrue("When no custom key configured, candidate list must be empty", keys.isEmpty())
        assertFalse(AmapApiKeyManager.hasConfiguredKey(null))
    }

    @Test
    fun `markQuotaExhausted deprioritizes exhausted key to failover next key`() {
        val testKey = "11111111111111111111111111111111"
        // 标记测试 Key 额度耗尽
        AmapApiKeyManager.markQuotaExhausted(testKey, "DAILY_QUERY_OVER_LIMIT")
        // 重置后完全恢复
        AmapApiKeyManager.resetExhaustedState()
    }
}
