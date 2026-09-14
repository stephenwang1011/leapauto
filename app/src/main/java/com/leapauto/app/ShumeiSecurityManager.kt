package com.leapauto.app

import android.content.Context
import android.os.Process
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.Proxy

/**
 * 数美反欺诈安全 SDK 运行时管理器。
 * 加载 APK 内的设备安全组件，指纹是否被接受由登录服务端判断。
 */
object ShumeiSecurityManager {
    private const val TAG = "ShumeiSecurity"
    private const val ORGANIZATION = "mRCScLRggvJAYnjU7WgS"
    private const val APP_ID = "default"
    private const val PUBLIC_KEY =
        "MIIDLzCCAhegAwIBAgIBMDANBgkqhkiG9w0BAQUFADAyMQswCQYDVQQGEwJDTjELMAkGA1UECwwCU00xFjAUBgNVBAMMDWUuaXNodW1laS5jb20wHhcNMjMwMzEzMDcwMDM4WhcNNDMwMzA4MDcwMDM4WjAyMQswCQYDVQQGEwJDTjELMAkGA1UECwwCU00xFjAUBgNVBAMMDWUuaXNodW1laS5jb20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCeaT5VtCQdHxs8vJYrn5v+gcIuHHK46wVD+EmHJoQyfG0po4/TJiqLNTMkmXRkSpnNXQkI1ISa8WJEdx98bEeh+k0PejTeMnMnh/N3pl0RoQwUJdSZ2JGYv7wI2G0T2yaltVLE9SfuJ94CS2jS1sMqR5TZNgDncGdNnTWmU+JBjyHvw4WDukIE+ut6XTmXOFuc2M/sUENGpAmDKydIba8eE+UqczskLeHHZEK3OIBb/rXRceA2Ev0OS3E8+440XNj1rSEM6GDowJstX0g4AHVlXyfIX9Q5CUt8ou2yLM/sV2DoyOZhlO8VOVlROyoEPSJ2zB4PXT5Wh59REeU6fOjXAgMBAAGjUDBOMB0GA1UdDgQWBBRPX/BD6jvfsAAvNtuIQG3MJEAb+jAfBgNVHSMEGDAWgBRPX/BD6jvfsAAvNtuIQG3MJEAb+jAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBQUAA4IBAQAan9/tQSgVH4ecCzqK+0t96mkNt/w14k3QFD0ix5UehTrk3d4v2fJ9Vf4RzWe2MsM6VfXIgRJwNi1msYiz+Z1flsDaVuw0RZGaAbPKkidbsd6nL8EQfL/VANi7P31G6u1/Nk3E8bfAAnFesYogVK0/T/WlXhJo1P9/HXRaKPydoS0u8uNPR4RN0aPeKglOYlzykcmCFDowNsMmq4ktYOtyuyTNQm2HkHYPREbkxr5nnvQwrzlvARnqH9Fu/4ZIQxMSZtQnCVLmQ3FO0eICDw/jAXrCnWAgtgc9kNNonaEnXItp/KQliEPlMvOTNyklffwKQwiCDC5OXTIVBQj5prsR"

    @Volatile
    private var smClass: Class<*>? = null
    private var sdkLoader: ClassLoader? = null
    @Volatile
    private var initializationFailure = DeviceSecurityFailure.NOT_INITIALIZED

    /** 初始化数美 SDK。 */
    @Synchronized
    fun initialize(context: Context): Boolean {
        if (smClass != null) return true
        return try {
            val appCtx = context.applicationContext
            // Native libraries stay bound to their class loader across initialization retries.
            val loader = sdkLoader ?: createClassLoader(appCtx).also { sdkLoader = it }
            smClass = initializeSdk(appCtx, loader)
            Log.i(TAG, "设备安全组件已初始化，登录结果由服务端判断")
            true
        } catch (e: Throwable) {
            initializationFailure = (e as? DeviceSecurityException)?.failure
                ?: DeviceSecurityFailure.SDK_LOAD_FAILED
            Log.w(TAG, "设备安全组件初始化失败: ${initializationFailure.name}")
            false
        }
    }

    private fun createClassLoader(context: Context): DexClassLoader {
        val nativeDirectory = context.applicationInfo.nativeLibraryDir
        NativeSecurityLibrary.requireCompatible(File(nativeDirectory, "libsmsdk.so"), Process.is64Bit())
        val asset = context.assets.open("shumei.dex").use { it.readBytes() }
        val dexFile = VerifiedDexAsset.prepare(File(context.codeCacheDir, "device-security"), asset)
        return DexClassLoader(
            dexFile.absolutePath,
            context.codeCacheDir.absolutePath,
            nativeDirectory,
            context.classLoader,
        )
    }

    private fun initializeSdk(context: Context, loader: ClassLoader): Class<*> {
        val sdkClass = loader.loadClass("com.ishumei.smantifraud.SmAntiFraud")
        val optionClass = loader.loadClass("com.ishumei.smantifraud.SmAntiFraud\$SmOption")
        val option = optionClass.getConstructor().newInstance()
        optionClass.getMethod("setOrganization", String::class.java).invoke(option, ORGANIZATION)
        optionClass.getMethod("setAppId", String::class.java).invoke(option, APP_ID)
        optionClass.getMethod("setPublicKey", String::class.java).invoke(option, PUBLIC_KEY)
        optionClass.getMethod("setUsingHttps", Boolean::class.javaPrimitiveType).invoke(option, true)
        val notCollect = setOf("wifiip", "bssid", "ssid", "oaid", "locationCls")
        optionClass.getMethod("setNotCollect", Set::class.java).invoke(option, notCollect)
        val success = sdkClass.getMethod("create", Context::class.java, optionClass)
            .invoke(null, context, option) as? Boolean ?: false
        if (!success) throw DeviceSecurityException(DeviceSecurityFailure.SDK_INITIALIZATION_FAILED)
        return sdkClass
    }

    fun requireDeviceId(timeoutMs: Long = DeviceIdCallbackBridge.MAX_TIMEOUT_MS): String {
        val sdkClass = smClass ?: throw DeviceSecurityException(initializationFailure)
        return DeviceIdCallbackBridge.await(timeoutMs) { callback ->
            requestDeviceId(sdkClass, callback)
        }
    }

    fun getDeviceId(timeoutMs: Long = DeviceIdCallbackBridge.MAX_TIMEOUT_MS): String? = try {
        requireDeviceId(timeoutMs)
    } catch (_: DeviceSecurityException) {
        null
    }

    private fun requestDeviceId(sdkClass: Class<*>, callback: (String?) -> Unit) {
        val loader = sdkClass.classLoader ?: throw DeviceSecurityException(DeviceSecurityFailure.SDK_LOAD_FAILED)
        val callbackClass = loader.loadClass("com.ishumei.smantifraud.SmAntiFraud\$IDeviceIdCallback")
        val proxy = Proxy.newProxyInstance(loader, arrayOf(callbackClass)) { instance, method, args ->
            when (method.name) {
                "onResult" -> { callback(args?.getOrNull(0) as? String); null }
                "equals" -> instance === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(instance)
                "toString" -> "DeviceSecurityCallback"
                else -> null
            }
        }
        sdkClass.getMethod("getDeviceId", callbackClass).invoke(null, proxy)
    }
}
