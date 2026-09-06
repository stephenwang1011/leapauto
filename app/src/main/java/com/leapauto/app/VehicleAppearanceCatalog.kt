package com.leapauto.app

import androidx.annotation.DrawableRes

data class VehicleColorOption(
    val id: String,
    val name: String,
    val swatchArgb: Long
)

data class VehicleAppearance(
    val model: String,
    val color: VehicleColorOption,
    @DrawableRes val imageResource: Int
)

/**
 * Keeps historical color identifiers readable while every model shares one local image.
 * Color is legacy display data and no longer changes the rendered vehicle artwork.
 */
object VehicleAppearanceCatalog {
    const val DEFAULT_COLOR_ID = "liquid_silver"

    private fun color(id: String, name: String, swatchArgb: Long) =
        VehicleColorOption(id, name, swatchArgb)

    private val default = color("default", "默认", 0xFFCBD5E1)
    private val photoelectricWhite = color("photoelectric_white", "光电白", 0xFFF5F5F0)
    private val metalBlack = color("metal_black", "金属黑", 0xFF25282D)
    private val seaweedGreen = color("seaweed_green", "海苔绿", 0xFF556A5A)
    private val berryBlue = color("berry_blue", "莓莓蓝", 0xFF365B91)
    private val morganPink = color("morgan_pink", "摩根粉", 0xFFC596A3)
    private val tundraGray = color("tundra_gray", "苔原灰", 0xFF777B7C)
    private val acornBrown = color("acorn_brown", "橡果棕", 0xFF745446)
    private val galaxySilver = color("galaxy_silver", "星河银", 0xFFB9BEC1)
    private val starryNightBlue = color("starry_night_blue", "星夜蓝", 0xFF1F3150)
    private val liquidSilver = color(DEFAULT_COLOR_ID, "液态银", 0xFFC7CBD0)
    private val cloudyPurple = color("cloudy_purple", "云黛紫", 0xFF776B8E)
    private val windWheatGreen = color("wind_wheat_green", "风禾青", 0xFF6B8E83)
    private val dawnPurple = color("dawn_purple", "曦露紫", 0xFF846B8D)
    private val tianmuGray = color("tianmu_gray", "天幕灰", 0xFF626970)
    private val cloudGold = color("cloud_gold", "云栖金", 0xFFB79B72)
    private val pineMistGray = color("pine_mist_gray", "松岚灰", 0xFF667069)
    private val rimeBeige = color("rime_beige", "雾凇米", 0xFFD3C6B2)
    private val starlightPurple = color("starlight_purple", "星煜紫", 0xFF765D8B)
    private val pineValleyGreen = color("pine_valley_green", "松谷绿", 0xFF3E6655)
    private val mirrorBlue = color("mirror_blue", "辰镜蓝", 0xFF476B96)
    private val electricYellow = color("electric_yellow", "电掣黄", 0xFFF0C438)
    private val speedOrange = color("speed_orange", "风驰橙", 0xFFE87B32)
    private val celadonGreen = color("celadon_green", "豆青", 0xFF9DB6A3)
    private val machoPink = color("macho_pink", "猛男粉", 0xFFE5A3B2)
    private val lightWaterBlue = color("light_water_blue", "浅水蓝", 0xFF8FC9D8)
    private val ruyaoWhite = color("ruyao_white", "汝窑白", 0xFFE9E6DE)
    private val deepSpaceBlue = color("deep_space_blue", "深空蓝", 0xFF263A5B)
    private val deepGray = color("deep_gray", "深遂灰", 0xFF4C535A)
    private val snowfieldWhite = color("snowfield_white", "雪域白", 0xFFF7F8F5)
    private val metalBlackAddCrown = color("metal_black_add_crown", "金属黑+银辉之冠", 0xFF313338)
    private val pineValleyGreenAddCrown = color("pine_valley_green_add_crown", "松谷绿+银辉之冠", 0xFF4C725E)
    private val tianmuGrayAddCrown = color("tianmu_gray_add_crown", "天幕灰+银辉之冠", 0xFF70777E)

    private val colorsByModel = mapOf(
        "A05" to listOf(metalBlack),
        "A10" to listOf(seaweedGreen, berryBlue, morganPink, tundraGray, acornBrown, galaxySilver),
        "B01" to listOf(photoelectricWhite, metalBlack, morganPink, tundraGray, galaxySilver, starryNightBlue, liquidSilver, cloudyPurple),
        "B10" to listOf(windWheatGreen, photoelectricWhite, metalBlack, morganPink, tundraGray, dawnPurple, galaxySilver, liquidSilver),
        "C01" to listOf(default),
        "C10" to listOf(photoelectricWhite, metalBlack, tundraGray, tianmuGray, dawnPurple, cloudGold),
        "C11" to listOf(photoelectricWhite, metalBlack, pineMistGray, galaxySilver, tianmuGray, cloudGold, rimeBeige),
        "C16" to listOf(photoelectricWhite, metalBlack, galaxySilver, tianmuGray, cloudGold, starlightPurple),
        "D19" to listOf(photoelectricWhite, metalBlack, metalBlackAddCrown, liquidSilver, pineValleyGreen, pineValleyGreenAddCrown, tianmuGray, tianmuGrayAddCrown),
        "D99" to listOf(photoelectricWhite, metalBlack, mirrorBlue, liquidSilver, tianmuGray),
        "Lafa5" to listOf(electricYellow, speedOrange, metalBlack, morganPink, galaxySilver, starryNightBlue, liquidSilver),
        "T03" to listOf(celadonGreen, machoPink, lightWaterBlue, ruyaoWhite, deepSpaceBlue, deepGray, tianmuGray, snowfieldWhite)
    )
    private val allColors = colorsByModel.values.flatten().associateBy { it.id } + (default.id to default)

    fun availableColors(model: String?): List<VehicleColorOption> =
        colorsByModel[canonicalModel(model)] ?: listOf(liquidSilver)

    fun defaultColor(model: String?): String = DEFAULT_COLOR_ID

    fun colorOption(model: String?, colorId: String?): VehicleColorOption =
        normalizeColor(model, colorId)?.let(allColors::get) ?: liquidSilver

    fun normalizeColor(model: String?, colorId: String?): String? =
        colorId?.trim()?.takeIf { it in allColors }

    fun reconciledColor(model: String?, colorId: String?): String =
        colorId?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_COLOR_ID

    fun isValidColor(model: String?, colorId: String?): Boolean = !colorId.isNullOrBlank()

    fun resolveAppearance(model: String?, colorId: String?): VehicleAppearance = VehicleAppearance(
        model = canonicalModel(model),
        color = colorOption(model, colorId),
        imageResource = R.drawable.vehicle_lafa5_liquid_silver
    )

    fun canonicalModel(model: String?): String {
        val normalized = VehicleConfigConfirmationPolicy.normalizeModel(model.orEmpty())
        return when (normalized) {
            "B05", "Lafa5" -> "Lafa5"
            else -> normalized?.takeIf { it in colorsByModel } ?: "Lafa5"
        }
    }
}
