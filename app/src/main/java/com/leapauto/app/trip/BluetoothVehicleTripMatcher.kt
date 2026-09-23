package com.leapauto.app.trip

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import java.util.Locale

/**
 * 车载蓝牙智能匹配器：
 * 采用全自动两级识别体系，零手动配置，开箱即用：
 * 1. 第一优先级（云端 MAC 精准匹配）：根据 VIN 对应的车载蓝牙硬件 MAC 地址比对，改名也绝不失效；
 * 2. 第二优先级（车载音频系统特征 + 名称匹配）：识别系统车载音频（CAR_AUDIO）类型并匹配零跑或车型关键字。
 */
object BluetoothVehicleTripMatcher {

    /**
     * 判定连接/断开的蓝牙设备是否属于当前车辆
     *
     * @param device 触发连接事件的蓝牙设备
     * @param targetVinMac 从零跑云端配置接口获取到的该 VIN 对应蓝牙 MAC 地址
     * @param selectedCarType 当前车主选择的车型名称（如 "零跑C16", "C11" 等）
     */
    fun isMatchingVehicle(
        device: BluetoothDevice,
        targetVinMac: String?,
        selectedCarType: String?
    ): Boolean {
        val address = runCatching { device.address }.getOrNull()
        val name = runCatching { device.name }.getOrNull()
        val isCarAudio = isCarAudioClass(device)
        return isMatchingVehicle(address, name, isCarAudio, targetVinMac, selectedCarType)
    }

    /** 纯逻辑两级匹配算法（支持单元测试与业务逻辑完全复用） */
    fun isMatchingVehicle(
        deviceAddress: String?,
        deviceName: String?,
        isCarAudioDevice: Boolean,
        targetVinMac: String?,
        selectedCarType: String?
    ): Boolean {
        val address = deviceAddress?.uppercase(Locale.ROOT)?.replace("-", ":")

        // 1. 第一优先级：云端 MAC 精准匹配 (硬件级身份证匹配，忽略任何车机大屏名称修改)
        if (!targetVinMac.isNullOrBlank() && !address.isNullOrBlank()) {
            val normalizedTarget = targetVinMac.uppercase(Locale.ROOT).replace("-", ":")
            if (address == normalizedTarget) return true

            // 兼容车机多媒体蓝牙模组与 BLE 网关末位差 1（前 5 组 OUI 厂商硬件指纹完全相同）
            val targetPrefix = normalizedTarget.substringBeforeLast(":")
            val devicePrefix = address.substringBeforeLast(":")
            if (targetPrefix.isNotBlank() && targetPrefix == devicePrefix) {
                return true
            }
        }

        // 2. 第二优先级：车载音频类型特征 + 品牌/车型关键字自动识别
        val name = deviceName?.trim().orEmpty()
        if (name.isBlank()) return false
        val lowerName = name.lowercase(Locale.ROOT)

        val isLeapBrand = lowerName.contains("leapmotor") || lowerName.contains("零跑")

        val modelKeyword = extractModelKeyword(selectedCarType)
        val matchesModel = modelKeyword != null && lowerName.contains(modelKeyword)

        // 若明确声明为车载音频设备且带有品牌/车型关键字，或名称明确包含零跑品牌
        return (isCarAudioDevice && (isLeapBrand || matchesModel)) || isLeapBrand
    }

    /** 是否属于车载音频系统（车载多媒体或车载免提） */
    fun isCarAudioClass(device: BluetoothDevice): Boolean {
        return runCatching {
            val btClass = device.bluetoothClass ?: return@runCatching false
            val major = btClass.majorDeviceClass
            val deviceClass = btClass.deviceClass
            major == BluetoothClass.Device.Major.AUDIO_VIDEO &&
                (deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                 deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE)
        }.getOrDefault(false)
    }

    private fun extractModelKeyword(carType: String?): String? {
        if (carType.isNullOrBlank()) return null
        val lower = carType.trim().lowercase(Locale.ROOT)
        return when {
            lower.contains("c16") -> "c16"
            lower.contains("c11") -> "c11"
            lower.contains("c10") -> "c10"
            lower.contains("c01") -> "c01"
            lower.contains("t03") -> "t03"
            lower.contains("b10") -> "b10"
            else -> lower.removePrefix("零跑").trim().takeIf { it.isNotBlank() }
        }
    }
}
