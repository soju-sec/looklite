package app.looklite.android.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.R
import app.looklite.android.engine.PresetThumbnailGenerator
import app.looklite.android.model.*
import app.looklite.android.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

enum class WorkbenchTab {
    LOOKS, ADJUST
}

enum class AdjustSubTab(val labelResId: Int) {
    LIGHT(R.string.adjust_light),
    COLOR(R.string.adjust_color),
    RETOUCH(R.string.adjust_retouch),
    FX(R.string.adjust_fx)
}

@Composable
fun StudioWorkbench(
    isEnabled: Boolean,
    activeTab: WorkbenchTab,
    onTabSelected: (WorkbenchTab) -> Unit,
    // Looks Tab
    selectedCategory: LookCategory,
    onCategorySelected: (LookCategory) -> Unit,
    presets: List<PresetProfile>,
    activePresetId: String,
    activeCustomLookId: String?,
    onPresetSelected: (PresetProfile) -> Unit,
    customLooks: List<CustomLook>,
    onCustomLookSelected: (CustomLook) -> Unit,
    onRequestSaveCustomLook: () -> Unit,
    onRenameCustomLook: (CustomLook) -> Unit,
    onOverwriteCustomLook: (CustomLook) -> Unit,
    onDeleteCustomLook: (CustomLook) -> Unit,
    baseThumbPixels: IntArray?,
    intensity: Int,
    onIntensityChanged: (Int) -> Unit,
    // Adjust Tab
    sliders: SliderValues,
    onSliderChanged: (SliderValues) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioWorkbenchBg)
    ) {
        // Apple 0.5.dp hairline separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(StudioHairlineBorder)
        )

        // Active Workbench Panel (dimmed & locked when no photo is loaded)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isEnabled) 1f else 0.32f)
                .then(
                    if (!isEnabled) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                    } else Modifier
                )
                .padding(top = 8.dp, bottom = 6.dp)
        ) {
            when (activeTab) {
                WorkbenchTab.LOOKS -> LooksPanel(
                    selectedCategory = selectedCategory,
                    onCategorySelected = onCategorySelected,
                    presets = presets,
                    activePresetId = activePresetId,
                    activeCustomLookId = activeCustomLookId,
                    onPresetSelected = onPresetSelected,
                    customLooks = customLooks,
                    onCustomLookSelected = onCustomLookSelected,
                    onRequestSaveCustomLook = onRequestSaveCustomLook,
                    onRenameCustomLook = onRenameCustomLook,
                    onOverwriteCustomLook = onOverwriteCustomLook,
                    onDeleteCustomLook = onDeleteCustomLook,
                    baseThumbPixels = baseThumbPixels,
                    intensity = intensity,
                    onIntensityChanged = onIntensityChanged
                )

                WorkbenchTab.ADJUST -> AdjustPanel(
                    sliders = sliders,
                    onSliderChanged = onSliderChanged
                )
            }
        }

        // Apple 0.5.dp hairline divider above bottom 2-tab bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(StudioHairlineBorder)
        )

        // Flush Bottom Navigation Bar (2 Focused Tabs: Looks, Adjust)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(50.dp)
                .alpha(if (isEnabled) 1f else 0.45f)
                .then(
                    if (!isEnabled) {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        }
                    } else Modifier
                )
                .background(StudioWorkbenchBg)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DockedNavTab(
                label = stringResource(R.string.tab_looks),
                icon = Icons.Rounded.AutoAwesome,
                isActive = activeTab == WorkbenchTab.LOOKS,
                onClick = { onTabSelected(WorkbenchTab.LOOKS) }
            )
            DockedNavTab(
                label = stringResource(R.string.tab_adjust),
                icon = Icons.Rounded.Tune,
                isActive = activeTab == WorkbenchTab.ADJUST,
                onClick = { onTabSelected(WorkbenchTab.ADJUST) }
            )
        }
    }
}

@Composable
private fun RowScope.DockedNavTab(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) StudioAccent else StudioTextSecondary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isActive) StudioTextPrimary else StudioTextSecondary
            )
        }
    }
}

