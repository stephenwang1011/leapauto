package com.leapauto.app

data class VehicleListRawResponse(
    val statusCode: Int,
    val rawBody: String,
    val authenticationFailed: Boolean = false
) {
    val isHttpSuccessful: Boolean get() = statusCode in 200..299
}
