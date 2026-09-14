package com.leapauto.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.leapauto.app.GeetestCaptchaResult
import com.leapauto.app.GeetestChallenge
import com.leapauto.app.R
import kotlinx.coroutines.delay

/**
 * 极验 GT4 安全拼图滑块验证弹窗。
 * 通过原生 WebView 加载极验官方轻量 JS，无需依赖繁重的第三方 AAR。
 */
@Composable
fun GeetestCaptchaDialog(
    challenge: GeetestChallenge,
    onSuccess: (GeetestCaptchaResult) -> Unit,
    onDismiss: () -> Unit,
    onError: (String) -> Unit = {}
) {
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        isLoading = true
        errorMessage = null
        delay(9_000L)
        if (isLoading && errorMessage == null) {
            errorMessage = "安全验证加载超时，可能零跑服务端未开放或网络受阻，请稍后重试"
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 顶栏：标题 + 关闭按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "安全拼图验证",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "拖动下方滑块完成拼图以继续登录",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_phosphor_x),
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 极验 WebView 容器
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(310.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (errorMessage != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_phosphor_warning),
                                contentDescription = "加载失败",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = errorMessage ?: "加载失败",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onDismiss) {
                                    Text("关闭")
                                }
                                Button(onClick = { reloadKey++ }) {
                                    Text("重试加载")
                                }
                            }
                        }
                    } else {
                        if (isLoading) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.5.dp
                                )
                                Text(
                                    text = "正在加载安全验证…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        GeetestWebView(
                            key = reloadKey,
                            challenge = challenge,
                            onReady = {
                                isLoading = false
                                errorMessage = null
                            },
                            onSuccess = onSuccess,
                            onError = { err ->
                                isLoading = false
                                errorMessage = err
                                onError(err)
                            },
                            onClose = onDismiss,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Text(
                    text = "验证通过后将自动提交登录并进入系统",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GeetestWebView(
    key: Int,
    challenge: GeetestChallenge,
    onReady: () -> Unit,
    onSuccess: (GeetestCaptchaResult) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bridge: GeetestJsBridge = remember(key) {
        GeetestJsBridge(
            requestId = challenge.requestId,
            onReady = onReady,
            onSuccess = onSuccess,
            onError = onError,
            onClose = onClose
        )
    }

    val html = remember(key, challenge.captchaId, challenge.riskType) {
        buildGeetestHtml(challenge.captchaId, challenge.riskType)
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("GeetestCaptcha", "${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()}")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        Log.e("GeetestCaptcha", "Resource error: ${error?.description} for ${request?.url}")
                    }
                }
                installGeetestBridge(bridge)
                loadDataWithBaseURL(
                    "https://appuser.leapmotor.cn",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        update = { webView ->
            // Re-load if key changes
        }
    )
}

private fun WebView.installGeetestBridge(bridge: GeetestJsBridge) {
    addJavascriptInterface(bridge, "AndroidBridge")
}

private class GeetestJsBridge(
    private val requestId: String,
    private val onReady: () -> Unit,
    private val onSuccess: (GeetestCaptchaResult) -> Unit,
    private val onError: (String) -> Unit,
    private val onClose: () -> Unit
) {
    @JavascriptInterface
    fun onReady() {
        onReady.invoke()
    }

    @JavascriptInterface
    fun onSuccess(resultJson: String) {
        try {
            val result = GeetestCaptchaResult.fromValidateJson(resultJson, requestId)
            onSuccess.invoke(result)
        } catch (e: Exception) {
            onError.invoke("解析验证结果失败: ${e.message}")
        }
    }

    @JavascriptInterface
    fun onError(errorJson: String) {
        onError.invoke(errorJson)
    }

    @JavascriptInterface
    fun onClose() {
        onClose.invoke()
    }
}

private fun buildGeetestHtml(captchaId: String, riskType: String): String {
    val cleanRiskType = riskType.trim()
    val riskAssignment = if (cleanRiskType.isNotEmpty()) {
        "config.riskType = \"$cleanRiskType\"; config.risk_type = \"$cleanRiskType\";"
    } else ""

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>安全验证</title>
            <script>
                window.onerror = function(msg, url, line) {
                    window.AndroidBridge && window.AndroidBridge.onError("脚本错误: " + msg + " (行" + line + ")");
                };
            </script>
            <script src="https://static.geetest.com/v4/gt4.js"></script>
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    background-color: transparent;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    overflow: hidden;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                }
                #captcha {
                    width: 100%;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                }
            </style>
        </head>
        <body>
            <div id="captcha"></div>
            <script>
                function startGeetest() {
                    if (typeof initGeetest4 !== 'function') {
                        window.AndroidBridge && window.AndroidBridge.onError("极验脚本未能成功下载，请检查网络");
                        return;
                    }
                    var config = {
                        captchaId: "$captchaId",
                        product: "float",
                        language: "zho",
                        protocol: "https://",
                        timeout: 8000
                    };
                    $riskAssignment
                    initGeetest4(config, function (captchaObj) {
                        window.captchaObj = captchaObj;
                        captchaObj.appendTo("#captcha");
                        captchaObj.onReady(function () {
                            window.AndroidBridge && window.AndroidBridge.onReady();
                            try {
                                if (typeof captchaObj.showBox === 'function') {
                                    captchaObj.showBox();
                                } else if (typeof captchaObj.showCaptcha === 'function') {
                                    captchaObj.showCaptcha();
                                }
                            } catch(e) {}
                        });
                        captchaObj.onSuccess(function () {
                            var result = captchaObj.getValidate();
                            if (result) {
                                window.AndroidBridge && window.AndroidBridge.onSuccess(JSON.stringify(result));
                            }
                        });
                        captchaObj.onError(function (error) {
                            var errStr = "";
                            try {
                                errStr = JSON.stringify(error || {});
                            } catch(e) {
                                errStr = String(error);
                            }
                            window.AndroidBridge && window.AndroidBridge.onError("验证码服务提示: " + errStr);
                        });
                        captchaObj.onClose(function () {
                            window.AndroidBridge && window.AndroidBridge.onClose();
                        });
                    });
                }

                if (document.readyState === 'complete' || document.readyState === 'interactive') {
                    startGeetest();
                } else {
                    window.addEventListener('DOMContentLoaded', startGeetest);
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}
