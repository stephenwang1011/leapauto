package com.leapauto.app

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class DeviceSecurityFailure(val userMessage: String) {
    NOT_INITIALIZED("设备安全组件尚未初始化，请重新点击登录"),
    UNSUPPORTED_ABI("当前设备缺少兼容的安全组件，请使用支持的 64 位设备"),
    SDK_LOAD_FAILED("设备安全组件加载失败，请重启应用后重试"),
    SDK_INITIALIZATION_FAILED("设备安全组件初始化失败，请稍后重试"),
    ASSET_INTEGRITY_FAILED("设备安全组件文件校验失败，请重新安装应用"),
    ASSET_READ_ONLY_FAILED("设备安全组件文件保护失败，请重启应用后重试"),
    DEVICE_ID_TIMEOUT("设备安全信息获取超时，请检查网络后重试"),
    DEVICE_ID_EMPTY("设备安全组件未返回有效信息，请稍后重试"),
    DEVICE_ID_REQUEST_FAILED("设备安全信息获取失败，请稍后重试"),
    DEVICE_ID_CANCELLED("设备安全信息获取已取消，请重新点击登录"),
}

class DeviceSecurityException(val failure: DeviceSecurityFailure) :
    IllegalStateException(failure.userMessage)

internal object VerifiedDexAsset {
    fun prepare(directory: File, trustedAsset: ByteArray): File {
        if (trustedAsset.isEmpty() || (!directory.isDirectory && !directory.mkdirs())) {
            throw DeviceSecurityException(DeviceSecurityFailure.ASSET_INTEGRITY_FAILED)
        }
        val digest = sha256(trustedAsset)
        val target = File(directory, "shumei-$digest.dex")
        if (target.isFile && sha256(target.readBytes()) == digest) {
            makeReadOnly(target)
            return target
        }
        removeInvalid(target)
        return writeVerified(target, trustedAsset, digest)
    }

    private fun removeInvalid(target: File) {
        if (!target.exists() || target.delete()) return
        // A corrupt file is never reused; Windows needs write permission to remove it.
        if (!target.setWritable(true) || !target.delete()) {
            throw DeviceSecurityException(DeviceSecurityFailure.ASSET_INTEGRITY_FAILED)
        }
    }

    private fun writeVerified(target: File, trustedAsset: ByteArray, digest: String): File {
        val temporary = File.createTempFile("shumei-", ".dex", target.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                // Android 14 requires read-only code before its contents are written.
                makeReadOnly(temporary)
                output.write(trustedAsset)
                output.fd.sync()
            }
            if (sha256(temporary.readBytes()) != digest) {
                throw DeviceSecurityException(DeviceSecurityFailure.ASSET_INTEGRITY_FAILED)
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return target
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun makeReadOnly(file: File) {
        if (!file.setReadOnly()) {
            throw DeviceSecurityException(DeviceSecurityFailure.ASSET_READ_ONLY_FAILED)
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

internal object NativeSecurityLibrary {
    fun requireCompatible(library: File, is64BitProcess: Boolean) {
        val header = ByteArray(20)
        val expectedClass = if (is64BitProcess) 2 else 1
        val valid = library.isFile && library.inputStream().use { input ->
            input.read(header) == header.size &&
                header.take(4) == listOf(0x7f.toByte(), 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()) &&
                header[4].toInt() == expectedClass
        }
        if (!valid) throw DeviceSecurityException(DeviceSecurityFailure.UNSUPPORTED_ABI)
    }
}

internal object DeviceIdCallbackBridge {
    const val MAX_TIMEOUT_MS = 10_000L

    private data class Outcome(val deviceId: String? = null, val failure: DeviceSecurityFailure? = null)

    fun await(timeoutMs: Long = MAX_TIMEOUT_MS, request: ((String?) -> Unit) -> Unit): String {
        if (Thread.currentThread().isInterrupted) {
            throw DeviceSecurityException(DeviceSecurityFailure.DEVICE_ID_CANCELLED)
        }
        val outcome = AtomicReference<Outcome?>()
        val signal = CountDownLatch(1)
        val future = FutureTask<Unit> {
            try {
                request { id ->
                    val result = if (id.isNullOrBlank()) Outcome(failure = DeviceSecurityFailure.DEVICE_ID_EMPTY)
                    else Outcome(deviceId = id)
                    if (outcome.compareAndSet(null, result)) signal.countDown()
                }
            } catch (_: Throwable) {
                if (outcome.compareAndSet(null, Outcome(failure = DeviceSecurityFailure.DEVICE_ID_REQUEST_FAILED))) {
                    signal.countDown()
                }
            }
        }
        Thread(future, "device-security-callback").apply { isDaemon = true }.start()
        var succeeded = false
        return try {
            readOutcome(signal, outcome, timeoutMs.coerceIn(1L, MAX_TIMEOUT_MS)).also { succeeded = true }
        } finally {
            outcome.compareAndSet(null, Outcome(failure = DeviceSecurityFailure.DEVICE_ID_CANCELLED))
            if (!succeeded) future.cancel(true)
        }
    }

    private fun readOutcome(signal: CountDownLatch, outcome: AtomicReference<Outcome?>, timeoutMs: Long): String {
        try {
            if (!signal.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw DeviceSecurityException(DeviceSecurityFailure.DEVICE_ID_TIMEOUT)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw DeviceSecurityException(DeviceSecurityFailure.DEVICE_ID_CANCELLED)
        }
        val result = outcome.get()
        result?.failure?.let { throw DeviceSecurityException(it) }
        return result?.deviceId ?: throw DeviceSecurityException(DeviceSecurityFailure.DEVICE_ID_EMPTY)
    }
}
