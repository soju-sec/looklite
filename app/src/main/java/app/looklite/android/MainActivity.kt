package app.looklite.android

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import app.looklite.android.data.PresetCatalog
import app.looklite.android.engine.LookLiteGLView
import app.looklite.android.engine.LookLiteRenderer
import app.looklite.android.engine.LutLoader
import app.looklite.android.model.*
import app.looklite.android.ui.components.*
import app.looklite.android.ui.theme.LookLiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive edge-to-edge UI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        setContent {
            LookLiteTheme {
                LookLiteApp()
            }
        }
    }
}

@Composable
fun LookLiteApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val presets = remember { PresetCatalog.getAllPresets(context) }
    var activePreset by remember { mutableStateOf(presets.firstOrNull() ?: PresetProfile("default", "Standard", CameraBrand.POLES, "", "")) }
    var selectedBrand by remember { mutableStateOf(activePreset.brand) }
    var intensity by remember { mutableIntStateOf(100) }
    var currentSliders by remember { mutableStateOf(activePreset.defaultSliders) }

    var currentMode by remember { mutableStateOf(ActiveMode.PRESETS) }
    var isSubMenuCollapsed by remember { mutableStateOf(false) }

    var isSplitActive by remember { mutableStateOf(false) }
    var splitPos by remember { mutableFloatStateOf(0.5f) }
    var isAutoColorEnabled by remember { mutableStateOf(false) }

    var selectedRatioOption by remember { mutableStateOf(PresetCatalog.aspectRatios.first()) }
    var cropNormX by remember { mutableFloatStateOf(0.5f) }
    var cropNormY by remember { mutableFloatStateOf(0.5f) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }

    val renderer = remember { LookLiteRenderer(context) }
    var glView by remember { mutableStateOf<LookLiteGLView?>(null) }

    // Initialize with default demo image
    LaunchedEffect(Unit) {
        val demo = createDemoBitmap()
        originalBitmap = demo
        renderer.setImageBitmap(demo)
        // Load initial LUT
        val lutBuf = LutLoader.loadLut(context, activePreset.lutFile)
        renderer.setLut(lutBuf, 17, activePreset.lutStage)
        renderer.setSliders(activePreset.defaultSliders)
        renderer.setIntensity(100)
        glView?.requestRender()
    }

    // Photo picker launcher
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val bmp = loadBitmapWithExif(context, uri)
                if (bmp != null) {
                    originalBitmap = bmp
                    renderer.setImageBitmap(bmp)
                    cropNormX = 0.5f
                    cropNormY = 0.5f
                    glView?.requestRender()
                    Toast.makeText(context, "Foto dimuat (${bmp.width} × ${bmp.height})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Gagal membaca format foto", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun applyPreset(preset: PresetProfile) {
        activePreset = preset
        selectedBrand = preset.brand
        intensity = 100
        currentSliders = preset.defaultSliders
        val buf = LutLoader.loadLut(context, preset.lutFile)
        renderer.setLut(buf, 17, preset.lutStage)
        renderer.setSliders(preset.defaultSliders)
        renderer.setIntensity(100)
        glView?.requestRender()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF6F7FB), Color(0xFFEAEEF6))
                )
            )
            .systemBarsPadding()
    ) {
        // 1. Top UI Layer: Unified Modern Header & Vibe Prompts Aspect Ratio Bar
        LookLiteHeader(
            activePresetName = activePreset.name,
            selectedRatioId = selectedRatioOption.id,
            onRatioSelected = { option ->
                selectedRatioOption = option
                cropNormX = 0.5f
                cropNormY = 0.5f
            },
            onOpenPhoto = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onReset = {
                intensity = 100
                currentSliders = activePreset.defaultSliders
                renderer.setSliders(activePreset.defaultSliders)
                renderer.setIntensity(100)
                isSplitActive = false
                renderer.setSplitPosition(-1f)
                isAutoColorEnabled = false
                renderer.setAutoColor(false)
                selectedRatioOption = PresetCatalog.aspectRatios.first()
                cropNormX = 0.5f
                cropNormY = 0.5f
                glView?.requestRender()
            },
            onDiagnostic = { showDiagnosticDialog = true },
            onSave = { showExportDialog = true },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )

        // 2. Center Viewport Canvas (Proportionally fitted, never stretched or hidden under controls)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .onSizeChanged { size ->
                    containerWidth = size.width.toFloat()
                    containerHeight = size.height.toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val imageAspect = originalBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (4f / 3f)

            if (containerWidth > 0 && containerHeight > 0) {
                var fitPxW = containerWidth
                var fitPxH = fitPxW / imageAspect
                if (fitPxH > containerHeight) {
                    fitPxH = containerHeight
                    fitPxW = fitPxH * imageAspect
                }

                val fitWdp = with(density) { fitPxW.toDp() }
                val fitHdp = with(density) { fitPxH.toDp() }

                Box(
                    modifier = Modifier
                        .size(width = fitWdp, height = fitHdp)
                        .shadow(12.dp, RoundedCornerShape(8.dp), spotColor = Color(0x33000000))
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .pointerInput(isSplitActive, fitPxW) {
                            if (isSplitActive) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    val relX = (change.position.x / fitPxW).coerceIn(0.02f, 0.98f)
                                    splitPos = relX
                                    renderer.setSplitPosition(relX)
                                    glView?.requestRender()
                                }
                            }
                        }
                ) {
                    // Native OpenGL ES 3.0 Viewport
                    AndroidView(
                        factory = { ctx ->
                            LookLiteGLView(ctx, renderer).also { glView = it }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Interactive Split View
                    if (isSplitActive) {
                        val splitXdp = with(density) { (fitPxW * splitPos).toDp() }

                        // White Divider Line
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.5.dp)
                                .offset(x = splitXdp - 0.75.dp)
                                .background(Color.White)
                        )

                        // Center Circle Handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = splitXdp - 12.dp)
                                .size(24.dp)
                                .shadow(4.dp, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "‹›",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Sebelum & Sesudah Badges inside photo corners
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = Color(0xCC000000)
                            ) {
                                Text(
                                    text = "SEBELUM",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(9999.dp),
                                color = Color(0xCC007AFF)
                            ) {
                                Text(
                                    text = "SESUDAH",
                                    color = Color.White,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Interactive Crop Framing Overlay
                    CropOverlay(
                        containerWidth = fitPxW,
                        containerHeight = fitPxH,
                        imageAspect = imageAspect,
                        targetAspect = selectedRatioOption.ratio,
                        normX = cropNormX,
                        normY = cropNormY,
                        onNormOffsetChanged = { nx, ny ->
                            cropNormX = nx
                            cropNormY = ny
                        }
                    )
                }
            }
        }

        // 3. Bottom Controls Layer: Sub-Menu Card + Floating Dock Navigation Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Sub-Menu Controls Card (Separated from bottom navigation)
            SubMenuCard(
                mode = currentMode,
                isCollapsed = isSubMenuCollapsed || isSplitActive,
                onToggleCollapse = { isSubMenuCollapsed = !isSubMenuCollapsed },
                selectedBrand = selectedBrand,
                onBrandSelected = { brand ->
                    selectedBrand = brand
                    // Switch to first preset of this brand
                    presets.firstOrNull { it.brand == brand }?.let { applyPreset(it) }
                },
                presets = presets,
                selectedPresetId = activePreset.id,
                onPresetSelected = { preset -> applyPreset(preset) },
                intensity = intensity,
                onIntensityChanged = { newIntensity ->
                    intensity = newIntensity
                    renderer.setIntensity(newIntensity)
                    glView?.requestRender()
                },
                isAutoColorEnabled = isAutoColorEnabled,
                onToggleAutoColor = { enabled ->
                    isAutoColorEnabled = enabled
                    renderer.setAutoColor(enabled)
                    glView?.requestRender()
                },
                sliders = currentSliders,
                onSliderChanged = { newSliders ->
                    currentSliders = newSliders
                    renderer.setSliders(newSliders)
                    glView?.requestRender()
                }
            )

            // Floating Navigation Pill Bar
            FloatingDockBar(
                currentMode = currentMode,
                isSplitActive = isSplitActive,
                onModeSelected = { mode ->
                    if (isSplitActive) {
                        isSplitActive = false
                        renderer.setSplitPosition(-1f)
                        glView?.requestRender()
                    }
                    if (currentMode == mode) {
                        isSubMenuCollapsed = !isSubMenuCollapsed
                    } else {
                        currentMode = mode
                        isSubMenuCollapsed = false
                    }
                },
                onToggleSplit = {
                    isSplitActive = !isSplitActive
                    renderer.setSplitPosition(if (isSplitActive) splitPos else -1f)
                    glView?.requestRender()
                },
                onHoldBeforeChanged = { holding ->
                    renderer.setShowBefore(holding)
                    glView?.requestRender()
                }
            )
        }
    }

    // Export Dialog
    if (showExportDialog) {
        val bmp = originalBitmap
        ExportDialog(
            originalWidth = bmp?.width ?: 1200,
            originalHeight = bmp?.height ?: 900,
            onDismiss = { showExportDialog = false },
            onConfirmExport = { resolution ->
                showExportDialog = false
                scope.launch {
                    executeNativeExport(
                        context = context,
                        sourceBitmap = originalBitmap ?: createDemoBitmap(),
                        targetRatio = selectedRatioOption.ratio,
                        normX = cropNormX,
                        normY = cropNormY,
                        resolution = resolution,
                        renderer = renderer,
                        presetName = activePreset.name
                    )
                }
            }
        )
    }

    // Diagnostic Dialog
    if (showDiagnosticDialog) {
        val bmp = originalBitmap
        DiagnosticDialog(
            activePresetName = "${activePreset.name} (${activePreset.brand.displayName})",
            imageDimensions = "${bmp?.width ?: 0} × ${bmp?.height ?: 0}",
            intensity = intensity,
            onDismiss = { showDiagnosticDialog = false }
        )
    }
}

