package com.leapauto.app.trip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothVehicleTripMatcherTest {

    private val targetMac = "A4:C1:38:12:34:56"

    @Test
    fun `priority 1 matches cloud mac address even when name is completely customized`() {
        // 用户把蓝牙名字改成了任意自定义名称（如“大白”、“小零跑”）
        val matched = BluetoothVehicleTripMatcher.isMatchingVehicle(
            deviceAddress = "A4:C1:38:12:34:56",
            deviceName = "大白专属座驾",
            isCarAudioDevice = false,
            targetVinMac = targetMac,
            selectedCarType = "零跑C16"
        )
        assertTrue(matched)

        // 连字符与大小写自适应测试
        val matchedDash = BluetoothVehicleTripMatcher.isMatchingVehicle(
            deviceAddress = "a4-c1-38-12-34-56",
            deviceName = "自定义名称",
            isCarAudioDevice = false,
            targetVinMac = "A4:C1:38:12:34:56",
            selectedCarType = "C11"
        )
        assertTrue(matchedDash)
    }

    @Test
    fun `priority 1 matches shared OUI hardware fingerprint with last octet offset`() {
        // 车载多媒体模组与网关 BLE 芯片处于同一模组，前 5 组 OUI 指纹相同
        val matched = BluetoothVehicleTripMatcher.isMatchingVehicle(
            deviceAddress = "A4:C1:38:12:34:57", // 末位为 57，目标为 56
            deviceName = "车载蓝牙",
            isCarAudioDevice = false,
            targetVinMac = targetMac,
            selectedCarType = "零跑C16"
        )
        assertTrue(matched)
    }

    @Test
    fun `priority 2 matches car audio class with brand or model keywords without cloud mac`() {
        // 无云端 MAC 时的车载多媒体品牌特征识别
        assertTrue(
            BluetoothVehicleTripMatcher.isMatchingVehicle(
                deviceAddress = "11:22:33:44:55:66",
                deviceName = "Leapmotor_BT",
                isCarAudioDevice = true,
                targetVinMac = null,
                selectedCarType = "零跑C16"
            )
        )

        assertTrue(
            BluetoothVehicleTripMatcher.isMatchingVehicle(
                deviceAddress = "11:22:33:44:55:66",
                deviceName = "零跑 C16 车载蓝牙",
                isCarAudioDevice = true,
                targetVinMac = null,
                selectedCarType = "C16"
            )
        )

        // 包含车型关键字
        assertTrue(
            BluetoothVehicleTripMatcher.isMatchingVehicle(
                deviceAddress = "11:22:33:44:55:66",
                deviceName = "C16-Audio",
                isCarAudioDevice = true,
                targetVinMac = null,
                selectedCarType = "零跑C16"
            )
        )
    }

    @Test
    fun `rejects unrelated personal bluetooth accessories`() {
        // 蓝牙耳机、手环等非车载设备
        assertFalse(
            BluetoothVehicleTripMatcher.isMatchingVehicle(
                deviceAddress = "88:99:AA:BB:CC:DD",
                deviceName = "AirPods Pro",
                isCarAudioDevice = false,
                targetVinMac = targetMac,
                selectedCarType = "C16"
            )
        )

        assertFalse(
            BluetoothVehicleTripMatcher.isMatchingVehicle(
                deviceAddress = "88:99:AA:BB:CC:DD",
                deviceName = "Sony WH-1000XM5",
                isCarAudioDevice = false,
                targetVinMac = null,
                selectedCarType = "C16"
            )
        )

        assertFalse(
            BluetoothVehicleTripMatcher.isMatchingVehicle(
                deviceAddress = null,
                deviceName = null,
                isCarAudioDevice = false,
                targetVinMac = targetMac,
                selectedCarType = "C16"
            )
        )
    }
}
