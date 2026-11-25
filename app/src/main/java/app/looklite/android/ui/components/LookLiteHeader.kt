package app.looklite.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.R
import app.looklite.android.ui.theme.*

@Composable
fun LookLiteHeader(
    activePresetName: String,
    hasEdits: Boolean,
    onOpenPhoto: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(StudioCanvasBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Open & Reset icon buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioElevatedBg)
                    .border(1.dp, StudioHairlineBorder, RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenPhoto()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = stringResource(R.string.action_open),
                    tint = StudioTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioElevatedBg)
                    .border(1.dp, StudioHairlineBorder, RoundedCornerShape(8.dp))
                    .alpha(if (hasEdits) 1f else 0.35f)
                    .clickable(enabled = hasEdits) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReset()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.action_reset),
                    tint = StudioTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Center: Active preset indicator pill ("● Portra 400")
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(StudioElevatedBg)
                .border(1.dp, StudioHairlineBorder, RoundedCornerShape(9999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(StudioAccent)
            )
            Text(
                text = activePresetName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = StudioTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right: 20.dp Settings gear icon + Compact Export pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenSettings()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    tint = StudioTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(StudioAccent)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExport()
                    }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.action_export),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}
