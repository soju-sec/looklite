package app.looklite.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.data.PresetCatalog
import app.looklite.android.model.AspectRatioOption
import app.looklite.android.ui.theme.*

/**
 * Unified Top Bar combining Brand Header and Vibe-style Aspect Ratio Selector
 * separated by a sleek hairline divider.
 */
@Composable
fun LookLiteHeader(
    activePresetName: String,
    selectedRatioId: String,
    onRatioSelected: (AspectRatioOption) -> Unit,
    onOpenPhoto: () -> Unit,
    onReset: () -> Unit,
    onDiagnostic: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(14.dp), spotColor = Color(0x18000000))
            .border(0.5.dp, AppleGlassBorder, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = AppleGlassBg
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Top Row: App Branding + Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Brand Title & Preset Info
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "LookLite",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppleLabelPrimary,
                        letterSpacing = (-0.2).sp
                    )
                    Text(
                        text = activePresetName,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppleCoral,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Open Photo
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x12000000))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenPhoto()
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Image,
                                contentDescription = "Open",
                                tint = AppleLabelPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Open",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppleLabelPrimary
                            )
                        }
                    }

                    // Reset
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x12000000))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onReset()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Reset",
                            tint = AppleLabelPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Diagnostics / Bug
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x12000000))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDiagnostic()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.BugReport,
                            contentDescription = "Diagnostics",
                            tint = AppleLabelPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Save / Export
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppleCoral)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSave()
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "Save",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Save",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Slender Hairline Divider
            HorizontalDivider(
                color = Color(0x14000000),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Bottom Row: Vibe Prompts Enhanced Aspect Ratio Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PresetCatalog.aspectRatios.forEach { option ->
                    val isActive = option.id == selectedRatioId
                    val chipBg by animateColorAsState(
                        targetValue = if (isActive) Color.White else Color.Transparent,
                        animationSpec = tween(150),
                        label = "chipBg"
                    )

                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(chipBg)
                            .then(
                                if (isActive) Modifier
                                    .shadow(4.dp, RoundedCornerShape(8.dp), spotColor = Color(0x1A000000))
                                    .border(0.5.dp, Color(0x12000000), RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRatioSelected(option)
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Geometric Aspect Ratio Wireframe Glyph
                            AspectRatioGlyph(
                                id = option.id,
                                isActive = isActive
                            )

                            // Numerical Ratio Badge
                            option.badge?.let { badge ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isActive) AppleCoral.copy(alpha = 0.12f) else Color(0x12000000))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = badge,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isActive) AppleCoral else AppleLabelSecondary
                                    )
                                }
                            }

                            // Human Readable Label (Asli, Persegi, Feed IG, Story, Cinema, Foto)
                            Text(
                                text = option.label,
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                color = if (isActive) AppleCoral else AppleLabelSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Geometric Aspect Ratio Wireframe Glyph representing the proportions visually
 */
@Composable
private fun AspectRatioGlyph(
    id: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val strokeColor = if (isActive) AppleCoral else Color(0xFF8E8E93)
    val strokeWidth = 1.25.dp

    Box(
        modifier = modifier.size(15.dp),
        contentAlignment = Alignment.Center
    ) {
        when (id) {
            "original" -> {
                Icon(
                    Icons.Rounded.CropFree,
                    contentDescription = null,
                    tint = strokeColor,
                    modifier = Modifier.size(13.dp)
                )
            }
            "1:1" -> {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .border(strokeWidth, strokeColor, RoundedCornerShape(1.5.dp))
                )
            }
            "4:5" -> {
                Box(
                    modifier = Modifier
                        .size(width = 9.dp, height = 11.5.dp)
                        .border(strokeWidth, strokeColor, RoundedCornerShape(1.5.dp))
                )
            }
            "9:16" -> {
                Box(
                    modifier = Modifier
                        .size(width = 7.dp, height = 13.dp)
                        .border(strokeWidth, strokeColor, RoundedCornerShape(1.5.dp))
                )
            }
            "16:9" -> {
                Box(
                    modifier = Modifier
                        .size(width = 13.dp, height = 7.5.dp)
                        .border(strokeWidth, strokeColor, RoundedCornerShape(1.5.dp))
                )
            }
            "3:2" -> {
                Box(
                    modifier = Modifier
                        .size(width = 12.dp, height = 8.5.dp)
                        .border(strokeWidth, strokeColor, RoundedCornerShape(1.5.dp))
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .border(strokeWidth, strokeColor, RoundedCornerShape(1.5.dp))
                )
            }
        }
    }
}