@Composable
private fun LooksPanel(
    selectedCategory: LookCategory,
    onCategorySelected: (LookCategory) -> Unit,
    presets: List<PresetProfile>,
    activePresetId: String,
    activeCustomLookId: String?,
    onPresetSelected: (PresetProfile) -> Unit,
    customLooks: List<CustomLook>,
    onCustomLookSelected: (CustomLook) -> Unit,
    onRequestSaveCustomLook: () -> Unit,
    onRenameCustomLook: (CustomLook) -> Unit,
    onOverwriteCustomLook: (CustomLook) -> Unit,
    onDeleteCustomLook: (CustomLook) -> Unit,
    baseThumbPixels: IntArray?,
    intensity: Int,
    onIntensityChanged: (Int) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Horizontal Scrollable Text Tabs with 2.dp bottom indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LookCategory.values().forEach { cat ->
                val isSelected = cat == selectedCategory
                val label = when (cat) {
                    LookCategory.MY_LOOKS -> stringResource(R.string.cat_my_looks)
                    LookCategory.KODAK -> "Kodak"
                    LookCategory.FUJI -> "Fuji"
                    LookCategory.LEICA -> "Leica"
                    LookCategory.HASSELBLAD -> "Hasselblad"
                    LookCategory.ILFORD -> "Ilford"
                    LookCategory.DIGICAM -> "Digicam"
                    LookCategory.RETOUCH -> stringResource(R.string.cat_retouch)
                    LookCategory.BEAUTY -> "Beauty"
                }

                Column(
                    modifier = Modifier
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCategorySelected(cat)
                        }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = label,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) StudioTextPrimary else StudioTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(2.dp)
                            .background(if (isSelected) StudioAccent else Color.Transparent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Slim Borderless Intensity Slider
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BorderlessSlider(
                value = intensity.toFloat(),
                min = 0f,
                max = 100f,
                defaultValue = 50f,
                onValueChange = { onIntensityChanged(it.roundToInt()) },
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "$intensity%",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = StudioTextPrimary,
                style = TextStyle(fontFeatureSettings = "tnum"),
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live-Filtered 68.dp x 88.dp Thumbnail Strip
        if (selectedCategory == LookCategory.MY_LOOKS) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "save_look_card") {
                    SaveLookDashedCard(onClick = onRequestSaveCustomLook)
                }

                items(customLooks, key = { it.id }) { look ->
                    val isSelected = activeCustomLookId == look.id
                    CustomLookThumbCard(
                        look = look,
                        isSelected = isSelected,
                        intensity = if (isSelected) intensity else look.intensity,
                        baseThumbPixels = baseThumbPixels,
                        onClick = { onCustomLookSelected(look) },
                        onRename = { onRenameCustomLook(look) },
                        onOverwrite = { onOverwriteCustomLook(look) },
                        onDelete = { onDeleteCustomLook(look) }
                    )
                }
            }
        } else {
            val filteredPresets = remember(presets, selectedCategory) {
                when (selectedCategory) {
                    LookCategory.KODAK -> presets.filter { it.brand == CameraBrand.KODAK }
                    LookCategory.FUJI -> presets.filter { it.brand == CameraBrand.FUJIFILM }
                    LookCategory.LEICA -> presets.filter { it.brand == CameraBrand.LEICA }
                    LookCategory.HASSELBLAD -> presets.filter { it.brand == CameraBrand.HASSELBLAD }
                    LookCategory.ILFORD -> presets.filter { it.brand == CameraBrand.VINTAGE }
                    LookCategory.DIGICAM -> presets.filter { it.brand == CameraBrand.DIGICAM }
                    LookCategory.RETOUCH -> presets.filter { it.brand == CameraBrand.POLES }
                    LookCategory.BEAUTY -> presets.filter { it.brand == CameraBrand.BEAUTY }
                    LookCategory.MY_LOOKS -> emptyList()
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPresets, key = { it.id }) { preset ->
                    val isSelected = activeCustomLookId == null && preset.id == activePresetId
                    PresetLiveThumbCard(
                        preset = preset,
                        isSelected = isSelected,
                        intensity = intensity,
                        baseThumbPixels = baseThumbPixels,
                        onClick = { onPresetSelected(preset) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveLookDashedCard(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StudioElevatedBg),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = StudioTextSecondary.copy(alpha = 0.55f),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    ),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = StudioAccent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.save_look),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = StudioAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PresetLiveThumbCard(
    preset: PresetProfile,
    isSelected: Boolean,
    intensity: Int,
    baseThumbPixels: IntArray?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var thumbBitmap by remember(preset.id, baseThumbPixels) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(preset.id, baseThumbPixels) {
        if (baseThumbPixels != null) {
            thumbBitmap = PresetThumbnailGenerator.renderThumbnail(
                context = context,
                basePixels = baseThumbPixels,
                lutFile = preset.lutFile,
                sliders = preset.defaultSliders,
                intensity = 1f
            )
        }
    }

    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(68.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(StudioElevatedBg)
                .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) StudioAccent else StudioHairlineBorder,
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            val bmp = thumbBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = preset.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(preset.gradientColors.first))
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(StudioAccent)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$intensity%",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = preset.name,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) StudioTextPrimary else StudioTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomLookThumbCard(
    look: CustomLook,
    isSelected: Boolean,
    intensity: Int,
    baseThumbPixels: IntArray?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onOverwrite: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    var thumbBitmap by remember(look.id, look.lutFile, look.sliders, baseThumbPixels) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(look.id, look.lutFile, look.sliders, baseThumbPixels) {
        if (baseThumbPixels != null) {
            thumbBitmap = PresetThumbnailGenerator.renderThumbnail(
                context = context,
                basePixels = baseThumbPixels,
                lutFile = look.lutFile,
                sliders = look.sliders,
                intensity = look.intensity / 100f
            )
        }
    }

    Box {
        Column(
            modifier = Modifier
                .width(68.dp)
                .combinedClickable(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showMenu = true
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(68.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioElevatedBg)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) StudioAccent else StudioHairlineBorder,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                val bmp = thumbBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = look.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(StudioAccent)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "$intensity%",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = look.name,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) StudioTextPrimary else StudioTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(StudioElevatedBg)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename), color = StudioTextPrimary, fontSize = 13.sp) },
                onClick = {
                    showMenu = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_overwrite), color = StudioTextPrimary, fontSize = 13.sp) },
                onClick = {
                    showMenu = false
                    onOverwrite()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete), color = StudioAccent, fontSize = 13.sp) },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun AdjustPanel(
    sliders: SliderValues,
    onSliderChanged: (SliderValues) -> Unit
) {
    var subTab by remember { mutableStateOf(AdjustSubTab.LIGHT) }
    val haptic = LocalHapticFeedback.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // Sub-tabs: Light, Color, Retouch, Grain / FX
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdjustSubTab.values().forEach { tab ->
                val isSelected = tab == subTab
                Column(
                    modifier = Modifier
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            subTab = tab
                        }
                        .padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(tab.labelResId),
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) StudioTextPrimary else StudioTextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(2.dp)
                            .background(if (isSelected) StudioAccent else Color.Transparent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Borderless Center-Detent Tick Sliders
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 124.dp, max = 148.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (subTab) {
                AdjustSubTab.LIGHT -> {
                    DetentSliderRow("Exposure", sliders.exposure, -3f, 3f, 0f, isDecimal = true) { onSliderChanged(sliders.copy(exposure = it)) }
                    DetentSliderRow("Contrast", sliders.contrast, -100f, 100f, 0f) { onSliderChanged(sliders.copy(contrast = it)) }
                    DetentSliderRow("Highlights", sliders.highlights, -100f, 100f, 0f) { onSliderChanged(sliders.copy(highlights = it)) }
                    DetentSliderRow("Shadows", sliders.shadows, -100f, 100f, 0f) { onSliderChanged(sliders.copy(shadows = it)) }
                    DetentSliderRow("Whites", sliders.whites, -100f, 100f, 0f) { onSliderChanged(sliders.copy(whites = it)) }
                    DetentSliderRow("Blacks", sliders.blacks, -100f, 100f, 0f) { onSliderChanged(sliders.copy(blacks = it)) }
                }

                AdjustSubTab.COLOR -> {
                    DetentSliderRow("Temp", sliders.temperature, -100f, 100f, 0f) { onSliderChanged(sliders.copy(temperature = it)) }
                    DetentSliderRow("Tint", sliders.tint, -100f, 100f, 0f) { onSliderChanged(sliders.copy(tint = it)) }
                    DetentSliderRow("Vibrance", sliders.vibrance, -100f, 100f, 0f) { onSliderChanged(sliders.copy(vibrance = it)) }
                    DetentSliderRow("Saturation", sliders.saturation, -100f, 100f, 0f) { onSliderChanged(sliders.copy(saturation = it)) }
                }

                AdjustSubTab.RETOUCH -> {
                    DetentSliderRow("Skin Smooth", sliders.skinSmooth, 0f, 100f, 0f) { onSliderChanged(sliders.copy(skinSmooth = it)) }
                    DetentSliderRow("Skin Radiance", sliders.skinRadiance, 0f, 100f, 0f) { onSliderChanged(sliders.copy(skinRadiance = it)) }
                    DetentSliderRow("Clarity", sliders.clarity, -100f, 100f, 0f) { onSliderChanged(sliders.copy(clarity = it)) }
                    DetentSliderRow("Glow", sliders.glow, 0f, 100f, 0f) { onSliderChanged(sliders.copy(glow = it)) }
                }

                AdjustSubTab.FX -> {
                    DetentSliderRow("Sharpness", sliders.sharpness, 0f, 100f, 0f) { onSliderChanged(sliders.copy(sharpness = it)) }
                    DetentSliderRow("Vignette", sliders.vignette, 0f, 100f, 0f) { onSliderChanged(sliders.copy(vignette = it)) }
                    DetentSliderRow("Grain", sliders.grainIntensity, 0f, 100f, 0f) { onSliderChanged(sliders.copy(grainIntensity = it)) }
                    DetentSliderRow("Grain Size", sliders.grainSize, 1f, 100f, 10f) { onSliderChanged(sliders.copy(grainSize = it)) }
                }
            }
        }
    }
}

