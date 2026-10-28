package com.leapauto.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.leapauto.app.CarModel3DManager
import com.leapauto.app.NightBrightnessPolicy
import com.leapauto.app.NightBrightnessStore
import com.leapauto.app.NightThemePolicy
import com.leapauto.app.ThemeManager
import com.leapauto.app.VehicleImageCache
import kotlin.math.roundToInt

@Composable
internal fun rememberNightBrightness(themeId: String, supports3d: Boolean): State<Float> {
    val context = LocalContext.current
    val store = remember(context) { NightBrightnessStore(context) }
    val value = remember(store, themeId, supports3d) { mutableStateOf(store.load(themeId, supports3d)) }
    DisposableEffect(store, themeId, supports3d) {
        val unsubscribe = store.observe { value.value = store.load(themeId, supports3d) }
        value.value = store.load(themeId, supports3d)
        onDispose { unsubscribe() }
    }
    return value
}

internal fun NightThemePolicy.BackgroundTreatment.applyTo(color: Color): Color {
    val gray = color.red * 0.2126f + color.green * 0.7152f + color.blue * 0.0722f
    fun channel(value: Float) = ((gray + (value - gray) * saturation) * gain + lift).coerceIn(0f, 1f)
    return Color(channel(color.red), channel(color.green), channel(color.blue), color.alpha)
}

/** Local draft dies on theme/vehicle change, dark-mode exit, dismissal or rotation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NightBrightnessControl(vin: String, themeId: String, supports3d: Boolean, enabled: Boolean) {
    val context = LocalContext.current
    val store = remember(context) { NightBrightnessStore(context) }
    val saved by rememberNightBrightness(themeId, supports3d)
    var draft by remember(vin, themeId, supports3d, saved, enabled) { mutableFloatStateOf(saved) }
    val interactions = remember(vin, themeId, supports3d) { MutableInteractionSource() }
    var dragging by remember(interactions) { mutableStateOf(false) }
    LaunchedEffect(interactions, saved, enabled) {
        interactions.interactions.collect {
            when (it) {
                is DragInteraction.Start -> dragging = true
                is DragInteraction.Cancel -> { draft = saved; dragging = false }
                is DragInteraction.Stop -> {
                    if (enabled) store.save(themeId, supports3d, draft)
                    dragging = false
                }
                else -> Unit
            }
        }
    }
    val bitmap = remember(vin, themeId, supports3d) { ThemeManager.load3dBgBitmap(context, themeId, vin) }
    val meta = remember(vin) { VehicleImageCache.getCachedMeta(context, vin) }
    val sky = supports3d && themeId == ThemeManager.ORIGINAL_SKYBOX_THEME_ID
    val modelKey = meta?.h5Key?.takeIf { it.isNotBlank() }
    val ready = remember(modelKey, meta?.srcKey) { modelKey != null && CarModel3DManager.isModelReady(context, modelKey, meta?.srcKey) }
    var previewFailed by remember(vin, themeId, modelKey) { mutableStateOf(false) }
    var backgroundSupported by remember(vin, themeId, modelKey) { mutableStateOf(true) }
    val backgroundSource = NightThemePolicy.selectBackground(
        themeId = themeId,
        show3D = sky && ready && !previewFailed,
        hasThemeBackground = bitmap != null && !sky
    )
    val presentation = NightThemePolicy.resolve(themeId, true, backgroundSource, brightnessPercent = draft)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("夜间背景亮度", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
            Text("${draft.roundToInt()}%", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
        }
        Box(Modifier.fillMaxWidth().height(144.dp).background(HomeNightStyle.page)) {
            if (backgroundSource == NightThemePolicy.BackgroundSource.ORIGINAL_SKYBOX && modelKey != null) {
                key(vin, modelKey) {
                    CarModel3DView(
                        h5Key = modelKey, modelParam = meta?.modelParam, themeId = themeId,
                        backgroundTreatment = presentation.background, previewOnly = true,
                        onError = { previewFailed = true },
                        onBackgroundSupport = { backgroundSupported = it },
                        modifier = Modifier.matchParentSize()
                    )
                }
            } else if (backgroundSource == NightThemePolicy.BackgroundSource.IMAGE && bitmap != null) {
                Image(
                    bitmap.asImageBitmap(), null, modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(presentation.background.colorMatrix()))
                )
            } else {
                Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(
                    presentation.background.applyTo(HomeNightStyle.cardStart),
                    presentation.background.applyTo(HomeNightStyle.cardMiddle),
                    presentation.background.applyTo(HomeNightStyle.page)
                ))))
            }
            val previewMessage = when {
                sky && !ready -> "3D 资源未就绪"
                previewFailed -> "3D 预览不可用"
                sky && !backgroundSupported -> "当前天空盒不支持亮度预览"
                else -> null
            }
            if (previewMessage != null) {
                Text(
                    previewMessage,
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 1f), 3f)
                    )
                )
            }
        }
        Slider(
            value = draft, onValueChange = { draft = NightBrightnessPolicy.decode(it, themeId) },
            onValueChangeFinished = { if (enabled && !dragging) store.save(themeId, supports3d, draft) },
            valueRange = NightBrightnessPolicy.MIN..NightBrightnessPolicy.MAX,
            interactionSource = interactions, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactions,
                    enabled = enabled,
                    thumbSize = DpSize(4.dp, 24.dp)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    enabled = enabled,
                    modifier = Modifier.height(4.dp),
                    drawStopIndicator = {}
                )
            }
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("较暗", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { draft = NightBrightnessPolicy.defaultPercent(themeId); store.reset(themeId, supports3d) }, enabled = enabled) {
                Text("恢复默认")
            }
            Spacer(Modifier.weight(1f))
            Text("较亮", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}
