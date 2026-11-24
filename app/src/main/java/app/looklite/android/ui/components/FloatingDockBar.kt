package app.looklite.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.ui.theme.*

@Composable
fun FloatingDockBar(
    currentMode: ActiveMode,
    isSplitActive: Boolean,
    onModeSelected: (ActiveMode) -> Unit,
    onToggleSplit: () -> Unit,
    onHoldBeforeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(16.dp, RoundedCornerShape(9999.dp), spotColor = Color(0x24000000))
            .border(0.5.dp, AppleGlassBorder, RoundedCornerShape(9999.dp)),
        shape = RoundedCornerShape(9999.dp),
        color = AppleGlassBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            // Presets Tab
            DockTabItem(
                label = "Presets",
                icon = Icons.Rounded.AutoAwesome,
                isActive = currentMode == ActiveMode.PRESETS && !isSplitActive,
                onClick = { onModeSelected(ActiveMode.PRESETS) }
            )

            // Fine-Tune Tab
            DockTabItem(
                label = "Fine-Tune",
                icon = Icons.Rounded.Tune,
                isActive = currentMode == ActiveMode.ADJUST && !isSplitActive,
                onClick = { onModeSelected(ActiveMode.ADJUST) }
            )

            // Split View Tab
            DockTabItem(
                label = "Split View",
                icon = Icons.Rounded.Compare,
                isActive = isSplitActive,
                onClick = { onToggleSplit() }
            )

            // Before Tab (Hold to view original)
            var isHolding by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(9999.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            isHolding = true
                            onHoldBeforeChanged(true)
                            waitForUpOrCancellation()
                            isHolding = false
                            onHoldBeforeChanged(false)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = "Before",
                        tint = if (isHolding) AppleCoral else AppleLabelTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Before",
                        fontSize = 10.sp,
                        fontWeight = if (isHolding) FontWeight.Bold else FontWeight.Medium,
                        color = if (isHolding) AppleCoral else AppleLabelTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.DockTabItem(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(9999.dp)),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) AppleCoral else AppleLabelTertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) AppleCoral else AppleLabelTertiary
            )
        }
    }
}
