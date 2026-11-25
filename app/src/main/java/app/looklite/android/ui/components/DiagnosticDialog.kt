package app.looklite.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.looklite.android.R
import app.looklite.android.ui.theme.*

@Composable
fun DiagnosticDialog(
    activePresetName: String,
    imageDimensions: String,
    intensity: Int,
    gpuFrameTimeMs: Float,
    glRendererName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val copiedMsg = stringResource(R.string.diag_copied)
    val report = """
        === LOOKLITE DIAGNOSTIC REPORT ===
        Engine: Native OpenGL ES 3.0 ($glRendererName)
        Frame Time: ${String.format("%.2f", gpuFrameTimeMs)} ms
        Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT})
        Active Preset: $activePresetName
        Intensity: $intensity%
        Image Dimensions: $imageDimensions
        LUT Engine: 3D Trilinear Hardware (17³ RGB8)
    """.trimIndent()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioWorkbenchBg)
                .border(1.dp, StudioHairlineBorder, RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.diag_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = StudioTextPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioCanvasBg)
                    .border(1.dp, StudioHairlineBorder, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = report,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = StudioTextPrimary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioElevatedBg)
                        .border(1.dp, StudioHairlineBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("LookLite Report", report))
                            Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.diag_copy),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = StudioTextPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(StudioAccent)
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.diag_close),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
