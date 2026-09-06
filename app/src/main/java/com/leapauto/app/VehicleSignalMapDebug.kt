package com.leapauto.app

import org.json.JSONObject

sealed interface VehicleSignalMapDebugState {
    data object Idle : VehicleSignalMapDebugState
    data object Loading : VehicleSignalMapDebugState
    data class Success(val formattedJson: String, val signalCount: Int) : VehicleSignalMapDebugState
    data class Failed(val message: String) : VehicleSignalMapDebugState
}

object VehicleSignalMapDebugFormatter {
    fun format(signalMap: JSONObject): String {
        require(signalMap.length() > 0) { "signalMap 不能为空" }
        return signalMap.toString(2)
    }
}
