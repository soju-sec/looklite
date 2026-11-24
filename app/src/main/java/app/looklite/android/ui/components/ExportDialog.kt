package app.looklite.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.ui.theme.*

enum class ExportResolution(val id: String, val title: String, val desc: String, val maxDimension: Int?) {
    ORIGINAL("original", "Ukuran Asli", "Kualitas penuh tanpa kompresi resolusi", null),
    RES_4K("4k", "4K Ultra HD (3840px)", "Ideal untuk cetak atau layar besar", 3840),
    SOCIAL("social", "Media Sosial (2048px)", "Optimal untuk Instagram, feed, & web", 2048),
    FHD("fhd", "Full HD (1080px)", "Ukuran file lebih hemat", 1080)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    originalWidth: Int,
    originalHeight: Int,
    onDismiss: () -> Unit,
    onConfirmExport: (ExportResolution) -> Unit
) {
    var selectedRes by remember { mutableStateOf(ExportResolution.ORIGINAL) }
    val mp = String.format("%.1f", (originalWidth * originalHeight) / 1000000f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Pilih Resolusi Ekspor",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppleLabelPrimary
            )
            Text(
                text = "Foto disimpan ke Galeri perangkat (Pictures/LookLite).",
                fontSize = 12.sp,
                color = AppleLabelSecondary
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExportResolution.values().forEach { res ->
                    val isSelected = res == selectedRes
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedRes = res },
                        color = if (isSelected) AppleCoral.copy(alpha = 0.08f) else Color(0x08000000),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (res == ExportResolution.ORIGINAL) "${res.title} ($originalWidth × $originalHeight, $mp MP)" else res.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) AppleCoral else AppleLabelPrimary
                                )
                                Text(
                                    text = res.desc,
                                    fontSize = 11.sp,
                                    color = AppleLabelSecondary
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedRes = res },
                                colors = RadioButtonDefaults.colors(selectedColor = AppleCoral)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onConfirmExport(selectedRes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleCoral)
            ) {
                Text(
                    text = "Simpan ke Galeri",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
