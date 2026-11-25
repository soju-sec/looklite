package app.looklite.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.R
import app.looklite.android.ui.theme.*

enum class ExportResolution(
    val id: String,
    val titleResId: Int,
    val descResId: Int,
    val maxDimension: Int?
) {
    ORIGINAL("original", R.string.export_res_original, R.string.export_res_original_desc, null),
    RES_4K("4k", R.string.export_res_4k, R.string.export_res_4k_desc, 3840),
    SOCIAL("social", R.string.export_res_social, R.string.export_res_social_desc, 2048),
    FHD("fhd", R.string.export_res_fhd, R.string.export_res_fhd_desc, 1080)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    originalWidth: Int,
    originalHeight: Int,
    onDismiss: () -> Unit,
    onConfirmExport: (ExportResolution) -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.export_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StudioTextPrimary
                    )
                    Text(
                        text = stringResource(R.string.export_subtitle),
                        fontSize = 12.sp,
                        color = StudioTextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioElevatedBg)
                        .border(1.dp, StudioHairlineBorder, RoundedCornerShape(8.dp))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.diag_close),
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            ExportResolution.values().forEach { res ->
                val aspect = originalWidth.toFloat() / originalHeight.coerceAtLeast(1).toFloat()
                val estDim = res.maxDimension?.let { maxDim ->
                    if (originalWidth >= originalHeight) {
                        val w = maxDim.coerceAtMost(originalWidth)
                        val h = (w / aspect).toInt()
                        "$w × $h"
                    } else {
                        val h = maxDim.coerceAtMost(originalHeight)
                        val w = (h * aspect).toInt()
                        "$w × $h"
                    }
                } ?: "$originalWidth × $originalHeight"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioElevatedBg)
                        .border(1.dp, StudioHairlineBorder, RoundedCornerShape(10.dp))
                        .clickable { onConfirmExport(res) }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(res.titleResId),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StudioTextPrimary
                        )
                        Text(
                            text = stringResource(res.descResId),
                            fontSize = 11.5.sp,
                            color = StudioTextSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = estDim,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StudioAccent,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = StudioAccent,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}
