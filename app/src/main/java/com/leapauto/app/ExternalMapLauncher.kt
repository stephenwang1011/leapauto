package com.leapauto.app

import android.content.Context
import android.content.Intent
import android.net.Uri

enum class ExternalMapApp(
    val displayName: String,
    internal val packageName: String?
) {
    AMAP("高德地图", "com.autonavi.minimap"),
    BAIDU("百度地图", "com.baidu.BaiduMap"),
    SYSTEM("其他地图", null)
}

object ExternalMapLauncher {
    fun availableApps(context: Context, forNavigation: Boolean): List<ExternalMapApp> {
        val probe = if (forNavigation) {
            navigationIntent(ExternalMapApp.SYSTEM, 39.9, 116.4)
        } else {
            searchIntent(ExternalMapApp.SYSTEM, "地点")
        }
        return ExternalMapApp.entries.filter { app ->
            val intent = when {
                app == ExternalMapApp.SYSTEM -> probe
                forNavigation -> navigationIntent(app, 39.9, 116.4)
                else -> searchIntent(app, "地点")
            }
            context.packageManager.resolveActivity(intent, 0) != null
        }
    }

    fun search(context: Context, app: ExternalMapApp, keyword: String) {
        launch(context, searchIntent(app, keyword.trim()))
    }

    fun navigateToVehicle(
        context: Context,
        app: ExternalMapApp,
        latitude: Double,
        longitude: Double
    ) {
        launch(context, navigationIntent(app, latitude, longitude))
    }

    internal fun searchIntent(app: ExternalMapApp, keyword: String): Intent {
        val encodedKeyword = Uri.encode(keyword)
        val uri = when (app) {
            ExternalMapApp.AMAP ->
                Uri.parse("androidamap://poi?sourceApplication=${Uri.encode(APP_NAME)}&keywords=$encodedKeyword&dev=0")
            ExternalMapApp.BAIDU ->
                Uri.parse("baidumap://map/place/search?query=$encodedKeyword&src=andr.$PACKAGE_NAME")
            ExternalMapApp.SYSTEM -> Uri.parse("geo:0,0?q=$encodedKeyword")
        }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            app.packageName?.let(::setPackage)
        }
    }

    internal fun navigationIntent(
        app: ExternalMapApp,
        latitude: Double,
        longitude: Double
    ): Intent {
        val coordinate = "$latitude,$longitude"
        val encodedName = Uri.encode("车辆位置")
        val uri = when (app) {
            ExternalMapApp.AMAP -> Uri.parse(
                "androidamap://navi?sourceApplication=${Uri.encode(APP_NAME)}" +
                    "&poiname=$encodedName&lat=$latitude&lon=$longitude&dev=0&style=2"
            )
            ExternalMapApp.BAIDU -> Uri.parse(
                "baidumap://map/direction?destination=latlng:$coordinate|name:$encodedName" +
                    "&mode=driving&coord_type=gcj02&src=andr.$PACKAGE_NAME"
            )
            ExternalMapApp.SYSTEM -> Uri.parse("geo:$coordinate?q=$coordinate($encodedName)")
        }
        return Intent(Intent.ACTION_VIEW, uri).apply {
            app.packageName?.let(::setPackage)
        }
    }

    private fun launch(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private const val APP_NAME = "零跑智控"
    private const val PACKAGE_NAME = "com.leapauto.app"
}
