package app.looklite.android.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.model.*
import app.looklite.android.ui.theme.*

enum class ActiveMode {
    PRESETS, ADJUST
}

enum class AdjustCategory(val label: String) {
    LIGHT("Light"),
    COLOR("Color"),
    POLES("Poles"),
    FX("Grain / FX")
}

@Composable
fun SubMenuCard(
    mode: ActiveMode,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    // Presets Mode Props
    selectedBrand: CameraBrand,
    onBrandSelected: (CameraBrand) -> Unit,
    presets: List<PresetProfile>,
    selectedPresetId: String,
    onPresetSelected: (PresetProfile) -> Unit,
    intensity: Int,
    onIntensityChanged: (Int) -> Unit,
    // Fine-Tune Mode Props
    sliders: SliderValues,
    onSliderChanged: (SliderValues) -> Unit,
    modifier: Modifier = Modifier
) {
    var adjustCat by remember { mutableStateOf(AdjustCategory.LIGHT) }

    AnimatedVisibility(
        visible = !isCollapsed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(20.dp), spotColor = Color(0x1A000000))
                .border(0.5.dp, AppleGlassBorder, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = AppleGlassBg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Sheet Grabber
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .size(width = 36.dp, height = 4.5.dp)
                        .clip(CircleShape)
                        .background(Color(0x33000000))
                        .clickable { onToggleCollapse() }
                )

                if (mode == ActiveMode.PRESETS) {
                    // Brand Pills Carousel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CameraBrand.values().forEach { brand ->
                            val isSelected = brand == selectedBrand
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(if (isSelected) AppleCoral else Color(0x14000000))
                                    .clickable { onBrandSelected(brand) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = brand.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else AppleLabelSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Direct Filter Intensity Slider (0–100%)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0x0D000000)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "FILTER INTENSITY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleLabelSecondary,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "$intensity%",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppleCoral
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .clickable { onIntensityChanged((intensity - 5).coerceAtLeast(0)) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = "-", tint = AppleLabelPrimary, modifier = Modifier.size(16.dp))
                                }

                                Slider(
                                    value = intensity.toFloat(),
                                    onValueChange = { onIntensityChanged(it.toInt()) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = AppleCoral,
                                        inactiveTrackColor = Color(0x26000000)
                                    )
                                )

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                        .clickable { onIntensityChanged((intensity + 5).coerceAtMost(100)) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "+", tint = AppleLabelPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Squircle Preset Cards (Strictly scoped to active brand)
                    val filteredPresets = presets.filter { it.brand == selectedBrand }
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredPresets, key = { it.id }) { preset ->
                            val isSelected = preset.id == selectedPresetId
                            PresetSquircleCard(
                                preset = preset,
                                isSelected = isSelected,
                                onClick = { onPresetSelected(preset) }
                            )
                        }
                    }
                } else {
                    // Fine-Tune Adjust Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AdjustCategory.values().forEach { cat ->
                            val isSelected = cat == adjustCat
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AppleCoral else Color(0x14000000))
                                    .clickable { adjustCat = cat }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cat.label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else AppleLabelSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Sliders Deck
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (adjustCat) {
                            AdjustCategory.LIGHT -> {
                                SliderRow("Exposure", sliders.exposure, -3f, 3f, 0.05f) { onSliderChanged(sliders.copy(exposure = it)) }
                                SliderRow("Contrast", sliders.contrast, -100f, 100f, 1f) { onSliderChanged(sliders.copy(contrast = it)) }
                                SliderRow("Highlights", sliders.highlights, -100f, 100f, 1f) { onSliderChanged(sliders.copy(highlights = it)) }
                                SliderRow("Shadows", sliders.shadows, -100f, 100f, 1f) { onSliderChanged(sliders.copy(shadows = it)) }
                                SliderRow("Whites", sliders.whites, -100f, 100f, 1f) { onSliderChanged(sliders.copy(whites = it)) }
                                SliderRow("Blacks", sliders.blacks, -100f, 100f, 1f) { onSliderChanged(sliders.copy(blacks = it)) }
                            }
                            AdjustCategory.COLOR -> {
                                SliderRow("Temp", sliders.temperature, -100f, 100f, 1f) { onSliderChanged(sliders.copy(temperature = it)) }
                                SliderRow("Tint", sliders.tint, -100f, 100f, 1f) { onSliderChanged(sliders.copy(tint = it)) }
                                SliderRow("Vibrance", sliders.vibrance, -100f, 100f, 1f) { onSliderChanged(sliders.copy(vibrance = it)) }
                                SliderRow("Saturation", sliders.saturation, -100f, 100f, 1f) { onSliderChanged(sliders.copy(saturation = it)) }
                            }
                            AdjustCategory.POLES -> {
                                SliderRow("Skin Smooth", sliders.skinSmooth, 0f, 100f, 1f) { onSliderChanged(sliders.copy(skinSmooth = it)) }
                                SliderRow("Skin Glow", sliders.skinRadiance, 0f, 100f, 1f) { onSliderChanged(sliders.copy(skinRadiance = it)) }
                                SliderRow("Clarity", sliders.clarity, -100f, 100f, 1f) { onSliderChanged(sliders.copy(clarity = it)) }
                                SliderRow("Soft Glow", sliders.glow, 0f, 100f, 1f) { onSliderChanged(sliders.copy(glow = it)) }
                                SliderRow("Sharpening", sliders.sharpness, 0f, 100f, 1f) { onSliderChanged(sliders.copy(sharpness = it)) }
                            }
                            AdjustCategory.FX -> {
                                SliderRow("Vignette", sliders.vignette, 0f, 100f, 1f) { onSliderChanged(sliders.copy(vignette = it)) }
                                SliderRow("Film Grain", sliders.grainIntensity, 0f, 100f, 1f) { onSliderChanged(sliders.copy(grainIntensity = it)) }
                                SliderRow("Grain Size", sliders.grainSize, 0.5f, 3f, 0.1f) { onSliderChanged(sliders.copy(grainSize = it)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetSquircleCard(
    preset: PresetProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(96.dp)
            .height(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .then(
                if (isSelected) Modifier.border(2.dp, AppleCoral, RoundedCornerShape(14.dp))
                else Modifier.border(0.5.dp, Color(0x1F000000), RoundedCornerShape(14.dp))
            ),
        shape = RoundedCornerShape(14.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Swatch header gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(preset.gradientColors.first), Color(preset.gradientColors.second))
                        )
                    )
            )

            // Name and tag
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = preset.name,
                    fontSize = 10.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) AppleCoral else AppleLabelPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preset.description.ifEmpty { preset.brand.displayName },
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppleLabelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    step: Float,
    onValueChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0x0A000000)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AppleLabelSecondary,
                modifier = Modifier.width(76.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .clickable { onValueChange((value - step).coerceAtLeast(min)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "-", tint = AppleLabelPrimary, modifier = Modifier.size(13.dp))
            }

            Slider(
                value = value,
                onValueChange = { onValueChange(it) },
                valueRange = min..max,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = AppleCoral,
                    inactiveTrackColor = Color(0x20000000)
                )
            )

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .clickable { onValueChange((value + step).coerceAtMost(max)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "+", tint = AppleLabelPrimary, modifier = Modifier.size(13.dp))
            }

            Text(
                text = if (step < 1f) String.format("%.2f", value) else value.toInt().toString(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppleLabelPrimary,
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }
    }
}
