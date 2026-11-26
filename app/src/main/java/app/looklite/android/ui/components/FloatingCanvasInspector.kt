package app.looklite.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.R
import app.looklite.android.data.PresetCatalog
import app.looklite.android.model.AspectRatioOption
import app.looklite.android.ui.theme.*

@Composable
fun FloatingCanvasInspector(
    selectedRatio: AspectRatioOption,
    onRatioSelected: (AspectRatioOption) -> Unit,
    isRatioStripOpen: Boolean,
    onToggleRatioStrip: () -> Unit,
    onRotate90: () -> Unit,
    isSplitActive: Boolean,
    onToggleSplit: () -> Unit,
    isHoldingOriginal: Boolean,
    onHoldOriginalChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Expandable Inline Aspect Ratio Strip
        AnimatedVisibility(
            visible = isRatioStripOpen,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .background(StudioGlassOverlay)
                    .border(1.dp, StudioGlassBorder, RoundedCornerShape(9999.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PresetCatalog.aspectRatios.forEach { option ->
                    val isSelected = option.id == selectedRatio.id
                    val label = if (option.labelResId != 0) stringResource(option.labelResId) else option.label
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9999.dp))
                            .background(if (isSelected) StudioAccent else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onRatioSelected(option)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) Color.White else StudioTextPrimary
                        )
                    }
                }
            }
        }

        // Translucent Glassmorphic Floating Pill Bar
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(StudioGlassOverlay)
                .border(1.dp, StudioGlassBorder, RoundedCornerShape(9999.dp))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 1. Aspect Ratio Trigger
            val ratioLabel = if (selectedRatio.labelResId != 0) stringResource(selectedRatio.labelResId) else selectedRatio.label
            Row(
                modifier = Modifier
                    .height(26.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(
                        if (isRatioStripOpen || selectedRatio.ratio != null) StudioAccent.copy(alpha = 0.18f)
                        else Color.Transparent
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleRatioStrip()
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Crop,
                    contentDescription = stringResource(R.string.inspector_ratio),
                    tint = if (isRatioStripOpen || selectedRatio.ratio != null) StudioAccent else StudioTextPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = ratioLabel,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isRatioStripOpen || selectedRatio.ratio != null) StudioAccent else StudioTextPrimary
                )
            }

            // Hairline vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(StudioHairlineBorder)
            )

            // 2. Rotate 90° CW
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 26.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRotate90()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.RotateRight,
                    contentDescription = stringResource(R.string.inspector_rotate),
                    tint = StudioTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Hairline vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(StudioHairlineBorder)
            )

            // 3. Split View Toggle
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 26.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(if (isSplitActive) StudioAccent.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleSplit()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Compare,
                    contentDescription = stringResource(R.string.inspector_split),
                    tint = if (isSplitActive) StudioAccent else StudioTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Hairline vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(StudioHairlineBorder)
            )

            // 3. Hold for Original (Eye Icon)
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 26.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(if (isHoldingOriginal) StudioAccent.copy(alpha = 0.22f) else Color.Transparent)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            onHoldOriginalChanged(true)
                            waitForUpOrCancellation()
                            onHoldOriginalChanged(false)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = stringResource(R.string.inspector_before),
                    tint = if (isHoldingOriginal) StudioAccent else StudioTextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
