package app.looklite.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticDialog(
    activePresetName: String,
    imageDimensions: String,
    intensity: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val timestamp = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) }

    val reportText = remember {
        """
        === LOOKLITE NATIVE DIAGNOSTIC REPORT ===
        Waktu: $timestamp
        Perangkat: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})
        OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, Build ${Build.DISPLAY})
        Engine: Native Kotlin + OpenGL ES 3.0 (Pure Native Android)
        Dimensi Gambar: $imageDimensions
        Preset Aktif: $activePresetName
        Intensitas Filter: $intensity%
        RAM: ${(Runtime.getRuntime().totalMemory() / (1024 * 1024))} MB used
        Status: 100% Native Architecture
        """.trimIndent()
    }

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Diagnostik Aplikasi",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppleLabelPrimary
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E)
            ) {
                Text(
                    text = reportText,
                    color = Color(0xFF4AF626),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                )
            }

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("LookLite Diagnostics", reportText))
                    Toast.makeText(context, "Diagnostik disalin ke clipboard! ✓", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppleCoral)
            ) {
                Text("Salin Diagnostik", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
