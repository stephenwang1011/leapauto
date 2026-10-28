package com.leapauto.app.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leapauto.app.ControlWidget
import com.leapauto.app.LeapmotorApi
import com.leapauto.app.R
import com.leapauto.app.SessionStore
import com.leapauto.app.ThemeManager
import com.leapauto.app.VehicleTheme
import com.leapauto.app.VehicleThemePolicy
import com.leapauto.app.ui.theme.LocalAppDarkTheme
import com.leapauto.app.ui.theme.statusGood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorBottomSheet(
    vin: String,
    currentThemeId: String,
    themes: List<VehicleTheme>,
    api: LeapmotorApi?,
    is3dSupported: Boolean,
    onDismiss: () -> Unit,
    onThemeApplied: (String) -> Unit,
    onNavigateToHome: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = LocalAppDarkTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var downloadingThemeId by remember { mutableStateOf<String?>(null) }
    var applyingThemeId by remember { mutableStateOf<String?>(null) }
    var activeThemeId by remember(vin, currentThemeId, is3dSupported) {
        mutableStateOf(VehicleThemePolicy.activeThemeId(is3dSupported, currentThemeId))
    }
    val availableThemes = VehicleThemePolicy.availableThemes(is3dSupported, themes)
    var showSwitchSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(vin, currentThemeId, is3dSupported) {
        if (!is3dSupported) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            ThemeManager.cleanInvalidBlurredCache(context)
            if (currentThemeId != ThemeManager.ORIGINAL_SKYBOX_THEME_ID && !ThemeManager.isThemeReady(context, currentThemeId)) {
                val currentObj = themes.find { it.themeId == currentThemeId }
                    ?: ThemeManager.PRESET_THEMES.find { it.themeId == currentThemeId }
                if (currentObj != null) {
                    val ok = ThemeManager.syncThemePackage(context, api, currentObj)
                    if (ok) {
                        withContext(Dispatchers.Main) {
                            if (VehicleThemePolicy.canApplyDownloadResult(
                                    vin, SessionStore(context).load().selectedVin,
                                    currentThemeId, ThemeManager.getActiveThemeId(context, vin),
                                    ThemeManager.isVehicle3DSupported(context, vin)
                                )) {
                                onThemeApplied(currentThemeId)
                            }
                        }
                    }
                }
            }
        }
    }

    val pageBgColor = if (isDark) Color(0xFF161A23) else Color(0xFFF7F8FA)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = pageBgColor,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "主题中心",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
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
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 主题列表
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (isDark) {
                    item(key = "night_brightness_$activeThemeId") {
                        NightBrightnessControl(
                            vin = vin,
                            themeId = activeThemeId,
                            supports3d = is3dSupported,
                            enabled = downloadingThemeId == null && applyingThemeId == null
                        )
                    }
                }
                items(availableThemes, key = { it.themeId }) { theme ->
                    val isSelected = activeThemeId == theme.themeId
                    val isReady = !is3dSupported || ThemeManager.isThemeReady(context, theme.themeId)
                    val isDownloading = downloadingThemeId == theme.themeId
                    val isApplying = applyingThemeId == theme.themeId
                    val isBusy = downloadingThemeId != null || applyingThemeId != null

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .frostedGlassCard(
                                shape = RoundedCornerShape(14.dp),
                                auraColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else null
                            )
                            .clickable(enabled = !isBusy && (!isSelected || !isReady)) {
                                if (isBusy || (isSelected && isReady)) return@clickable
                                if (isReady || theme.themeId == ThemeManager.ORIGINAL_SKYBOX_THEME_ID) {
                                    applyingThemeId = theme.themeId
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            ThemeManager.setActiveThemeId(context, vin, theme.themeId)
                                            ControlWidget.refreshData(context)
                                        }
                                        activeThemeId = theme.themeId
                                        onThemeApplied(theme.themeId)
                                        applyingThemeId = null
                                        showSwitchSuccessDialog = true
                                    }
                                } else {
                                    downloadingThemeId = theme.themeId
                                    coroutineScope.launch {
                                        val fullReady = withContext(Dispatchers.IO) {
                                            ThemeManager.syncThemePackage(context, api, theme)
                                        }
                                        downloadingThemeId = null
                                        if (fullReady) {
                                            applyingThemeId = theme.themeId
                                            withContext(Dispatchers.IO) {
                                                ThemeManager.setActiveThemeId(context, vin, theme.themeId)
                                                ControlWidget.refreshData(context)
                                            }
                                            activeThemeId = theme.themeId
                                            onThemeApplied(theme.themeId)
                                            applyingThemeId = null
                                            showSwitchSuccessDialog = true
                                        } else {
                                            Toast.makeText(context, "主题包下载失败，请检查网络", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = glassCardBorder(),
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val displayName = VehicleThemePolicy.displayName(is3dSupported, theme.themeId, availableThemes)
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (theme.tags.isNotEmpty()) {
                                        theme.tags.forEach { tag ->
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = MaterialTheme.statusGood.copy(alpha = 0.16f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = tag,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.statusGood
                                                )
                                            }
                                        }
                                    }
                                    val isDefaultTheme = if (is3dSupported) theme.themeId == ThemeManager.DEFAULT_THEME_ID else theme.isDefault
                                    if (isDefaultTheme) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "默认",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                val displayDesc = when {
                                    !is3dSupported -> theme.description
                                    theme.themeId == ThemeManager.ORIGINAL_SKYBOX_THEME_ID || theme.name == "默认主题" -> "车模型 3D 原厂天空盒背景"
                                    else -> theme.description
                                }
                                if (displayDesc.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Text(
                                        text = displayDesc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // 状态标记 / 下载提示 / 应用中 / 选中指示
                            when {
                                isApplying -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "应用中",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                isDownloading -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "下载中",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                isSelected && isReady -> {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_phosphor_check),
                                            contentDescription = "已选用",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                !isReady -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f))
                                    ) {
                                        Text(
                                            text = if (isSelected) "下载并生效" else "下载并应用",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f))
                                    ) {
                                        Text(
                                            text = "应用",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSwitchSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchSuccessDialog = false },
            modifier = Modifier.solidDialogModifier(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    text = "切换成功",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "切换成功，是否返回主页查看？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSwitchSuccessDialog = false
                        onDismiss()
                        onNavigateToHome()
                    }
                ) {
                    Text(
                        "确定",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSwitchSuccessDialog = false
                    }
                ) {
                    Text(
                        "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}
