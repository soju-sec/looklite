package app.looklite.android.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.R
import app.looklite.android.model.AppLanguageOption
import app.looklite.android.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    selectedLanguage: AppLanguageOption,
    onLanguageSelected: (AppLanguageOption) -> Unit,
    isDeveloperUnlocked: Boolean,
    onUnlockDeveloper: () -> Unit,
    gpuFrameTimeMs: Float,
    glRendererName: String,
    activePresetName: String,
    imageDimensions: String,
    onOpenDebugConsole: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var versionTapCount by remember { mutableIntStateOf(0) }

    val versionName = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }
    val versionCode = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (_: Exception) {
            1
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = StudioWorkbenchBg,
        contentColor = StudioTextPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Text(
                text = stringResource(R.string.settings_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = StudioTextPrimary
            )

            // App Language Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_language),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = StudioTextSecondary
                    )
                }

                // 3-Option Segmented Selector ("System", "English", "Indonesia")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioElevatedBg)
                        .border(1.dp, StudioHairlineBorder, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val options = listOf(
                        AppLanguageOption.SYSTEM to stringResource(R.string.lang_system),
                        AppLanguageOption.ENGLISH to stringResource(R.string.lang_english),
                        AppLanguageOption.INDONESIAN to stringResource(R.string.lang_indonesian)
                    )
                    options.forEach { (option, label) ->
                        val isSelected = option == selectedLanguage
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) StudioAccent else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onLanguageSelected(option)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) Color.White else StudioTextSecondary
                            )
                        }
                    }
                }
            }

            // Developer Diagnostics Section (revealed after 5 taps on version string)
            AnimatedVisibility(visible = isDeveloperUnlocked) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioElevatedBg)
                        .border(1.dp, StudioHairlineBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_dev_title),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StudioAccent
                    )

                    MetricRow(
                        label = stringResource(R.string.settings_dev_gpu_label),
                        value = "${String.format("%.2f", gpuFrameTimeMs)} ms ($glRendererName)"
                    )
                    MetricRow(
                        label = stringResource(R.string.settings_dev_lut_label),
                        value = "17³ RGB8 · $activePresetName"
                    )
                    MetricRow(
                        label = stringResource(R.string.settings_dev_res_label),
                        value = imageDimensions
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioWorkbenchBg)
                            .border(1.dp, StudioHairlineBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenDebugConsole()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.BugReport,
                                contentDescription = null,
                                tint = StudioTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_dev_open_console),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = StudioTextPrimary
                            )
                        }
                    }
                }
            }

            // App Version Footer (Tap 5 times to unlock Developer Diagnostics)
            val unlockedToast = stringResource(R.string.toast_dev_unlocked)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isDeveloperUnlocked) {
                            versionTapCount++
                            if (versionTapCount >= 5) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onUnlockDeveloper()
                                Toast.makeText(context, unlockedToast, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_version_format, versionName, versionCode),
                    fontSize = 11.5.sp,
                    color = StudioTextSecondary
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = StudioTextSecondary
        )
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = StudioTextPrimary,
            style = TextStyle(fontFeatureSettings = "tnum")
        )
    }
}
