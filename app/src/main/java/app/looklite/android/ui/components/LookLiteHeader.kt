package app.looklite.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    hasPhoto: Boolean,
    isRawPhoto: Boolean,
    hasEdits: Boolean,
    onOpenGallery: () -> Unit,
    onOpenRaw: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showOpenMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(StudioCanvasBg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Apple-style circular icon buttons (Open & Reset)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(StudioElevatedBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showOpenMenu = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.action_open),
                        tint = StudioTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showOpenMenu,
                    onDismissRequest = { showOpenMenu = false },
                    modifier = Modifier.background(StudioElevatedBg)
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                tint = StudioTextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.action_open_gallery),
                                color = StudioTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            showOpenMenu = false
                            onOpenGallery()
                        }
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.InsertDriveFile,
                                contentDescription = null,
                                tint = StudioAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.action_open_raw),
                                color = StudioTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        onClick = {
                            showOpenMenu = false
                            onOpenRaw()
                        }
                    )
                }
            }

            val resetEnabled = hasPhoto && hasEdits
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (resetEnabled) StudioElevatedBg else StudioElevatedBg.copy(alpha = 0.45f))
                    .clickable(enabled = resetEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onReset()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.action_reset),
                    tint = if (resetEnabled) StudioTextPrimary else StudioTextSecondary.copy(alpha = 0.45f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // Center: Clean Apple-style preset pill (shows "LookLite" when no photo loaded)
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(if (hasPhoto) StudioElevatedBg else Color.Transparent)
                .then(
                    if (hasPhoto) Modifier.border(0.5.dp, StudioHairlineBorder, RoundedCornerShape(9999.dp))
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hasPhoto) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(StudioAccent)
                )
            }
            Text(
                text = if (hasPhoto) activePresetName else stringResource(R.string.app_name),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = StudioTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasPhoto && isRawPhoto) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(StudioAccent.copy(alpha = 0.2f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "RAW",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = StudioAccent
                    )
                }
            }
        }

        // Right: 20.dp Settings gear icon + Apple-style Export capsule
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
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
                    .height(30.dp)
                    .clip(RoundedCornerShape(9999.dp))
                    .background(if (hasPhoto) StudioAccent else StudioElevatedBg)
                    .clickable(enabled = hasPhoto) {
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
                    letterSpacing = (-0.1).sp,
                    color = if (hasPhoto) Color.White else StudioTextSecondary.copy(alpha = 0.55f)
                )
            }
        }
    }
}
