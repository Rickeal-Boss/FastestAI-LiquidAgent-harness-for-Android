package com.rickeal.liquidharness.core.agent.approval

import com.rickeal.liquidharness.core.model.ToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 会话级审批缓存：指纹维度（工具名 / 参数规范化）+ 有界策略。
 *
 * 并发语义由 `ConcurrentHashMap` + 写锁保证，不在单测范围（无法确定性复现竞态）。
 */
class ToolApprovalCacheTest {

    private fun call(name: String = "clipboard", args: String = "{\"action\":\"set\",\"text\":\"hi\"}") =
        ToolCall(name = name, argumentsJson = args)

    @Test
    fun unknownCallIsNotAllowed() {
        assertFalse(ToolApprovalCache().isAllowed(call()))
    }

    @Test
    fun grantedCallIsAllowed() {
        val cache = ToolApprovalCache()
        cache.allowForSession(call())
        assertTrue(cache.isAllowed(call()))
        assertEquals(1, cache.size())
    }

    @Test
    fun differentArgumentsAreNotCoveredByTheGrant() {
        val cache = ToolApprovalCache()
        cache.allowForSession(call(args = "{\"action\":\"set\",\"text\":\"hi\"}"))
        assertFalse(cache.isAllowed(call(args = "{\"action\":\"set\",\"text\":\"other\"}")))
    }

    @Test
    fun whitespaceAndKeyOrderAreNormalized() {
        val cache = ToolApprovalCache()
        cache.allowForSession(call(args = "{\"action\":\"set\",\"text\":\"hi\"}"))
        assertTrue(cache.isAllowed(call(args = "{ \"text\" : \"hi\" , \"action\" : \"set\" }")))
    }

    @Test
    fun differentToolNamesAreIsolated() {
        val cache = ToolApprovalCache()
        cache.allowForSession(call(name = "file_write"))
        assertFalse(cache.isAllowed(call(name = "file_read")))
    }

    @Test
    fun clearRevokesEverything() {
        val cache = ToolApprovalCache()
        cache.allowForSession(call())
        cache.clear()
        assertFalse(cache.isAllowed(call()))
        assertEquals(0, cache.size())
    }

    @Test
    fun cacheIsBoundedAndRejectsNewEntriesWhenFull() {
        val cache = ToolApprovalCache(maxEntries = 2)
        cache.allowForSession(call(args = "{\"n\":1}"))
        cache.allowForSession(call(args = "{\"n\":2}"))
        cache.allowForSession(call(args = "{\"n\":3}"))

        assertEquals(2, cache.size())
        // 已授权的两条仍在（有界策略是「拒绝新增」，不是淘汰旧授权）。
        assertTrue(cache.isAllowed(call(args = "{\"n\":1}")))
        assertTrue(cache.isAllowed(call(args = "{\"n\":2}")))
        assertFalse(cache.isAllowed(call(args = "{\"n\":3}")))
    }

    @Test
    fun fingerprintIsToolNamePlusDigest() {
        val fingerprint = ToolApprovalCache.fingerprint(call(name = "file_write", args = "{\"path\":\"a\"}"))
        assertTrue(fingerprint.startsWith("file_write|"))
        assertEquals(64, fingerprint.substringAfter("|").length)
    }

    @Test
    fun fingerprintIsStableAndDistinct() {
        assertEquals(
            ToolApprovalCache.fingerprint(call(args = "{\"text\":\"hi\"}")),
            ToolApprovalCache.fingerprint(call(args = "{\"text\":\"hi\"}")),
        )
        assertNotEquals(
            ToolApprovalCache.fingerprint(call(args = "{\"text\":\"hi\"}")),
            ToolApprovalCache.fingerprint(call(args = "{\"text\":\"ho\"}")),
        )
    }

    @Test
    fun malformedArgumentsStillProduceAStableFingerprint() {
        val malformed = "not json {"
        assertEquals(
            ToolApprovalCache.sha256Hex(malformed),
            ToolApprovalCache.fingerprint(call(args = malformed)).substringAfter("|"),
        )
    }

    @Test
    fun sha256MatchesTheWellKnownEmptyDigest() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ToolApprovalCache.sha256Hex(""),
        )
    }
}