@Composable
private fun DetentSliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    defaultValue: Float,
    isDecimal: Boolean = false,
    onValueChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val valueText = if (isDecimal) String.format("%.2f", value) else "${value.roundToInt()}"
    val isModified = abs(value - defaultValue) > 0.01f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Double-tap label to reset parameter to 0 / default
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (isModified) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isModified) StudioTextPrimary else StudioTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(84.dp)
                .pointerInput(defaultValue) {
                    detectTapGestures(
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onValueChange(defaultValue)
                        }
                    )
                }
        )

        BorderlessSlider(
            value = value,
            min = min,
            max = max,
            defaultValue = defaultValue,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = valueText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (isModified) StudioAccent else StudioTextSecondary,
            style = TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun BorderlessSlider(
    value: Float,
    min: Float,
    max: Float,
    defaultValue: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val hasCenterDetent = min < 0f && max > 0f
    var widthPx by remember { mutableFloatStateOf(1f) }
    var lastDetentCrossed by remember { mutableStateOf(abs(value) < 0.01f) }

    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)

    fun updateFromX(x: Float) {
        val frac = (x / widthPx).coerceIn(0f, 1f)
        var raw = min + frac * (max - min)
        if (hasCenterDetent) {
            val snapThreshold = (max - min) * 0.025f
            if (abs(raw) < snapThreshold) {
                raw = 0f
                if (!lastDetentCrossed) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastDetentCrossed = true
                }
            } else {
                lastDetentCrossed = false
            }
        }
        currentOnChange(raw)
    }

    Box(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(min, max) {
                detectTapGestures(
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnChange(defaultValue)
                    },
                    onTap = { offset ->
                        updateFromX(offset.x)
                    }
                )
            }
            .pointerInput(min, max) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    updateFromX(change.position.x)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height / 2f
            val frac = ((currentValue - min) / (max - min)).coerceIn(0f, 1f)
            val thumbX = frac * size.width

            // Apple-style rounded 3.dp track
            drawLine(
                color = Color(0x28FFFFFF),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Center detent tick & active fill
            if (hasCenterDetent) {
                val zeroFrac = (0f - min) / (max - min)
                val zeroX = zeroFrac * size.width
                drawLine(
                    color = StudioAccent,
                    start = Offset(zeroX, y),
                    end = Offset(thumbX, y),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0x99FFFFFF),
                    start = Offset(zeroX, y - 5.dp.toPx()),
                    end = Offset(zeroX, y + 5.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            } else {
                drawLine(
                    color = StudioAccent,
                    start = Offset(0f, y),
                    end = Offset(thumbX, y),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Crisp Apple circular thumb
            drawCircle(
                color = Color.White,
                radius = 7.5.dp.toPx(),
                center = Offset(thumbX, y)
            )
        }
    }
}

