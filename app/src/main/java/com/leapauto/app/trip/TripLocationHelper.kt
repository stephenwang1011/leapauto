package com.leapauto.app.trip

import com.leapauto.app.VehicleLocationDomain
import com.leapauto.app.VehicleLocationGeocoder
import com.leapauto.app.VehicleLocationValidation
import org.json.JSONObject

object TripLocationHelper {
    /**
     * 从车辆遥测 JSON 中提取有效坐标并逆地理编码为短地标。
     * 若处于地下车库无卫星信号或坐标无效，返回空字符串。
     */
    fun resolveAddressFromStatus(status: JSONObject?): String {
        if (status == null || status.length() == 0) return ""
        val map = mutableMapOf<String, Any?>()
        val keys = status.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = status.opt(k)
        }
        val validation = VehicleLocationDomain.validate(map)
        if (validation is VehicleLocationValidation.Valid) {
            val geocoded = VehicleLocationGeocoder.reverseGeocode(
                latitude = validation.location.latitude,
                longitude = validation.location.longitude
            )
            return geocoded?.shortAddress.orEmpty()
        }
        return ""
    }
}