private suspend fun executeNativeExport(
    context: Context,
    sourceBitmap: Bitmap,
    targetRatio: Float?,
    normX: Float,
    normY: Float,
    resolution: ExportResolution,
    renderer: LookLiteRenderer,
    presetName: String
) = withContext(Dispatchers.IO) {
    try {
        val origW = sourceBitmap.width
        val origH = sourceBitmap.height

        // Calculate Crop Rect
        val cropX: Int
        val cropY: Int
        val cropW: Int
        val cropH: Int

        if (targetRatio != null) {
            val imgAspect = origW.toFloat() / origH.toFloat()
            if (imgAspect > targetRatio) {
                cropH = origH
                cropW = (cropH * targetRatio).toInt().coerceAtMost(origW)
                val maxShift = origW - cropW
                cropX = (maxShift * normX).toInt().coerceIn(0, maxShift)
                cropY = 0
            } else {
                cropW = origW
                cropH = (cropW / targetRatio).toInt().coerceAtMost(origH)
                val maxShift = origH - cropH
                cropX = 0
                cropY = (maxShift * normY).toInt().coerceIn(0, maxShift)
            }
        } else {
            cropX = 0
            cropY = 0
            cropW = origW
            cropH = origH
        }

        val croppedBitmap = Bitmap.createBitmap(sourceBitmap, cropX, cropY, cropW, cropH)

        // Calculate Target Export Dimensions
        var targetW = cropW
        var targetH = cropH
        val aspect = cropW.toFloat() / cropH.toFloat()

        resolution.maxDimension?.let { maxDim ->
            if (cropW >= cropH) {
                targetW = maxDim.coerceAtMost(cropW)
                targetH = (targetW / aspect).toInt()
            } else {
                targetH = maxDim.coerceAtMost(cropH)
                targetW = (targetH * aspect).toInt()
            }
        }

        // Render with Native OpenGL ES 3.0 Offscreen FBO
        val renderedBitmap = renderer.renderOffscreen(croppedBitmap, targetW, targetH)

        // Save to Android MediaStore
        val filename = "LookLite_${presetName.replace("\\s+".toRegex(), "_")}_${System.currentTimeMillis()}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LookLite")
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                renderedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Disimpan ke Galeri ($targetW × $targetH)! ✓", Toast.LENGTH_SHORT).show()
            }
        } else {
            throw RuntimeException("Gagal mengalokasikan file MediaStore")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Gagal mengekspor foto: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun loadBitmapWithExif(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream) ?: return null
        inputStream.close()

        val exifStream = context.contentResolver.openInputStream(uri) ?: return original
        val exif = ExifInterface(exifStream)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        exifStream.close()

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return original
        }
        Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun createDemoBitmap(): Bitmap {
    val w = 1200
    val h = 900
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Luminous Golden-Hour Sky Gradient
    paint.shader = android.graphics.LinearGradient(
        0f, 0f, 0f, 540f,
        intArrayOf(
            android.graphics.Color.parseColor("#4A85C4"),
            android.graphics.Color.parseColor("#88B4DC"),
            android.graphics.Color.parseColor("#F5CA94"),
            android.graphics.Color.parseColor("#FFE5B8")
        ),
        floatArrayOf(0f, 0.42f, 0.80f, 1f),
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w.toFloat(), 540f, paint)

    // 2. Soft Sun Glow
    paint.shader = android.graphics.RadialGradient(
        820f, 350f, 340f,
        intArrayOf(
            android.graphics.Color.parseColor("#FFFFF4D6"),
            android.graphics.Color.parseColor("#88FFD78A"),
            android.graphics.Color.parseColor("#00FFD78A")
        ),
        floatArrayOf(0f, 0.35f, 1f),
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawCircle(820f, 350f, 340f, paint)

    // 3. Distant Atmospheric Mountain Ridge
    paint.shader = android.graphics.LinearGradient(
        0f, 270f, 0f, 540f,
        android.graphics.Color.parseColor("#8A96B5"),
        android.graphics.Color.parseColor("#B8B5C8"),
        android.graphics.Shader.TileMode.CLAMP
    )
    val farMountain = android.graphics.Path().apply {
        moveTo(0f, 540f)
        lineTo(0f, 390f)
        lineTo(220f, 310f)
        lineTo(470f, 405f)
        lineTo(740f, 285f)
        lineTo(1010f, 375f)
        lineTo(1200f, 325f)
        lineTo(1200f, 540f)
        close()
    }
    canvas.drawPath(farMountain, paint)

    // 4. Foreground Mountain Ridge
    paint.shader = android.graphics.LinearGradient(
        0f, 340f, 0f, 540f,
        android.graphics.Color.parseColor("#556078"),
        android.graphics.Color.parseColor("#3E4658"),
        android.graphics.Shader.TileMode.CLAMP
    )
    val nearMountain = android.graphics.Path().apply {
        moveTo(0f, 540f)
        lineTo(0f, 455f)
        lineTo(310f, 360f)
        lineTo(620f, 465f)
        lineTo(940f, 350f)
        lineTo(1200f, 450f)
        lineTo(1200f, 540f)
        close()
    }
    canvas.drawPath(nearMountain, paint)

    // 5. Reflective Alpine Lake Water
    paint.shader = android.graphics.LinearGradient(
        0f, 540f, 0f, h.toFloat(),
        intArrayOf(
            android.graphics.Color.parseColor("#E6BA8B"),
            android.graphics.Color.parseColor("#7E92AE"),
            android.graphics.Color.parseColor("#475D7A"),
            android.graphics.Color.parseColor("#2B3B50")
        ),
        floatArrayOf(0f, 0.28f, 0.65f, 1f),
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 540f, w.toFloat(), h.toFloat(), paint)

    // 6. Subtle Sun Reflection Shimmer on Water
    paint.shader = android.graphics.RadialGradient(
        820f, 620f, 260f,
        intArrayOf(
            android.graphics.Color.parseColor("#AAFFE6B0"),
            android.graphics.Color.parseColor("#00FFE6B0")
        ),
        null,
        android.graphics.Shader.TileMode.CLAMP
    )
    canvas.drawOval(560f, 545f, 1080f, 760f, paint)
    paint.shader = null

    return bmp
}
