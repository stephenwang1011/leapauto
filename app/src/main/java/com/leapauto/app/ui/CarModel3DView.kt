package com.leapauto.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.leapauto.app.CarModel3DManager
import com.leapauto.app.CompactControlWidget
import com.leapauto.app.ControlWidget
import com.leapauto.app.TrunkState
import com.leapauto.app.VehicleImageCache
import com.leapauto.app.VehicleStatus
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

private const val TAG = "CarModel3DView"
private const val ASSETS_SCHEME = "https"
private const val ASSETS_HOST = "appassets.androidplatform.net"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CarModel3DView(
    h5Key: String,
    modelParam: JSONObject?,
    status: VehicleStatus? = null,
    vin: String = "",
    isCruising: Boolean = false,
    isDark: Boolean = true,
    modifier: Modifier = Modifier,
    onReady: () -> Unit = {},
    onError: () -> Unit = {},
    onCarClick: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val modelDir = remember(h5Key) {
        CarModel3DManager.getModelDir(context, h5Key)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val actualModelDir = CarModel3DManager.getModelDir(ctx, h5Key)
            CarModelWebView(ctx, actualModelDir, h5Key, modelParam, status, vin, isCruising, isDark, onReady, onError, onCarClick)
        },
        update = { webView ->
            webView.onCarClick = onCarClick
            webView.updateVehicleState(status, cruising = isCruising, isDark = isDark)
        }
    )
}

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class CarModelWebView(
    context: Context,
    private val modelDir: File,
    private val modelKey: String,
    private val modelParam: JSONObject?,
    private var lastStatus: VehicleStatus?,
    private val vin: String,
    private var lastCruising: Boolean = false,
    private var lastDark: Boolean = true,
    private val onReady: () -> Unit,
    private val onError: () -> Unit,
    var onCarClick: (() -> Unit)? = null
) : WebView(context) {

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var touchStartTime = 0L
    private var isDragging = false
    private var isSceneReady = false
    private var snapshotSaved = false
    private var hasAppliedInitialState = false

    init {
        setWebContentsDebuggingEnabled(true)
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

        addJavascriptInterface(LeapNativeBridge { dataUrl ->
            val base64 = dataUrl.removePrefix("data:image/png;base64,")
            handle3DSnapshot(base64)
        }, "LeapNative")

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                if (url.scheme != ASSETS_SCHEME || url.host != ASSETS_HOST) {
                    return null
                }
                val rawPath = url.path?.removePrefix("/") ?: return null
                val decodedPath = try { Uri.decode(rawPath) } catch (_: Exception) { rawPath }
                val targetFile = File(modelDir, decodedPath).canonicalFile
                val file = if (targetFile.exists() && targetFile.isFile) {
                    targetFile
                } else {
                    File(modelDir, rawPath).canonicalFile
                }
                if (!file.path.startsWith(modelDir.canonicalPath + File.separator) || !file.isFile) {
                    return WebResourceResponse(
                        "text/plain", "UTF-8", 404, "Not Found",
                        null, ByteArrayInputStream(ByteArray(0))
                    )
                }
                val checkPath = decodedPath.lowercase()
                if (checkPath.endsWith("index.html")) {
                    return try {
                        val originalHtml = file.readText(Charsets.UTF_8)
                        val mtkHookScript = """
                            <script>
                            (function() {
                                if (window.__mtkWebglHooked) return;
                                window.__mtkWebglHooked = true;
                                var orig = HTMLCanvasElement.prototype.getContext;
                                HTMLCanvasElement.prototype.getContext = function(type, attributes) {
                                    if (type === 'webgl' || type === 'experimental-webgl' || type === 'webgl2') {
                                        attributes = attributes || {};
                                        attributes.preserveDrawingBuffer = true;
                                        attributes.failIfMajorPerformanceCaveat = false;
                                        attributes.powerPreference = 'high-performance';
                                    }
                                    return orig.call(this, type, attributes);
                                };
                            })();
                            </script>
                        """.trimIndent()
                        val patchedHtml = if (originalHtml.contains("<head>", ignoreCase = true)) {
                            originalHtml.replaceFirst("<head>", "<head>\n$mtkHookScript", ignoreCase = true)
                        } else {
                            "$mtkHookScript\n$originalHtml"
                        }
                        val bytes = patchedHtml.toByteArray(Charsets.UTF_8)
                        WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(bytes))
                    } catch (e: Exception) {
                        Log.w(TAG, "动态注入 index.html WebGL hook 异常，回退原始流", e)
                        try {
                            WebResourceResponse("text/html", "UTF-8", FileInputStream(file))
                        } catch (_: Exception) { null }
                    }
                }
                if (checkPath.endsWith("index.js")) {
                    return try {
                        val originalJs = file.readText(Charsets.UTF_8)
                        val errIdx = originalJs.indexOf("加载车模型颜色信息错误")
                        if (errIdx != -1) {
                            val start = (errIdx - 300).coerceAtLeast(0)
                            val end = (errIdx + 200).coerceAtMost(originalJs.length)
                            Log.i("CarModelAssetDump", "Color snippet: " + originalJs.substring(start, end))
                        }
                        val filesList = modelDir.walkTopDown().maxDepth(2).map { it.name }.take(50).toList()
                        Log.i("CarModelAssetDump", "Files in modelDir: $filesList")
                        val mtkPrefix = """
                            (function(){
                                if (!window.__mtkWebglHooked) {
                                    window.__mtkWebglHooked = true;
                                    var orig = HTMLCanvasElement.prototype.getContext;
                                    HTMLCanvasElement.prototype.getContext = function(t, a) {
                                        if (t === 'webgl' || t === 'experimental-webgl' || t === 'webgl2') {
                                            a = a || {};
                                            a.preserveDrawingBuffer = true;
                                            a.failIfMajorPerformanceCaveat = false;
                                            a.powerPreference = 'high-performance';
                                        }
                                        return orig.call(this, t, a);
                                    };
                                }
                            })();
                        """.trimIndent()
                        val patchedJs = mtkPrefix + "\n" + originalJs
                            .replace(
                                "if(e.name===t)return e;",
                                "if(e.name&&(e.name===t||e.name.toLowerCase().replaceAll(\".\",\"\").replaceAll(\" \",\"\")===t))return e;"
                            )
                            .replace(
                                "this.uniforms.uSpace.value+=t*this.speed",
                                "this.uniforms.uSpace.value+=t*this.speed*0.22"
                            )
                            .replace(
                                "float outAlpha      = mix(alpha_mix, alpha_mix * alpha_base, useDeslove) * alpha * 0.5;\n                vec3  outClr        = vec3(1.0) * intensity * color;\n                // vec3 outClr         = mix(loopCol, staticCol, loop).rgb * intensity * color;\n\n                gl_FragColor = vec4(outClr, outAlpha);",
                                "float pulse = pow(sin((vUV.y * 3.5 - uSpace * 0.45) * 3.14159) * 0.5 + 0.5, 2.5);\n                float fadeEnds = smoothstep(0.01, 0.18, vUV.y) * smoothstep(0.99, 0.72, vUV.y);\n                float outAlpha = (alpha_mix * 0.45 + pulse * 0.55) * fadeEnds * alpha * 0.90;\n                vec3  outClr = color * (0.85 + pulse * 0.95);\n                gl_FragColor = vec4(outClr, outAlpha);"
                            )
                        val bytes = patchedJs.toByteArray(Charsets.UTF_8)
                        WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(bytes))
                    } catch (e: Exception) {
                        Log.w(TAG, "动态修复 index.js 异常，回退原始流", e)
                        try {
                            WebResourceResponse("application/javascript", "UTF-8", FileInputStream(file))
                        } catch (_: Exception) { null }
                    }
                }
                val mimeType = when {
                    checkPath.endsWith(".html") -> "text/html"
                    checkPath.endsWith(".js") -> "application/javascript"
                    checkPath.endsWith(".json") -> "application/json"
                    checkPath.endsWith(".css") -> "text/css"
                    checkPath.endsWith(".png") -> "image/png"
                    checkPath.endsWith(".jpg") || checkPath.endsWith(".jpeg") -> "image/jpeg"
                    checkPath.endsWith(".webp") -> "image/webp"
                    checkPath.endsWith(".gltf") -> "model/gltf+json"
                    checkPath.endsWith(".bin") -> "application/octet-stream"
                    checkPath.endsWith(".csv") -> "text/csv"
                    checkPath.endsWith(".fbx") -> "application/octet-stream"
                    else -> "application/octet-stream"
                }
                return try {
                    WebResourceResponse(mimeType, "UTF-8", FileInputStream(file))
                } catch (e: Exception) {
                    null
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectInitScript(view)
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                Log.d(TAG, "3D JS [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                return true
            }

            override fun onJsPrompt(
                view: WebView?,
                url: String?,
                message: String?,
                defaultValue: String?,
                result: JsPromptResult?
            ): Boolean {
                result?.confirm()
                when (message) {
                    "onFirstFrame" -> {
                        Log.i(TAG, "3D 首帧就绪: $message")
                        if (!isSceneReady) {
                            isSceneReady = true
                            onReady()
                            tuneViewer(view)
                            view?.postDelayed({
                                lastStatus?.let { updateVehicleState(it, forceImmediately = true) }
                            }, 500)
                            // 兜底防线：若极少数特殊老车型资产未发送 onFullCarLoaded，延时 3.5 秒安全截取
                            view?.postDelayed({
                                capture3DSnapshot(view)
                            }, 3500)
                        }
                    }
                    "onFullCarLoaded" -> {
                        Log.i(TAG, "3D 全车模型就绪: $message")
                        isSceneReady = true
                        onReady()
                        tuneViewer(view)
                        view?.postDelayed({ tuneViewer(view) }, 200)
                        view?.postDelayed({
                            tuneViewer(view)
                            lastStatus?.let { updateVehicleState(it, forceImmediately = true) }
                            capture3DSnapshot(view)
                        }, 400)
                    }
                    "on3DSnapshot" -> {
                        val dataUrl = defaultValue.orEmpty()
                        Log.i(TAG, "onJsPrompt on3DSnapshot received, length=${dataUrl.length}")
                        if (dataUrl.startsWith("data:image/png;base64,")) {
                            val base64 = dataUrl.removePrefix("data:image/png;base64,")
                            handle3DSnapshot(base64)
                        }
                    }
                    "onInitThrow", "onInitTimeout" -> {
                        Log.w(TAG, "3D 车模初始化失败: $message")
                        onError()
                    }
                }
                return true
            }
        }

        loadUrl("https://$ASSETS_HOST/index.html")
    }

    fun updateVehicleState(status: VehicleStatus?, forceImmediately: Boolean = false, cruising: Boolean = lastCruising, isDark: Boolean = lastDark) {
        lastStatus = status
        lastCruising = cruising
        lastDark = isDark
        if (!isSceneReady) return
        val immediately = forceImmediately || !hasAppliedInitialState
        hasAppliedInitialState = true
        val stateJson = buildStateJson(status, immediately, cruising, isDark)
        injectStateScript(stateJson)
    }

    internal fun buildStateJson(status: VehicleStatus?, immediately: Boolean = false, cruising: Boolean = false, isDark: Boolean = true): String {
        val obj = JSONObject()
        val doorFl = if (status?.driverDoorOpen == true) 1 else 0
        val doorFr = if (status?.passengerDoorOpen == true) 1 else 0
        val doorRl = if (status?.leftRearDoorOpen == true) 1 else 0
        val doorRr = if (status?.rightRearDoorOpen == true) 1 else 0
        val trunk = if (status?.trunkState == TrunkState.OPEN) 1 else 0

        val gear = status?.gearStatus?.trim()?.uppercase()
        val isDrivingGear = gear in setOf("D", "D挡", "DRIVE", "前进", "3", "R", "R挡", "REVERSE", "倒车", "1")
        val parsedSpeed = status?.speed.orEmpty().replace("km/h", "", ignoreCase = true).trim().toFloatOrNull() ?: 0f
        val effectiveSpeed = when {
            isDrivingGear && parsedSpeed <= 0f -> 0f
            isDrivingGear -> parsedSpeed
            cruising -> if (parsedSpeed > 0f) parsedSpeed else 60f
            status?.isDriving == true && parsedSpeed <= 0f -> 0f
            status?.isDriving == true -> parsedSpeed
            else -> parsedSpeed
        }
        obj.put("speed", effectiveSpeed)
        obj.put("isDriving", effectiveSpeed > 0f)
        obj.put("isDark", isDark)

        // 真实开合比例百分比 (0..100)
        val resolveWindowPercent = { explicitPercent: Int?, label: String ->
            if (explicitPercent != null && explicitPercent > 0) {
                when (explicitPercent) {
                    in 1..3 -> 15   // 0~10 刻度下的通风微开 (如 2 对应 15% 微开开度)
                    in 4..6 -> 50   // 0~10 刻度下的半开 (如 5 对应 50% 半开开度)
                    in 7..10 -> 100 // 0~10 刻度下的全开 (如 10 对应 100% 全开)
                    else -> explicitPercent.coerceIn(0, 100)
                }
            } else if (status?.openWindows?.contains(label) == true) {
                15 // 仅有开窗标签状态但无具体开度时，默认以通风微开(15%)呈现，消除半开误判
            } else {
                0
            }
        }
        val winFl = resolveWindowPercent(status?.leftFrontWindowPercent, "左前")
        val winFr = resolveWindowPercent(status?.rightFrontWindowPercent, "右前")
        val winRl = resolveWindowPercent(status?.leftRearWindowPercent, "左后")
        val winRr = resolveWindowPercent(status?.rightRearWindowPercent, "右后")

        obj.put("immediately", immediately)
        obj.put("door_fl", doorFl)
        obj.put("door_fr", doorFr)
        obj.put("door_rl", doorRl)
        obj.put("door_rr", doorRr)
        obj.put("trunk", trunk)

        val doors = JSONObject().apply {
            put("fl", doorFl)
            put("fr", doorFr)
            put("rl", doorRl)
            put("rr", doorRr)
            put("trunk", trunk)
            put("driverDoor", doorFl)
            put("passengerDoor", doorFr)
            put("leftRearDoor", doorRl)
            put("rightRearDoor", doorRr)
        }
        obj.put("doors", doors)

        val windows = JSONObject().apply {
            put("fl", winFl)
            put("fr", winFr)
            put("rl", winRl)
            put("rr", winRr)
            put("leftFront", winFl)
            put("rightFront", winFr)
            put("leftRear", winRl)
            put("rightRear", winRr)
            put("leftFrontWindowPercent", winFl)
            put("rightFrontWindowPercent", winFr)
            put("leftRearWindowPercent", winRl)
            put("rightRearWindowPercent", winRr)
        }
        obj.put("windows", windows)

        obj.put("win_fl", winFl)
        obj.put("win_fr", winFr)
        obj.put("win_rl", winRl)
        obj.put("win_rr", winRr)

        obj.put("leftFrontWindowPercent", winFl)
        obj.put("rightFrontWindowPercent", winFr)
        obj.put("leftRearWindowPercent", winRl)
        obj.put("rightRearWindowPercent", winRr)
        obj.put("3727", winFl)
        obj.put("3728", winFr)
        obj.put("1879", winRl)
        obj.put("1880", winRr)

        obj.put("lbcmDriverDoorStatus", doorFl)
        obj.put("rbcmDriverDoorStatus", doorFr)
        obj.put("lbcmLeftRearDoorStatus", doorRl)
        obj.put("rbcmRightRearDoorStatus", doorRr)
        obj.put("bbcmBackDoorStatus", trunk)
        obj.put("driverDoorLockStatus", if (status?.locked == true) 1 else 0)
        obj.put("1277", doorFl)
        obj.put("1278", doorFr)
        obj.put("1279", doorRl)
        obj.put("1280", doorRr)
        obj.put("1281", trunk)
        obj.put("1298", if (status?.locked == true) 1 else 0)
        obj.put("chargeState", status?.chargeState ?: 0)
        obj.put("1149", status?.chargeState ?: 0)

        // 1. 充电状态与流光 (1=充电中, 2=充电完成/连接中, 或枪已连接)
        val isCharging = status?.chargeState == 1 || status?.chargeState == 2 || status?.chargeGunConnected == true
        obj.put("isCharging", isCharging)

        // 2. 车窗除雾水汽消融 (前挡除霜开启)
        val isDemist = status?.windshieldDefrost == true
        obj.put("isDemist", isDemist)

        // 3. 后视镜与后窗微晶发热暖光
        val isMirrorHeating = status?.rearviewMirrorHeating == true || status?.rearWindowHeating == true
        obj.put("isMirrorHeating", isMirrorHeating)

        // 4. 四轮胎压告警检测
        val tpLfAlert = status?.tires?.find { it.position == "左前" }?.warning == true
        val tpRfAlert = status?.tires?.find { it.position == "右前" }?.warning == true
        val tpLrAlert = status?.tires?.find { it.position == "左后" }?.warning == true
        val tpRrAlert = status?.tires?.find { it.position == "右后" }?.warning == true
        obj.put("tp_lf_alert", tpLfAlert)
        obj.put("tp_rf_alert", tpRfAlert)
        obj.put("tp_lr_alert", tpLrAlert)
        obj.put("tp_rr_alert", tpRrAlert)

        // 5. 全功能车灯联动 (行车开大灯/日行灯、暗夜展车点亮、刹车亮高位红光)
        val isDrivingNow = effectiveSpeed > 0f || (status?.isDriving == true && !(isDrivingGear && parsedSpeed <= 0f))
        // 仅在挂 D/R 挡且速度为 0 时 (等待红绿灯/踩刹车 AutoHold) 触发高位刹车灯红光；挂 P 挡驻车绝不常亮刹车灯
        val isBraking = isDrivingGear && parsedSpeed <= 0f
        // 日间行车灯 (星环日行灯带/贯穿式前后示宽灯)：行车中必亮、通电未休眠必亮、暗夜模式点亮以勾勒车身
        val shouldMarkLight = isDark || isDrivingNow || isDrivingGear || (status?.locked == false && status?.isShutDown == false)
        // 近光透镜大灯：暗夜模式下点亮（照亮夜空展台）、行车中点亮、通电/解锁点亮
        val shouldLowBeam = isDark || isDrivingNow || (status?.locked == false && status?.isShutDown == false)

        obj.put("lowBeam", shouldLowBeam)
        obj.put("stopLight", isBraking)
        obj.put("markLight", shouldMarkLight)

        // 6. 前舱盖 (引擎盖/前备箱) 状态
        obj.put("bonnet", 0)
        return obj.toString()
    }

    private fun injectStateScript(stateJsonStr: String) {
        val script = """
        (function(state) {
            var tries = 0;
            function apply() {
                var c = window.car || (window.viewer && window.viewer.car);
                if (!c) {
                    if (++tries < 30) {
                        setTimeout(apply, 200);
                    }
                    return;
                }

                try {
                    var imm = !!state.immediately;
                    // 1. 锁定视角动画，防止开关门或升降车窗时相机镜头乱飞破坏黄金视角
                    c.__lockViewAnim__ = true;

                    // 2. 车窗精确开合比例 (0.0 ~ 1.0)
                    var w = state.windows || {};
                    var rFl = Math.max(0, Math.min(100, Number(w.fl !== undefined ? w.fl : state.win_fl) || 0)) / 100.0;
                    var rFr = Math.max(0, Math.min(100, Number(w.fr !== undefined ? w.fr : state.win_fr) || 0)) / 100.0;
                    var rRl = Math.max(0, Math.min(100, Number(w.rl !== undefined ? w.rl : state.win_rl) || 0)) / 100.0;
                    var rRr = Math.max(0, Math.min(100, Number(w.rr !== undefined ? w.rr : state.win_rr) || 0)) / 100.0;

                    var doorFlOpen = state.door_fl > 0;
                    var doorFrOpen = state.door_fr > 0;
                    var doorRlOpen = state.door_rl > 0;
                    var doorRrOpen = state.door_rr > 0;
                    var trunkOpen = state.trunk > 0;

                    // 3. 唤醒 WebGL 高帧率渲染动力调度 (3.5 秒 60 FPS，覆盖车门 0.5s 与车窗 3.0s 全过程)
                    var v = window.viewer || (c && c.viewer);
                    if (v && typeof v.setFPS === 'function') {
                        v.setFPS(60, 3500, 'car_door_window_anim');
                    }

                    // 4. 优先通过官方控制器 window.controller 与车模实例原生动效方法双重驱动
                    if (typeof window.controller === 'function') {
                        try {
                            window.controller([
                                {
                                    type: 'Door',
                                    command: [
                                        { type: imm ? 'LeftFront_DoorImmediately' : 'LeftFront_Door', open: doorFlOpen },
                                        { type: imm ? 'RightFront_DoorImmediately' : 'RightFront_Door', open: doorFrOpen },
                                        { type: imm ? 'LeftRear_DoorImmediately' : 'LeftRear_Door', open: doorRlOpen },
                                        { type: imm ? 'RightRear_DoorImmediately' : 'RightRear_Door', open: doorRrOpen },
                                        { type: imm ? 'TrunkImmediately' : 'Trunk', open: trunkOpen }
                                    ]
                                },
                                {
                                    type: 'Window',
                                    command: [
                                        { type: imm ? 'LeftFront_WindowImmediately' : 'LeftFront_Window', open: rFl },
                                        { type: imm ? 'RightFront_WindowImmediately' : 'RightFront_Window', open: rFr },
                                        { type: imm ? 'LeftRear_WindowImmediately' : 'LeftRear_Window', open: rRl },
                                        { type: imm ? 'RightRear_WindowImmediately' : 'RightRear_Window', open: rRr }
                                    ]
                                }
                            ]);
                        } catch(err) {
                            console.warn('3D window.controller error: ' + err);
                        }
                    }

                    // 原生实例方法直调保证：直接作用于当前活动车模节点，不受模块变量隔离影响
                    if (typeof c.handleTrunk === 'function') c.handleTrunk(trunkOpen, imm);
                    if (typeof c.handleLFDoor === 'function') c.handleLFDoor(doorFlOpen, imm);
                    if (typeof c.handleRFDoor === 'function') c.handleRFDoor(doorFrOpen, imm);
                    if (typeof c.handleLRDoor === 'function') c.handleLRDoor(doorRlOpen, imm);
                    if (typeof c.handleRRDoor === 'function') c.handleRRDoor(doorRrOpen, imm);
                    if (typeof c.handleDoorLFWindow === 'function') c.handleDoorLFWindow(rFl, imm);
                    if (typeof c.handleDoorRFWindow === 'function') c.handleDoorRFWindow(rFr, imm);
                    if (typeof c.handleDoorLRWindow === 'function') c.handleDoorLRWindow(rRl, imm);
                    if (typeof c.handleDoorRRWindow === 'function') c.handleDoorRRWindow(rRr, imm);

                    // 6. 底层物理网格位姿强力锚定：
                    // 当冷启动或全车 full_car.fbx 网格覆盖完毕后，直接锚定网格坐标，防止顶点被初始全关网格冲刷覆盖
                    if (imm) {
                        if (c.doorLFWindow && c.doorLFWindow.frameAnim) {
                            c.doorLFWindow.winProcess = rFl;
                            c.doorLFWindow.frameAnim.setCurrentFrame(rFl);
                        }
                        if (c.doorRFWindow && c.doorRFWindow.frameAnim) {
                            c.doorRFWindow.winProcess = rFr;
                            c.doorRFWindow.frameAnim.setCurrentFrame(rFr);
                        }
                        if (c.doorLRWindow && c.doorLRWindow.frameAnim) {
                            c.doorLRWindow.winProcess = rRl;
                            c.doorLRWindow.frameAnim.setCurrentFrame(rRl);
                        }
                        if (c.doorRRWindow && c.doorRRWindow.frameAnim) {
                            c.doorRRWindow.winProcess = rRr;
                            c.doorRRWindow.frameAnim.setCurrentFrame(rRr);
                        }
                        if (c.trunkNode) {
                            c.trunkNode.rotation.z = trunkOpen ? c.maxTrunkOpenAngle : 0;
                            c.__TrunkState__ = 0;
                        }
                    }

                    // 5. 驱动车轮旋转与地面车道线飞退 (车辆行驶物理级动效)
                    var speed = Number(state.speed) || 0;
                    var isDark = state.isDark !== undefined ? !!state.isDark : true;
                    if (typeof c.handleVehicleMove === 'function') {
                        if (c.__laneLine__) {
                            c.__laneLine__.visible = speed > 0;
                            // 动态调节车道线 Shader 颜色：浅色模式使用零跑科技蓝高光流动标线，深色模式使用高亮纯白流光
                            if (c.__laneLine__.material && c.__laneLine__.material.uniforms && c.__laneLine__.material.uniforms.color) {
                                if (isDark) {
                                    c.__laneLine__.material.uniforms.color.value.set(0.35, 0.88, 1.0);
                                } else {
                                    c.__laneLine__.material.uniforms.color.value.set(0.0, 0.42, 1.0);
                                }
                            }
                        }
                        c.handleVehicleMove(speed);
                    }

                    // 7. 同步官方 3D 全景天幕日夜模式 (深色模式星空夜景，浅色模式日光晴空)
                    var skyBox = (v && v.frameState && v.frameState.skyBox) || (window.viewer && window.viewer.frameState && window.viewer.frameState.skyBox);
                    if (skyBox) {
                        skyBox.visible = true;
                        if (imm && typeof skyBox.setDayOrNightImmediately === 'function') {
                            skyBox.setDayOrNightImmediately(isDark ? 1.0 : 0.0);
                        } else if (typeof skyBox.setDayOrNight === 'function') {
                            skyBox.setDayOrNight(isDark);
                        }
                    }

                    // 8. 充电状态驱动 (插枪充电时充电桩就位 + 底盘能量流光)
                    var isCharging = !!state.isCharging;
                    if (typeof c.handleCharge === 'function') {
                        c.handleCharge(isCharging, imm);
                    }

                    // 9. 车窗除雾水汽消融动画
                    var isDemist = !!state.isDemist;
                    if (typeof c.handleDemist === 'function') {
                        c.handleDemist(isDemist);
                    }

                    // 10. 后视镜与后窗微晶发热暖光
                    var isMirrorHeating = !!state.isMirrorHeating;
                    if (typeof c.handleRearViewWarmEffect === 'function') {
                        c.handleRearViewWarmEffect(isMirrorHeating);
                    }

                    // 11. 四轮胎压告警独立发光
                    if (typeof c.handleTirePressure === 'function') {
                        c.handleTirePressure(!!state.tp_lf_alert, 'LF');
                        c.handleTirePressure(!!state.tp_rf_alert, 'RF');
                        c.handleTirePressure(!!state.tp_lr_alert, 'LR');
                        c.handleTirePressure(!!state.tp_rr_alert, 'RR');
                    }

                    // 12. 全功能透镜车灯联动
                    var lowBeamOn = state.lowBeam !== undefined ? !!state.lowBeam : false;
                    if (typeof c.handleLowBeamLight === 'function') {
                        c.handleLowBeamLight(lowBeamOn, imm);
                    }
                    if (typeof c.handleHeadLight === 'function') {
                        c.handleHeadLight(lowBeamOn, imm);
                    }
                    var stopLightOn = state.stopLight !== undefined ? !!state.stopLight : false;
                    if (typeof c.handleStopLight === 'function') {
                        c.handleStopLight(stopLightOn, imm);
                    }

                    // 13. 前舱盖 (引擎盖/前备箱) 开启动效
                    var bonnetOpen = state.bonnet > 0;
                    if (typeof c.handleBonnet === 'function') {
                        c.handleBonnet(bonnetOpen, imm);
                    }

                    // 14. 贯穿式星环日行灯/示宽灯带 (行车点亮、通电点亮、暗夜模式点亮)
                    var markLightOn = state.markLight !== undefined ? !!state.markLight : isDark;
                    if (typeof c.handleMarkLight === 'function') {
                        c.handleMarkLight(markLightOn, imm);
                    }
                    if (typeof c.handleDayLight === 'function') {
                        c.handleDayLight(markLightOn, imm);
                    }

                    // 严禁在此处直接强行给 c.LFDoorState、c.TrunkState 或 c.DoorLFWindowState 赋值，
                    // 避免破坏官方内部 handleDoor / handleDoorWindow 状态机的差异检测与动画标记开启。

                    console.log('3D Official car states dispatched: Trunk=' + trunkOpen + ', LFDoor=' + doorFlOpen + ', WinLF=' + rFl + ', WinFR=' + rFr + ', WinRL=' + rRl + ', WinRR=' + rRr + ', imm=' + imm);
                } catch(e) {
                    console.warn('3D error setting window.car: ' + e);
                }
            }
            apply();
        })($stateJsonStr);
        """.trimIndent()
        evaluateJavascript(script, null)
    }

    private fun buildCarParamJson(param: JSONObject?): String {
        val obj = JSONObject()
        val carType = param?.optString("carType").orEmpty().ifEmpty { "C16" }
        // 官方 3D 引擎模型配置中，C16 / C10 / C11 等车型的动画参数表 (this.params) 均以 2026/2027 为基线，
        // 传 2025 会导致 this.params[year].door 报 undefined 错误中断整车动画初始化。
        val rawYear = param?.optInt("year") ?: 2026
        val year = if (rawYear < 2026) 2026 else rawYear
        val carTypeCode = param?.optString("carTypeCode").orEmpty()
        val colorCode = param?.optInt("colorCode") ?: 0
        val rudder = param?.optInt("rudder") ?: 0

        obj.put("carType", carType)
        obj.put("year", year)
        obj.put("carTypeCode", carTypeCode)
        obj.put("colorCode", colorCode)
        obj.put("rudder", rudder)
        obj.put("radius", 7.2)
        obj.put("offset", 0.0)

        if (param != null) {
            if (param.has("roofColor") && !param.isNull("roofColor")) {
                obj.put("roofColorCode", param.optInt("roofColor"))
            }
            if (param.has("roofColorCode") && !param.isNull("roofColorCode")) {
                obj.put("roofColorCode", param.optInt("roofColorCode"))
            }
            if (param.has("seat") && !param.isNull("seat")) {
                obj.put("seat", param.optInt("seat"))
            }
            if (param.has("alias") && !param.isNull("alias")) {
                obj.put("alias", param.optString("alias"))
            }
        }
        return obj.toString()
    }

    private fun injectInitScript(view: WebView?) {
        val paramStr = buildCarParamJson(modelParam).replace("'", "\\'")
        val script = """
        (function() {
            window.IOSWebview = true;
            window.onerror = function(m, s, l) { window.prompt('onJSError', m + ' @' + l); };
            window.addEventListener('unhandledrejection', function(e) {
                window.prompt('onReject', String(e.reason && (e.reason.message || e.reason)));
            });
            var tries = 0;
            function start() {
                if (typeof window.newInit === 'function') {
                    if (window.innerWidth > 0) {
                        window.prompt('onInitCalled', '');
                        var app = JSON.stringify({
                            width: window.innerWidth,
                            height: window.innerHeight,
                            energy: 0,
                            inland: 0
                        });
                        try {
                            window.newInit('$paramStr', app);
                        } catch(e) {
                            window.prompt('onInitThrow', String(e && (e.message || e)));
                        }
                        return;
                    }
                    setTimeout(start, 100);
                    return;
                }
                if (++tries > 400) {
                    window.prompt('onInitTimeout');
                    return;
                }
                setTimeout(start, 50);
            }
            start();
        })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun tuneViewer(view: WebView?) {
        val tuneScript = """
        (function() {
            var v = window.viewer;
            if (!v) return;

            // 1. 隐藏多余地面遮罩网格，保留车身、车道线与官方 3D 全景天空盒
            if (v.scene) {
                v.scene.children.forEach(function(c) {
                    if (c.isMesh) {
                        var isLane = (window.car && c === window.car.__laneLine__) ||
                                     (c.material && c.material.uniforms && c.material.uniforms.loopTilingOffset);
                        var isSky = (v.frameState && c === v.frameState.skyBox) ||
                                    (c.material && c.material.uniforms && (c.material.uniforms.dayTexture || c.material.uniforms.nightTexture));
                        var isCharger = (window.car && c === window.car.__charger__) ||
                                        (c.name && c.name.toLowerCase().indexOf("charger") >= 0);
                        var isLight = (c.name && (c.name.toLowerCase().indexOf("light") >= 0 || c.name.toLowerCase().indexOf("beam") >= 0 || c.name.toLowerCase().indexOf("lamp") >= 0));
                        if (!isLane && !isSky && !isCharger && !isLight) {
                            c.visible = false;
                        }
                    }
                });
                // 官方 3D 全景天幕已激活，保留原生天空盒渲染，不再执行 v.scene.background = null
            }

            // 2. 保证 WebGL 画布与 HTML 背景绝对透明
            if (v.renderer) {
                v.renderer.setClearColor(0x000000, 0);
                v.renderer.setClearAlpha(0);
            }
            if (document.body) document.body.style.background = 'transparent';
            if (document.documentElement) document.documentElement.style.background = 'transparent';

            // 3. 核心光学放大与视角定位：设置前侧斜 45 度黄金展车视角 (大幅抬升车身，完整显示四轮与前脸)
            var cam = (v.controls && v.controls.object) || v.camera;
            var ctrl = v.controls || v.control || v.orbitControls;

            if (cam && ctrl) {
                if (!v.__leapTuned) {
                    v.__leapTuned = true;

                    // 目标点设置在 y = 0.16，使车模在 155dp 紧凑展台下车轮精准距离卡片底边 25dp
                    var target = new THREE.Vector3(0, 0.16, 0);
                    ctrl.target.copy(target);

                    // 镜头位置：前侧斜 45 度 (x = -14.5, z = 14.5, y = 1.96)，平视角度，完整展现四轮、轮毂与前脸
                    cam.position.set(-14.5, 1.96, 14.5);
                    cam.lookAt(target);

                    // 固化控制器坐标与默认视角
                    if (ctrl.target0) ctrl.target0.copy(target);
                    if (ctrl.position0) ctrl.position0.set(-14.5, 1.96, 14.5);
                    if (typeof ctrl.saveState === 'function') ctrl.saveState();
                    if (typeof ctrl.update === 'function') ctrl.update();

                    console.log('3D Golden front-45 angle set: cam=(-14.5, 1.96, 14.5), target=(0, 0.16, 0)');
                }

                // 再缩小 1/10 (1.025 * 0.9 = 0.9225)，视野更舒适精致
                var targetZoom = 0.9225;
                cam.zoom = targetZoom;
                if (!cam.__leapZoomHooked) {
                    cam.__leapZoomHooked = true;
                    var origUpdate = cam.updateProjectionMatrix;
                    cam.updateProjectionMatrix = function() {
                        this.zoom = targetZoom;
                        origUpdate.call(this);
                    };
                }
                cam.updateProjectionMatrix();
            }
        })();
        """.trimIndent()
        view?.evaluateJavascript(tuneScript, null)
    }

    private fun capture3DSnapshot(view: WebView?) {
        if (snapshotSaved || vin.isBlank()) return
        val captureScript = """
        (function() {
            var v = window.viewer;
            if (!v || v.__leapSnapshotTaken) return;

            function hasCarMeshes() {
                if (window.car && window.car.children && window.car.children.length > 0) return true;
                if (v.scene) {
                    var meshCount = 0;
                    v.scene.traverse(function(obj) {
                        if (obj.isMesh && obj.visible) meshCount++;
                    });
                    return meshCount >= 5;
                }
                return false;
            }

            function doSnapshot(attempt) {
                try {
                    if (!hasCarMeshes() && attempt < 4) {
                        console.log('3D snapshot waiting for car meshes, attempt=' + attempt);
                        setTimeout(function() { doSnapshot(attempt + 1); }, 400);
                        return;
                    }

                    var cam = (v.controls && v.controls.object) || v.camera;
                    var activeCam = cam || v.camera;
                    var sky = v.frameState && v.frameState.skyBox;
                    if (sky) sky.visible = false;

                    if (v.renderer && v.scene && activeCam) {
                        v.renderer.render(v.scene, activeCam);
                    }
                    var canvas = v.renderer ? v.renderer.domElement : null;
                    if (sky) sky.visible = true;

                    if (canvas && typeof canvas.toDataURL === 'function') {
                        var url = canvas.toDataURL('image/png');
                        console.log('3D snapshot attempt=' + attempt + ', url length=' + (url ? url.length : 0));
                        // 完整 3D 车模 Base64 数据通常大于 30KB，若小于 2000 字节则通常为空白画板或尚未就绪
                        if (url && url.length > 2000) {
                            v.__leapSnapshotTaken = true;
                            if (window.LeapNative && typeof window.LeapNative.saveSnapshot === 'function') {
                                window.LeapNative.saveSnapshot(url);
                            } else {
                                window.prompt('on3DSnapshot', url);
                            }
                        } else if (attempt < 4) {
                            setTimeout(function() { doSnapshot(attempt + 1); }, 400);
                        }
                    }
                } catch(e) {
                    console.warn('3D snapshot export error: ' + e);
                }
            }

            setTimeout(function() { doSnapshot(1); }, 300);
        })();
        """.trimIndent()
        view?.evaluateJavascript(captureScript, null)
    }

    private fun handle3DSnapshot(base64: String) {
        Log.i(TAG, "handle3DSnapshot called, vin='$vin', snapshotSaved=$snapshotSaved")
        if (vin.isBlank() || snapshotSaved) return
        Thread {
            try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@Thread
                val cropped = VehicleImageCache.cropTransparentPixels(raw)
                if (cropped == null) {
                    Log.w(TAG, "3D 定妆照裁切后为无效/纯透明图，拒绝保存，保护小组件回退官方2D图")
                    return@Thread
                }
                VehicleImageCache.save3DSnapshot(context, vin, cropped, modelKey)
                snapshotSaved = true
                ControlWidget.refreshData(context)
                Log.i(TAG, "已成功截取并保存 3D 车模高清定妆照: ${cropped.width}x${cropped.height} -> ${vin}_3d.png")
            } catch (e: Exception) {
                Log.w(TAG, "保存 3D 定妆照异常", e)
            }
        }.start()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                touchStartTime = android.os.SystemClock.uptimeMillis()
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(event.x - initialTouchX)
                val dy = Math.abs(event.y - initialTouchY)
                if (dx > 8f || dy > 8f) {
                    isDragging = true
                }
                if (dx > dy && dx > 8f) {
                    // 水平旋转车模时，禁止父级滑动截断
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else if (dy > dx && dy > 16f) {
                    // 纵向滑动时允许页面滚动
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP -> {
                val duration = android.os.SystemClock.uptimeMillis() - touchStartTime
                if (!isDragging && duration < 350) {
                    onCarClick?.invoke()
                }
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

class LeapNativeBridge(private val onSnapshot: (String) -> Unit) {
    @android.webkit.JavascriptInterface
    fun saveSnapshot(dataUrl: String) {
        android.util.Log.i("CarModel3DView", "LeapNativeBridge.saveSnapshot received, length=${dataUrl.length}")
        onSnapshot(dataUrl)
    }
}

object CarModel3DStateHelper {
    fun calculateEffectiveSpeed(
        gearStatus: String?,
        speedStr: String?,
        isDriving: Boolean? = null,
        cruising: Boolean = false
    ): Float {
        val gear = gearStatus?.trim()?.uppercase()
        val isDrivingGear = gear in setOf("D", "D挡", "DRIVE", "前进", "3", "R", "R挡", "REVERSE", "倒车", "1")
        val parsedSpeed = speedStr.orEmpty().replace("km/h", "", ignoreCase = true).trim().toFloatOrNull() ?: 0f
        return when {
            isDrivingGear && parsedSpeed <= 0f -> 0f
            isDrivingGear -> parsedSpeed
            cruising -> if (parsedSpeed > 0f) parsedSpeed else 60f
            isDriving == true && parsedSpeed <= 0f -> 0f
            isDriving == true -> parsedSpeed
            else -> parsedSpeed
        }
    }

    fun isActuallyDriving(
        gearStatus: String?,
        speedStr: String?,
        isDriving: Boolean? = null
    ): Boolean {
        val gear = gearStatus?.trim()?.uppercase()
        val isDrivingGear = gear in setOf("D", "D挡", "DRIVE", "前进", "3", "R", "R挡", "REVERSE", "倒车", "1")
        val speedValue = speedStr.orEmpty().replace("km/h", "", ignoreCase = true).trim().toFloatOrNull() ?: 0f
        return if (isDrivingGear && speedValue <= 0f) {
            false
        } else {
            isDrivingGear || isDriving == true || speedValue > 0f
        }
    }
}
