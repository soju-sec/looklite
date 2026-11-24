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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .systemBarsPadding()
    ) {
        // Center Viewport: Native OpenGL ES 3.0 Surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    containerWidth = size.width.toFloat()
                    containerHeight = size.height.toFloat()
                }
                .pointerInput(isSplitActive) {
                    if (isSplitActive) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val relX = (change.position.x / size.width).coerceIn(0.02f, 0.98f)
                            splitPos = relX
                            renderer.setSplitPosition(relX)
                            glView?.requestRender()
                        }
                    }
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    LookLiteGLView(ctx, renderer).also { glView = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Split View Divider Label Badges
            if (isSplitActive) {
                // Split Line indicator
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .offset(x = (containerWidth * splitPos).dp)
                        .background(Color.White)
                )

                // Sebelum & Sesudah Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 96.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = Color(0xCC000000)
                    ) {
                        Text(
                            text = "SEBELUM",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(9999.dp),
                        color = Color(0xCC007AFF)
                    ) {
                        Text(
                            text = "SESUDAH",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Interactive Crop Overlay
            val imageAspect = originalBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: (4f / 3f)
            CropOverlay(
                containerWidth = containerWidth,
                containerHeight = containerHeight,
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

        // Top UI Layer: Boxy Modern Header & Aspect Ratio Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LookLiteHeader(
                activePresetName = activePreset.name,
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
                    glView?.requestRender()
                },
                onDiagnostic = { showDiagnosticDialog = true },
                onSave = { showExportDialog = true }
            )

            // Ratio Bar directly above image view
            AspectRatioBar(
                selectedRatioId = selectedRatioOption.id,
                onRatioSelected = { option ->
                    selectedRatioOption = option
                    cropNormX = 0.5f
                    cropNormY = 0.5f
                }
            )
        }

        // Bottom UI Layer: Sub-Menu Card + Floating Dock Navigation Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
    val bmp = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    paint.color = android.graphics.Color.parseColor("#2B4C6F")
    canvas.drawRect(0f, 0f, 1200f, 450f, paint)

    paint.color = android.graphics.Color.parseColor("#F4B251")
    canvas.drawRect(0f, 450f, 1200f, 520f, paint)

    paint.color = android.graphics.Color.parseColor("#3A3845")
    val path = android.graphics.Path().apply {
        moveTo(0f, 520f)
        lineTo(350f, 320f)
        lineTo(650f, 420f)
        lineTo(950f, 290f)
        lineTo(1200f, 520f)
        close()
    }
    canvas.drawPath(path, paint)

    paint.color = android.graphics.Color.parseColor("#1F2A30")
    canvas.drawRect(0f, 520f, 1200f, 900f, paint)

    return bmp
}
