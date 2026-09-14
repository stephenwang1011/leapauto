package com.leapauto.app

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class DeviceSecurityTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun allowTemporaryFileCleanup() {
        temporaryFolder.root.walkBottomUp().forEach { it.setWritable(true) }
    }

    @Test
    fun `trusted dex is verified and read only before reuse`() {
        val directory = temporaryFolder.newFolder("dex")
        val bytes = byteArrayOf(1, 2, 3, 4)
        val first = VerifiedDexAsset.prepare(directory, bytes)
        val second = VerifiedDexAsset.prepare(directory, bytes)
        assertEquals(first, second)
        assertArrayEquals(bytes, first.readBytes())
        assertFalse(first.canWrite())
        assertTrue(first.name.matches(Regex("shumei-[a-f0-9]{64}\\.dex")))
    }

    @Test
    fun `updated asset receives a different path and ignores legacy cache`() {
        val directory = temporaryFolder.newFolder("dex")
        File(directory, "shumei.dex").writeBytes(byteArrayOf(9))
        val previous = VerifiedDexAsset.prepare(directory, byteArrayOf(1, 2))
        val current = VerifiedDexAsset.prepare(directory, byteArrayOf(1, 3))
        assertNotEquals(previous, current)
        assertArrayEquals(byteArrayOf(1, 3), current.readBytes())
        assertFalse(current.canWrite())
    }

    @Test
    fun `corrupt previously extracted dex is replaced from the trusted asset`() {
        val directory = temporaryFolder.newFolder("dex")
        val bytes = byteArrayOf(1, 2, 3)
        val target = VerifiedDexAsset.prepare(directory, bytes)
        assertTrue(target.setWritable(true))
        target.writeBytes(byteArrayOf(9, 9, 9))
        assertTrue(target.setReadOnly())
        val repaired = VerifiedDexAsset.prepare(directory, bytes)
        assertEquals(target, repaired)
        assertArrayEquals(bytes, repaired.readBytes())
        assertFalse(repaired.canWrite())
    }

    @Test
    fun `valid writable cached dex is protected before reuse`() {
        val bytes = byteArrayOf(1, 2, 3)
        val target = VerifiedDexAsset.prepare(temporaryFolder.root, bytes)
        assertTrue(target.setWritable(true))
        assertEquals(target, VerifiedDexAsset.prepare(temporaryFolder.root, bytes))
        assertFalse(target.canWrite())
    }

    @Test
    fun `empty assets and invalid extraction directories fail explicitly`() {
        assertFailure(DeviceSecurityFailure.ASSET_INTEGRITY_FAILED) {
            VerifiedDexAsset.prepare(temporaryFolder.root, byteArrayOf())
        }
        assertFailure(DeviceSecurityFailure.ASSET_INTEGRITY_FAILED) {
            VerifiedDexAsset.prepare(temporaryFolder.newFile("not-directory"), byteArrayOf(1))
        }
    }

    @Test
    fun `native library must exist and match process bitness`() {
        assertFailure(DeviceSecurityFailure.UNSUPPORTED_ABI) {
            NativeSecurityLibrary.requireCompatible(File(temporaryFolder.root, "missing.so"), true)
        }
        val library = temporaryFolder.newFile("fixture.so")
        library.writeBytes(elfHeader(2))
        NativeSecurityLibrary.requireCompatible(library, true)
        assertFailure(DeviceSecurityFailure.UNSUPPORTED_ABI) {
            NativeSecurityLibrary.requireCompatible(library, false)
        }
        library.writeBytes(elfHeader(1))
        NativeSecurityLibrary.requireCompatible(library, false)
        library.writeBytes(byteArrayOf(1, 2))
        assertFailure(DeviceSecurityFailure.UNSUPPORTED_ABI) {
            NativeSecurityLibrary.requireCompatible(library, true)
        }
    }

    @Test
    fun `nonempty SDK output including D prefix is returned unchanged`() {
        listOf("B-fixture", "D-fixture", "fixture-with-other-prefix").forEach { id ->
            assertEquals(id, DeviceIdCallbackBridge.await { callback -> callback(id) })
        }
    }

    @Test
    fun `empty and throwing callbacks report explicit sanitized failures`() {
        listOf(null, "", "   ").forEach { id ->
            assertFailure(DeviceSecurityFailure.DEVICE_ID_EMPTY) {
                DeviceIdCallbackBridge.await { callback -> callback(id) }
            }
        }
        val error = assertFailure(DeviceSecurityFailure.DEVICE_ID_REQUEST_FAILED) {
            DeviceIdCallbackBridge.await { throw IllegalStateException("private-fixture-data") }
        }
        assertFalse(error.toString().contains("private-fixture-data"))
        assertEquals(null, error.cause)
    }

    @Test
    fun `missing and blocked callbacks time out without using previous success`() {
        assertEquals("previous-fixture", DeviceIdCallbackBridge.await { it("previous-fixture") })
        assertFailure(DeviceSecurityFailure.DEVICE_ID_TIMEOUT) {
            DeviceIdCallbackBridge.await(20L) { }
        }
        val interrupted = CountDownLatch(1)
        assertFailure(DeviceSecurityFailure.DEVICE_ID_TIMEOUT) {
            DeviceIdCallbackBridge.await(100L) {
                try {
                    CountDownLatch(1).await()
                } finally {
                    interrupted.countDown()
                }
            }
        }
        assertTrue(interrupted.await(1, TimeUnit.SECONDS))
        assertEquals("next-fixture", DeviceIdCallbackBridge.await { it("next-fixture") })
    }

    @Test
    fun `callback after timeout cannot satisfy a later request`() {
        val lateCallback = AtomicReference<((String?) -> Unit)?>()
        assertFailure(DeviceSecurityFailure.DEVICE_ID_TIMEOUT) {
            DeviceIdCallbackBridge.await(100L) { lateCallback.set(it) }
        }
        requireNotNull(lateCallback.get()).invoke("late-fixture")
        assertFailure(DeviceSecurityFailure.DEVICE_ID_TIMEOUT) {
            DeviceIdCallbackBridge.await(20L) { }
        }
    }

    @Test
    fun `registration that ignores interruption does not block the next request`() {
        val release = AtomicBoolean()
        val ended = CountDownLatch(1)
        try {
            assertFailure(DeviceSecurityFailure.DEVICE_ID_TIMEOUT) {
                DeviceIdCallbackBridge.await(100L) {
                    try {
                        while (!release.get()) {
                            try {
                                Thread.sleep(10L)
                            } catch (_: InterruptedException) {
                                // Simulate a native registration call that does not honor cancellation.
                            }
                        }
                        it("late-fixture")
                    } finally {
                        ended.countDown()
                    }
                }
            }
            assertEquals("next-fixture", DeviceIdCallbackBridge.await(1000L) { it("next-fixture") })
        } finally {
            release.set(true)
            assertTrue(ended.await(1, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `only the first callback result is accepted`() {
        assertEquals("first-fixture", DeviceIdCallbackBridge.await {
            it("first-fixture")
            it("second-fixture")
        })
    }

    @Test
    fun `interrupting a waiter cancels the request and preserves interruption`() {
        val started = CountDownLatch(1)
        val failure = AtomicReference<DeviceSecurityFailure?>()
        val wasInterrupted = AtomicBoolean()
        val waiter = Thread {
            try {
                DeviceIdCallbackBridge.await { started.countDown() }
            } catch (e: DeviceSecurityException) {
                failure.set(e.failure)
                wasInterrupted.set(Thread.currentThread().isInterrupted)
            }
        }
        waiter.start()
        assertTrue(started.await(1, TimeUnit.SECONDS))
        waiter.interrupt()
        waiter.join(1000L)
        assertFalse(waiter.isAlive)
        assertEquals(DeviceSecurityFailure.DEVICE_ID_CANCELLED, failure.get())
        assertTrue(wasInterrupted.get())
    }

    private fun elfHeader(elfClass: Int): ByteArray = ByteArray(20).apply {
        this[0] = 0x7f
        this[1] = 'E'.code.toByte()
        this[2] = 'L'.code.toByte()
        this[3] = 'F'.code.toByte()
        this[4] = elfClass.toByte()
    }

    private fun assertFailure(expected: DeviceSecurityFailure, action: () -> Unit): DeviceSecurityException {
        val error = assertThrows(DeviceSecurityException::class.java, action)
        assertEquals(expected, error.failure)
        assertEquals(expected.userMessage, error.message)
        return error
    }
}
