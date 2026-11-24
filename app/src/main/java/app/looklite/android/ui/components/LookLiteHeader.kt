package app.looklite.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.ui.theme.*

@Composable
fun LookLiteHeader(
    activePresetName: String,
    onOpenPhoto: () -> Unit,
    onReset: () -> Unit,
    onDiagnostic: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(12.dp, RoundedCornerShape(12.dp), spotColor = Color(0x1A000000))
            .border(0.5.dp, AppleGlassBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = AppleGlassBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Brand & Preset Info
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
                // Open Photo Button
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14000000))
                        .clickable { onOpenPhoto() }
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
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Open",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppleLabelPrimary
                        )
                    }
                }

                // Reset Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14000000))
                        .clickable { onReset() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Reset",
                        tint = AppleLabelPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Bug / Diagnostic Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x14000000))
                        .clickable { onDiagnostic() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.BugReport,
                        contentDescription = "Diagnostics",
                        tint = AppleLabelPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Save Button (Apple Coral Pill)
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppleCoral)
                        .clickable { onSave() }
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
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Save",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
