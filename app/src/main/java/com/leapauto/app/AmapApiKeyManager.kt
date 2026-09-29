package com.leapauto.app

import android.content.Context
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * 高德 Web 服务多 API Key 故障转移与配额熔断管理器：
 * 1. 自动聚合内置主 Key、备用 Key 以及用户自定义 Key；
 * 2. 毫秒级识别高德配额超限错误码 (10003: 日配额耗尽, 10044: 超限, 10014: QPS超限, 10001: Key无效等)；
 * 3. 额度耗尽时自动触发单 Key 熔断冷冻，并在当前请求中零感知原地自动重试下一枚备用 Key；
 * 4. 具备自愈机制：冷却期过后自动解除熔断状态，全链路自愈闭环。
 */
object AmapApiKeyManager {

    private const val TAG = "AmapApiKeyManager"

    // 高德官方定义的配额超限与无效 Key 错误码集合
    private val QUOTA_EXHAUSTED_CODES = setOf(
        "10001", // INVALID_USER_KEY: 用户key不正确或过期
        "10003", // DAILY_QUERY_OVER_LIMIT: 访问已超出日访问量
        "10004", // ACCESS_TOO_FREQUENT: 单位时间内访问过于频繁
        "10014", // QPS_HAS_EXCEEDED_THE_LIMIT: 云图服务QPS超限
        "10019", // USER_KEY_RECYCLED: 开发者key被删除或回收
        "10044"  // USER_DAILY_QUERY_OVER_LIMIT: 账号日调用量超限
    )

    // 记录各 Key 的熔断时间戳 (毫秒)
    private val exhaustedKeys = ConcurrentHashMap<String, Long>()

    // 熔断冷却期：默认 4 小时 (4 小时后自动重新允许探测，次日自然完全恢复)
    private const val EXHAUST_COOLING_MS = 4 * 3600 * 1000L

    /**
     * 获取当前可用的候选 Key 列表（完全基于用户个人专属配置，不再依赖任何内置公共 Key）。
     */
    fun getCandidateKeys(context: Context? = null): List<String> {
        val now = System.currentTimeMillis()
        val customKey = context?.let { SessionStore(it).loadCustomAmapWebKey() }?.trim()?.takeIf { it.length == 32 }
            ?: return emptyList()

        val exhaustedAt = exhaustedKeys[customKey] ?: 0L
        if (now - exhaustedAt > EXHAUST_COOLING_MS) {
            return listOf(customKey)
        }
        return emptyList()
    }

    /**
     * 检查用户是否已配置有效的高德专属 Key。
     */
    fun hasConfiguredKey(context: Context? = null): Boolean =
        context?.let { SessionStore(it).loadCustomAmapWebKey() }?.trim()?.length == 32

    /**
     * 判定高德 API 响应是否表示配额耗尽、被限流或 Key 无效。
     */
    fun isQuotaExhausted(status: String?, infocode: String?): Boolean {
        if (status == "0") {
            if (infocode != null && infocode.trim() in QUOTA_EXHAUSTED_CODES) {
                return true
            }
        }
        return false
    }

    /**
     * 标记指定 Key 今日额度已耗尽，触发自动熔断。
     */
    fun markQuotaExhausted(key: String, reason: String? = null) {
        val cleanKey = key.trim()
        if (cleanKey.isBlank()) return
        val now = System.currentTimeMillis()
        exhaustedKeys[cleanKey] = now
        val masked = if (cleanKey.length >= 8) "${cleanKey.take(4)}...${cleanKey.takeLast(4)}" else "***"
        try {
            Log.w(TAG, "高德 API Key [$masked] 额度超限/受限 ($reason)，已触发熔断，自动故障转移至备用 Key")
        } catch (_: Throwable) {
            println("[$TAG] 高德 API Key [$masked] 额度超限/受限 ($reason)，已触发熔断，自动故障转移至备用 Key")
        }
    }

    /**
     * 手动重置所有 Key 的熔断状态（如测试或用户手动刷新时）。
     */
    fun resetExhaustedState() {
        exhaustedKeys.clear()
    }
}
