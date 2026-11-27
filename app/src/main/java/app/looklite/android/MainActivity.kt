package app.looklite.android

import android.app.LocaleManager
import android.content.ContentValues
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.LocaleList
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.exifinterface.media.ExifInterface
import app.looklite.android.data.PresetCatalog
import app.looklite.android.data.StudioPreferences
import app.looklite.android.engine.LookLiteGLView
import app.looklite.android.engine.LookLiteRenderer
import app.looklite.android.engine.LutLoader
import app.looklite.android.engine.PresetThumbnailGenerator
import app.looklite.android.engine.RawImageDecoder
import app.looklite.android.model.*
import app.looklite.android.ui.components.*
import app.looklite.android.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive edge-to-edge Darkroom Studio UI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

        setContent {
            LookLiteTheme {
                LookLiteLocalizedRoot()
            }
        }
    }
}

@Composable
private fun LookLiteLocalizedRoot() {
    val baseContext = LocalContext.current
    val prefs = remember { StudioPreferences(baseContext.applicationContext) }
    val selectedLang by prefs.languageFlow.collectAsState(initial = AppLanguageOption.SYSTEM)
    val scope = rememberCoroutineScope()

    val localizedContext = remember(baseContext, selectedLang) {
        if (selectedLang == AppLanguageOption.SYSTEM) {
            baseContext
        } else {
            val locale = Locale(selectedLang.tag)
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            baseContext.createConfigurationContext(config)
        }
    }

    val activityRegistryOwner = baseContext as? ActivityResultRegistryOwner
    val providers = remember(localizedContext, activityRegistryOwner) {
        buildList {
            add(LocalContext provides localizedContext)
            if (activityRegistryOwner != null) {
                add(LocalActivityResultRegistryOwner provides activityRegistryOwner)
            }
        }.toTypedArray()
    }

    CompositionLocalProvider(*providers) {
        LookLiteApp(
            prefs = prefs,
            selectedLanguage = selectedLang,
            onChangeLanguage = { option ->
                scope.launch {
                    prefs.setLanguage(option)
                }
                applyAppLocale(baseContext, option)
            }
        )
    }
}

