package app.looklite.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.data.PresetCatalog
import app.looklite.android.model.AspectRatioOption
import app.looklite.android.ui.theme.*

@Composable
fun AspectRatioBar(
    selectedRatioId: String,
    onRatioSelected: (AspectRatioOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(12.dp), spotColor = Color(0x10000000))
            .border(0.5.dp, AppleGlassBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = AppleGlassBg
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PresetCatalog.aspectRatios.forEach { option ->
                val isActive = option.id == selectedRatioId

                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive) Color.White else Color.Transparent)
                        .then(
                            if (isActive) Modifier.shadow(4.dp, RoundedCornerShape(8.dp), spotColor = Color(0x1F000000))
                            else Modifier
                        )
                        .clickable { onRatioSelected(option) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        option.badge?.let { badge ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isActive) AppleCoral.copy(alpha = 0.12f) else Color(0x1A000000))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = badge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) AppleCoral else AppleLabelSecondary
                                )
                            }
                        }
                        Text(
                            text = option.label,
                            fontSize = 11.5.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isActive) AppleCoral else AppleLabelSecondary
                        )
                    }
                }
            }
        }
    }
}
