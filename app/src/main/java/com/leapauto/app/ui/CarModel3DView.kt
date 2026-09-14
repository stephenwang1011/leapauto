package com.leapauto.app.ui

import android.annotation.SuppressLint
import android.content.Context
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
            CarModelWebView(ctx, actualModelDir, modelParam, onReady, onError, onCarClick)
        },
        update = { webView ->
            webView.onCarClick = onCarClick
        }
    )
}

@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
internal class CarModelWebView(
    context: Context,
    private val modelDir: File,
    private val modelParam: JSONObject?,
    private val onReady: () -> Unit,
    private val onError: () -> Unit,
    var onCarClick: (() -> Unit)? = null
) : WebView(context) {

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var touchStartTime = 0L
    private var isDragging = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false

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
                val path = url.path?.removePrefix("/") ?: return null
                val file = File(modelDir, path).canonicalFile
                if (!file.path.startsWith(modelDir.canonicalPath + File.separator) || !file.isFile) {
                    return WebResourceResponse(
                        "text/plain", "UTF-8", 404, "Not Found",
                        null, ByteArrayInputStream(ByteArray(0))
                    )
                }
                val mimeType = when {
                    path.endsWith(".html", ignoreCase = true) -> "text/html"
                    path.endsWith(".js", ignoreCase = true) -> "application/javascript"
                    path.endsWith(".json", ignoreCase = true) -> "application/json"
                    path.endsWith(".css", ignoreCase = true) -> "text/css"
                    path.endsWith(".png", ignoreCase = true) -> "image/png"
                    path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                    path.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    path.endsWith(".gltf", ignoreCase = true) -> "model/gltf+json"
                    path.endsWith(".bin", ignoreCase = true) -> "application/octet-stream"
                    path.endsWith(".csv", ignoreCase = true) -> "text/csv"
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
                    "onFirstFrame", "onFullCarLoaded" -> {
                        Log.i(TAG, "3D 车模首帧渲染就绪: $message")
                        onReady()
                        stripBackground(view)
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

    private fun buildCarParamJson(param: JSONObject?): String {
        val obj = JSONObject()
        val carType = param?.optString("carType").orEmpty()
        val year = param?.optInt("year") ?: 2025
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
                if (++tries > 200) {
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

    private fun stripBackground(view: WebView?) {
        val stripScript = """
        (function() {
            var v = window.viewer;
            if (!v || !v.scene || !v.renderer) return;
            v.scene.children.forEach(function(c) { if (c.isMesh) c.visible = false; });
            v.scene.background = null;
            v.renderer.setClearColor(0x000000, 0);
            v.renderer.setClearAlpha(0);
            document.body.style.background = 'transparent';
            document.documentElement.style.background = 'transparent';
        })();
        """.trimIndent()
        view?.evaluateJavascript(stripScript, null)
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