private fun applyAppLocale(context: Context, option: AppLanguageOption) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val lm = context.getSystemService(LocaleManager::class.java)
        lm?.applicationLocales = if (option == AppLanguageOption.SYSTEM) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(option.tag)
        }
    } else {
        val locales = if (option == AppLanguageOption.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(option.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

@Composable
fun LookLiteApp(
    prefs: StudioPreferences,
    selectedLanguage: AppLanguageOption,
    onChangeLanguage: (AppLanguageOption) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val presets = remember { PresetCatalog.getAllPresets(context) }
    var activePreset by remember {
        mutableStateOf(
            presets.firstOrNull { it.brand == CameraBrand.KODAK }
                ?: presets.firstOrNull()
                ?: PresetProfile("default", "Standard", CameraBrand.KODAK, "", "")
        )
    }
    var activeCustomLookId by remember { mutableStateOf<String?>(null) }
    var activeLabel by remember { mutableStateOf(activePreset.name) }

    var selectedCategory by remember { mutableStateOf(LookCategory.KODAK) }
    var activeWorkbenchTab by remember { mutableStateOf(WorkbenchTab.LOOKS) }

    var intensity by remember { mutableIntStateOf(50) }
    var currentSliders by remember { mutableStateOf(activePreset.defaultSliders) }
    var baseSlidersForReset by remember { mutableStateOf(activePreset.defaultSliders) }
    var isSplitActive by remember { mutableStateOf(false) }
    var splitPos by remember { mutableFloatStateOf(0.5f) }
    var isHoldingOriginal by remember { mutableStateOf(false) }
    var isRatioStripOpen by remember { mutableStateOf(false) }

    var selectedRatioOption by remember { mutableStateOf(PresetCatalog.aspectRatios.first()) }
    var cropNormX by remember { mutableFloatStateOf(0.5f) }
    var cropNormY by remember { mutableFloatStateOf(0.5f) }

    val customLooks by prefs.customLooksFlow.collectAsState(initial = emptyList())

    var showExportDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var isDeveloperUnlocked by remember { mutableStateOf(false) }

    // Name input dialogs state
    var showSaveLookDialog by remember { mutableStateOf(false) }
    var renamingLook by remember { mutableStateOf<CustomLook?>(null) }

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRawPhoto by remember { mutableStateOf(false) }
    var baseThumbPixels by remember { mutableStateOf<IntArray?>(null) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }

    val renderer = remember { LookLiteRenderer(context) }
    var glView by remember { mutableStateOf<LookLiteGLView?>(null) }

    // Pre-load initial LUT so it's ready as soon as user opens a photo
    LaunchedEffect(Unit) {
        val loaded = LutLoader.loadLut(context, activePreset.lutFile)
        renderer.setLut(loaded.buffer, loaded.size, activePreset.lutStage)
        renderer.setSliders(activePreset.defaultSliders)
        renderer.setIntensity(50)
    }

    fun handleSelectedUri(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val decoded = withContext(Dispatchers.IO) { RawImageDecoder.decode(context, uri) }
            if (decoded != null) {
                val bmp = decoded.bitmap
                originalBitmap = bmp
                isRawPhoto = decoded.isRaw
                renderer.setImageBitmap(bmp)
                cropNormX = 0.5f
                cropNormY = 0.5f
                glView?.requestRender()
                baseThumbPixels = withContext(Dispatchers.Default) {
                    PresetThumbnailGenerator.createBaseThumbnail(bmp)
                }
                val msgRes = if (decoded.isRaw) R.string.toast_loaded_raw else R.string.toast_loaded
                Toast.makeText(
                    context,
                    context.getString(msgRes, bmp.width, bmp.height),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_load_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Standard Gallery Photo Picker
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> handleSelectedUri(uri) }

    // System Document Picker for RAW / DNG / All Images
    val rawPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> handleSelectedUri(uri) }

    fun applyPreset(preset: PresetProfile) {
        activePreset = preset
        activeCustomLookId = null
        activeLabel = preset.name
        intensity = 50
        currentSliders = preset.defaultSliders
        baseSlidersForReset = preset.defaultSliders
        val loaded = LutLoader.loadLut(context, preset.lutFile)
        renderer.setLut(loaded.buffer, loaded.size, preset.lutStage)
        renderer.setSliders(preset.defaultSliders)
        renderer.setIntensity(50)
        glView?.requestRender()
    }

    fun applyCustomLook(look: CustomLook) {
        activeCustomLookId = look.id
        activeLabel = look.name
        presets.firstOrNull { it.id == look.basePresetId }?.let { activePreset = it }
        intensity = look.intensity
        currentSliders = look.sliders
        baseSlidersForReset = look.sliders
        val loaded = LutLoader.loadLut(context, look.lutFile)
        renderer.setLut(loaded.buffer, loaded.size, look.lutStage)
        renderer.setSliders(look.sliders)
        renderer.setIntensity(look.intensity)
        glView?.requestRender()
    }

    val hasPhoto = originalBitmap != null
    val hasEdits = hasPhoto && (
        intensity != 50 ||
            currentSliders != baseSlidersForReset ||
            selectedRatioOption.id != "original" ||
            isSplitActive
        )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioCanvasBg)
            .statusBarsPadding()
    ) {
        // 1. TOP BAR (48.dp, Apple Darkroom style)
        LookLiteHeader(
            activePresetName = activeLabel,
            hasPhoto = hasPhoto,
            isRawPhoto = isRawPhoto,
            hasEdits = hasEdits,
            onOpenGallery = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onOpenRaw = {
                rawPicker.launch(arrayOf("*/*"))
            },
            onReset = {
                intensity = 50
                currentSliders = baseSlidersForReset
                renderer.setSliders(baseSlidersForReset)
                renderer.setIntensity(50)
                isSplitActive = false
                renderer.setSplitPosition(-1f)
                selectedRatioOption = PresetCatalog.aspectRatios.first()
                cropNormX = 0.5f
                cropNormY = 0.5f
                isRatioStripOpen = false
                glView?.requestRender()
            },
            onOpenSettings = { showSettingsSheet = true },
            onExport = { showExportDialog = true }
        )

        // 2. PHOTO CANVAS & FLOATING INSPECTOR (~68% screen height)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .onSizeChanged { size ->
                    containerWidth = size.width.toFloat()
                    containerHeight = size.height.toFloat()
                },
            contentAlignment = Alignment.Center
        ) {
            if (!hasPhoto) {
                // Apple-style Empty Canvas State
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(StudioWorkbenchBg)
                        .border(0.5.dp, StudioHairlineBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        .padding(horizontal = 24.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(StudioElevatedBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            tint = StudioAccent,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Text(
                        text = stringResource(R.string.empty_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                        color = StudioTextPrimary,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = stringResource(R.string.empty_subtitle),
                        fontSize = 12.5.sp,
                        color = StudioTextSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(9999.dp))
                                .background(StudioAccent)
                                .clickable {
                                    photoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.empty_cta),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(9999.dp))
                                .background(StudioElevatedBg)
                                .border(0.5.dp, StudioHairlineBorder, RoundedCornerShape(9999.dp))
                                .clickable {
                                    rawPicker.launch(arrayOf("*/*"))
                                }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.action_open_raw),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = StudioTextPrimary
                            )
                        }
                    }
                }
            } else {
                val density = LocalDensity.current
                val imageAspect = originalBitmap!!.width.toFloat() / originalBitmap!!.height.toFloat()

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
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .pointerInput(isSplitActive, selectedRatioOption.ratio, fitPxW) {
                                if (isSplitActive) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val relX = (change.position.x / fitPxW).coerceIn(0.02f, 0.98f)
                                        splitPos = relX
                                        renderer.setSplitPosition(relX)
                                        glView?.requestRender()
                                    }
                                } else if (selectedRatioOption.ratio == null) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        isHoldingOriginal = true
                                        renderer.setShowBefore(true)
                                        glView?.requestRender()
                                        waitForUpOrCancellation()
                                        isHoldingOriginal = false
                                        renderer.setShowBefore(false)
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

                        if (isHoldingOriginal) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(9999.dp))
                                    .background(StudioGlassOverlay)
                                    .border(0.5.dp, StudioGlassBorder, RoundedCornerShape(9999.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.badge_original),
                                    color = StudioTextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }

                        if (isSplitActive) {
                            val splitXdp = with(density) { (fitPxW * splitPos).toDp() }

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.5.dp)
                                    .offset(x = splitXdp - 0.75.dp)
                                    .background(Color.White)
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = splitXdp - 11.dp)
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(StudioGlassOverlay)
                                    .border(1.dp, Color.White, RoundedCornerShape(11.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "‹›",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9999.dp))
                                        .background(StudioGlassOverlay)
                                        .border(0.5.dp, StudioGlassBorder, RoundedCornerShape(9999.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.badge_before),
                                        color = StudioTextPrimary,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9999.dp))
                                        .background(StudioAccent)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.badge_after),
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

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

                // Floating Glassmorphic Inspector Pill Bar (only when photo is loaded)
                FloatingCanvasInspector(
                    selectedRatio = selectedRatioOption,
                    onRatioSelected = { option ->
                        selectedRatioOption = option
                        cropNormX = 0.5f
                        cropNormY = 0.5f
                        isRatioStripOpen = false
                    },
                    isRatioStripOpen = isRatioStripOpen,
                    onToggleRatioStrip = { isRatioStripOpen = !isRatioStripOpen },
                    onRotate90 = {
                        val bmp = originalBitmap ?: return@FloatingCanvasInspector
                        scope.launch {
                            val rotated = withContext(Dispatchers.Default) {
                                val m = Matrix().apply { postRotate(90f) }
                                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                            }
                            originalBitmap = rotated
                            renderer.setImageBitmap(rotated)
                            cropNormX = 0.5f
                            cropNormY = 0.5f
                            glView?.requestRender()
                            baseThumbPixels = withContext(Dispatchers.Default) {
                                PresetThumbnailGenerator.createBaseThumbnail(rotated)
                            }
                        }
                    },
                    isSplitActive = isSplitActive,
                    onToggleSplit = {
                        isSplitActive = !isSplitActive
                        renderer.setSplitPosition(if (isSplitActive) splitPos else -1f)
                        glView?.requestRender()
                    },
                    isHoldingOriginal = isHoldingOriginal,
                    onHoldOriginalChanged = { holding ->
                        isHoldingOriginal = holding
                        renderer.setShowBefore(holding)
                        glView?.requestRender()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }
        }

        // 3. DOCKED BOTTOM WORKBENCH (Flush to bottom edge, #141416, locked until photo is loaded)
        StudioWorkbench(
            isEnabled = hasPhoto,
            activeTab = activeWorkbenchTab,
            onTabSelected = { activeWorkbenchTab = it },
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            presets = presets,
            activePresetId = activePreset.id,
            activeCustomLookId = activeCustomLookId,
            onPresetSelected = { preset -> applyPreset(preset) },
            customLooks = customLooks,
            onCustomLookSelected = { look -> applyCustomLook(look) },
            onRequestSaveCustomLook = { showSaveLookDialog = true },
            onRenameCustomLook = { look -> renamingLook = look },
            onOverwriteCustomLook = { look ->
                val updated = customLooks.map {
                    if (it.id == look.id) {
                        it.copy(
                            basePresetId = activePreset.id,
                            lutFile = activePreset.lutFile,
                            lutStage = activePreset.lutStage,
                            intensity = intensity,
                            autoColor = false,
                            sliders = currentSliders
                        )
                    } else it
                }
                scope.launch { prefs.saveCustomLooks(updated) }
                Toast.makeText(context, context.getString(R.string.toast_look_saved, look.name), Toast.LENGTH_SHORT).show()
            },
            onDeleteCustomLook = { look ->
                val updated = customLooks.filterNot { it.id == look.id }
                if (activeCustomLookId == look.id) activeCustomLookId = null
                scope.launch { prefs.saveCustomLooks(updated) }
            },
            baseThumbPixels = baseThumbPixels,
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
    }

    // Export Modal Sheet
    if (showExportDialog && originalBitmap != null) {
        val bmp = originalBitmap!!
        ExportDialog(
            originalWidth = bmp.width,
            originalHeight = bmp.height,
            onDismiss = { showExportDialog = false },
            onConfirmExport = { resolution ->
                showExportDialog = false
                scope.launch {
                    executeNativeExport(
                        context = context,
                        sourceBitmap = bmp,
                        targetRatio = selectedRatioOption.ratio,
                        normX = cropNormX,
                        normY = cropNormY,
                        resolution = resolution,
                        glView = glView,
                        renderer = renderer,
                        presetName = activeLabel
                    )
                }
            }
        )
    }

    // Settings Modal Sheet
    if (showSettingsSheet) {
        val bmp = originalBitmap
        SettingsBottomSheet(
            selectedLanguage = selectedLanguage,
            onLanguageSelected = onChangeLanguage,
            isDeveloperUnlocked = isDeveloperUnlocked,
            onUnlockDeveloper = { isDeveloperUnlocked = true },
            gpuFrameTimeMs = renderer.lastFrameTimeMs,
            glRendererName = renderer.glRendererName,
            activePresetName = activeLabel,
            imageDimensions = "${bmp?.width ?: 0} × ${bmp?.height ?: 0}",
            onOpenDebugConsole = {
                showSettingsSheet = false
                showDiagnosticDialog = true
            },
            onDismiss = { showSettingsSheet = false }
        )
    }

    // Developer Diagnostics Dialog
    if (showDiagnosticDialog) {
        val bmp = originalBitmap
        DiagnosticDialog(
            activePresetName = "$activeLabel (${activePreset.brand.displayName})",
            imageDimensions = "${bmp?.width ?: 0} × ${bmp?.height ?: 0}",
            intensity = intensity,
            gpuFrameTimeMs = renderer.lastFrameTimeMs,
            glRendererName = renderer.glRendererName,
            onDismiss = { showDiagnosticDialog = false }
        )
    }

    // Save Custom Look Dialog
    if (showSaveLookDialog) {
        StudioNameInputDialog(
            title = stringResource(R.string.save_look_title),
            hint = stringResource(R.string.save_look_hint),
            initialValue = "",
            onDismiss = { showSaveLookDialog = false },
            onConfirm = { name ->
                showSaveLookDialog = false
                val newLook = CustomLook(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    basePresetId = activePreset.id,
                    lutFile = activePreset.lutFile,
                    lutStage = activePreset.lutStage,
                    intensity = intensity,
                    autoColor = false,
                    sliders = currentSliders
                )
                activeCustomLookId = newLook.id
                activeLabel = name
                scope.launch { prefs.saveCustomLooks(listOf(newLook) + customLooks) }
                Toast.makeText(context, context.getString(R.string.toast_look_saved, name), Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Rename Custom Look Dialog
    renamingLook?.let { target ->
        StudioNameInputDialog(
            title = stringResource(R.string.action_rename),
            hint = stringResource(R.string.save_look_hint),
            initialValue = target.name,
            onDismiss = { renamingLook = null },
            onConfirm = { newName ->
                renamingLook = null
                val updated = customLooks.map { if (it.id == target.id) it.copy(name = newName) else it }
                if (activeCustomLookId == target.id) activeLabel = newName
                scope.launch { prefs.saveCustomLooks(updated) }
            }
        )
    }
}

@Composable
private fun StudioNameInputDialog(
    title: String,
    hint: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioWorkbenchBg)
                .border(1.dp, StudioHairlineBorder, RoundedCornerShape(14.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = StudioTextPrimary
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioElevatedBg)
                    .border(1.dp, StudioHairlineBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(text = hint, fontSize = 13.sp, color = StudioTextSecondary)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = TextStyle(color = StudioTextPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(StudioAccent),
                    modifier = Modifier.fillMaxWidth()
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
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = StudioTextSecondary
                    )
                }

                val canSave = text.trim().isNotEmpty()
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (canSave) StudioAccent else StudioElevatedBg)
                        .clickable(enabled = canSave) { onConfirm(text.trim()) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.action_save),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canSave) Color.White else StudioTextSecondary
                    )
                }
            }
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
    glView: LookLiteGLView?,
    renderer: LookLiteRenderer,
    presetName: String
) = withContext(Dispatchers.IO) {
    try {
        val origW = sourceBitmap.width
        val origH = sourceBitmap.height

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

        val view = glView ?: throw IllegalStateException("OpenGL surface is not ready")
        val finalBitmap = suspendCancellableCoroutine<Bitmap> { cont ->
            view.queueEvent {
                try {
                    val bmp = renderer.renderOffscreen(croppedBitmap, targetW, targetH)
                    if (cont.isActive) cont.resume(bmp)
                } catch (t: Throwable) {
                    if (cont.isActive) cont.resumeWithException(t)
                }
            }
        }
        view.requestRender()

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
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_saved_gallery, finalBitmap.width, finalBitmap.height),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            throw RuntimeException("MediaStore allocation failed")
        }
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_export_failed, e.message ?: "Unknown"),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

