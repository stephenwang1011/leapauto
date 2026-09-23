package com.leapauto.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleLocationGeocoderTest {

    @Test
    fun `formats user example address to residential compound without city`() {
        val json = JSONObject("""
            {
                "formatted_address": "湖北省襄阳市樊城区团山镇吾悦华府A区",
                "addressComponent": {
                    "province": "湖北省",
                    "city": "襄阳市",
                    "district": "樊城区",
                    "township": "团山镇"
                },
                "aois": [
                    {"name": "吾悦华府A区"}
                ]
            }
        """.trimIndent())

        assertEquals("吾悦华府A区", VehicleLocationGeocoder.formatShortAddress(json))
    }

    @Test
    fun `formats address by stripping prefixes when aois are empty`() {
        val json = JSONObject("""
            {
                "formatted_address": "湖北省襄阳市樊城区团山镇枫叶路",
                "addressComponent": {
                    "province": "湖北省",
                    "city": "襄阳市",
                    "district": "樊城区",
                    "township": "团山镇"
                },
                "aois": []
            }
        """.trimIndent())

        assertEquals("枫叶路", VehicleLocationGeocoder.formatShortAddress(json))
    }

    @Test
    fun `formats municipality address correctly without city`() {
        val json = JSONObject("""
            {
                "formatted_address": "北京市海淀区中关村南大街1号",
                "addressComponent": {
                    "province": "北京市",
                    "city": [],
                    "district": "海淀区",
                    "township": "中关村街道"
                },
                "aois": []
            }
        """.trimIndent())

        assertEquals("中关村南大街1号", VehicleLocationGeocoder.formatShortAddress(json))
    }

    @Test
    fun `strips city prefix when specific place starts with city`() {
        val json = JSONObject("""
            {
                "formatted_address": "湖北省襄阳市襄阳火车站",
                "addressComponent": {
                    "province": "湖北省",
                    "city": "襄阳市",
                    "district": "樊城区"
                },
                "aois": [
                    {"name": "襄阳火车站"}
                ]
            }
        """.trimIndent())

        assertEquals("火车站", VehicleLocationGeocoder.formatShortAddress(json))
    }

    @Test
    fun `clears cache safely without throwing`() {
        VehicleLocationGeocoder.clearCache()
    }
}
