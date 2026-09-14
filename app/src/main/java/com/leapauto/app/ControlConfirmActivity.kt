package com.leapauto.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.leapauto.app.ui.frostedGlassCard
import com.leapauto.app.ui.glassCardBorder
import com.leapauto.app.ui.theme.LeapAutoTheme
import com.leapauto.app.ui.theme.LeapBlue
import com.leapauto.app.ui.theme.statusGood

/**
 * 桌面插件的高风险控车确认页。
 * 仅在本应用内可启动，验证成功后才交给 [ControlService] 下发云控命令。
 */
class ControlConfirmActivity : FragmentActivity() {

    companion object {
        const val EXTRA_COMMAND = "confirmation_command"
        private const val CONFIRM_TIMEOUT_SECONDS = 10
        private const val COUNTDOWN_INTERVAL_MS = 1_000L
        private const val MAX_PIN_ATTEMPTS = 3

        fun requiresConfirmation(command: String): Boolean =
            WidgetControlSecurity.isSensitiveCommand(command)
    }

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private lateinit var command: String
    private var showPin by mutableStateOf(false)
    private var pin by mutableStateOf("")
    private var message by mutableStateOf<String?>(null)
    private var verified by mutableStateOf(false)
    private var secondsRemaining by mutableStateOf(CONFIRM_TIMEOUT_SECONDS)
    private var pinAttempts = 0
    private var commandStarted = false

    private val countdown = object : Runnable {
        override fun run() {
            if (commandStarted || isFinishing) return
            if (secondsRemaining <= 1) {
                finishWithoutTransition()
                return
            }
            secondsRemaining -= 1
            timeoutHandler.postDelayed(this, COUNTDOWN_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        command = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
        if (!requiresConfirmation(command)) {
            finishWithoutTransition()
            return
        }

        setContent {
            val appearanceMode = SessionStore(this).loadAppearanceMode()
            LeapAutoTheme(darkTheme = appearanceMode.resolvesToDark(isSystemInDarkTheme())) {
                ControlConfirmationScreen(
                    command = command,
                    showPin = showPin,
                    pin = pin,
                    message = message,
                    verified = verified,
                    secondsRemaining = secondsRemaining,
                    onPinChange = { pin = it.take(4) },
                    onBiometric = ::requestBiometric,
                    onPinMode = { showPin = true },
                    onConfirmPin = ::confirmPin,
                    onCancel = ::finishWithoutTransition
                )
            }
        }
        timeoutHandler.postDelayed(countdown, COUNTDOWN_INTERVAL_MS)
        window.decorView.post { requestBiometric() }
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacks(countdown)
        super.onDestroy()
    }

    private fun requestBiometric() {
        if (commandStarted || isFinishing) return
        val availability = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (availability != BiometricManager.BIOMETRIC_SUCCESS) {
            showPin = true
            message = "当前设备无法使用生物识别，请输入操作密码"
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    startControl()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        showPin = true
                        message = null
                    } else if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_CANCELED) {
                        showPin = true
                        message = "生物识别不可用，请输入操作密码"
                    }
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("确认${commandLabel(command)}")
                .setSubtitle("验证后将向车辆发送控车指令")
                .setNegativeButtonText("使用密码")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()
        )
    }

    private fun confirmPin() {
        val savedPin = SessionStore(this).loadOpPassword()
        if (savedPin.isNullOrBlank()) {
            message = "未设置操作密码，请先打开 App 设置"
            return
        }
        if (pin != savedPin) {
            pinAttempts += 1
            pin = ""
            if (pinAttempts >= MAX_PIN_ATTEMPTS) {
                finishWithoutTransition()
            } else {
                message = "密码不正确，还可尝试 ${MAX_PIN_ATTEMPTS - pinAttempts} 次"
            }
            return
        }
        startControl()
    }

    private fun startControl() {
        if (commandStarted) return
        commandStarted = true
        verified = true
        timeoutHandler.removeCallbacks(countdown)
        startForegroundService(
            Intent(this, ControlService::class.java).putExtra(ControlService.EXTRA_COMMAND, command)
        )
        timeoutHandler.postDelayed({ finishWithoutTransition() }, 520L)
    }

    private fun finishWithoutTransition() {
        // The verification activity is launched from the widget. Keep the app
        // task in the background so finishing verification never reveals the
        // main screen over the launcher.
        moveTaskToBack(true)
        finish()
        overridePendingTransition(0, 0)
    }
}

@androidx.compose.runtime.Composable
private fun ControlConfirmationScreen(
    command: String,
    showPin: Boolean,
    pin: String,
    message: String?,
    verified: Boolean,
    secondsRemaining: Int,
    onPinChange: (String) -> Unit,
    onBiometric: () -> Unit,
    onPinMode: () -> Unit,
    onConfirmPin: () -> Unit,
    onCancel: () -> Unit
) {
    val iconRes = if (command == "unlock") R.drawable.ic_phosphor_lock_open else R.drawable.ic_phosphor_trunk_open
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0066FF).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.5f, h * 0.35f),
                        radius = w * 0.75f
                    ),
                    center = Offset(w * 0.5f, h * 0.35f),
                    radius = w * 0.75f
                )
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = if (verified) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            painter = painterResource(if (verified) R.drawable.ic_phosphor_check else iconRes),
                            contentDescription = null,
                            tint = if (verified) MaterialTheme.statusGood else LeapBlue,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (verified) "验证通过" else "安全验证", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (verified) "正在向车辆发送指令" else "确认后将立即执行控车操作",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlassCard(
                            shape = RoundedCornerShape(20.dp),
                            auraColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            auraCenter = Offset(0.2f, 0.3f)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent,
                    border = glassCardBorder(),
                    shadowElevation = 0.dp
                ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("本次控车操作", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(commandLabel(command), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (verified) "车辆正在处理该指令，请稍候。" else "身份验证完成后，指令将安全发送至车辆。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (verified) {
                Text(
                    "${commandLabel(command)}指令已发送",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.statusGood,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("选择验证方式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (showPin) {
                        OutlinedTextField(
                            value = pin,
                            onValueChange = onPinChange,
                            label = { Text("4 位操作密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = onConfirmPin,
                            enabled = pin.length == 4,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LeapBlue, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Text("确认${commandLabel(command)}")
                        }
                        TextButton(
                            onClick = onBiometric,
                            colors = ButtonDefaults.textButtonColors(contentColor = LeapBlue),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("改用指纹验证") }
                    } else {
                        Button(
                            onClick = onBiometric,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LeapBlue, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Text("使用指纹验证")
                        }
                        TextButton(
                            onClick = onPinMode,
                            colors = ButtonDefaults.textButtonColors(contentColor = LeapBlue),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) { Text("使用操作密码") }
                    }
                    if (message != null) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                            Text(
                                message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("取消操作")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (verified) "正在返回桌面"
                else "$secondsRemaining 秒后自动取消",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        }
    }
}

private fun commandLabel(command: String): String = when (command) {
    "unlock" -> "解锁车辆"
    "trunkOpen" -> "打开后备箱"
    else -> "控车"
}
