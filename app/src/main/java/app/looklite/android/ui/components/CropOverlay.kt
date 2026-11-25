package app.looklite.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.looklite.android.R
import app.looklite.android.ui.theme.StudioGlassOverlay
import app.looklite.android.ui.theme.StudioHairlineBorder
import app.looklite.android.ui.theme.StudioTextPrimary

@Composable
fun CropOverlay(
    containerWidth: Float,
    containerHeight: Float,
    imageAspect: Float,
    targetAspect: Float?,
    normX: Float,
    normY: Float,
    onNormOffsetChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (targetAspect == null || containerWidth <= 0 || containerHeight <= 0 || imageAspect <= 0) {
        return
    }

    // Calculate actual image display rect inside container
    var imgW = containerWidth
    var imgH = imgW / imageAspect
    if (imgH > containerHeight) {
        imgH = containerHeight
        imgW = imgH * imageAspect
    }
    val imgLeft = (containerWidth - imgW) / 2f
    val imgTop = (containerHeight - imgH) / 2f

    // Calculate crop box rect based on target aspect
    val boxW: Float
    val boxH: Float
    val maxShiftX: Float
    val maxShiftY: Float

    if (imageAspect > targetAspect) {
        boxH = imgH
        boxW = boxH * targetAspect
        maxShiftX = imgW - boxW
        maxShiftY = 0f
    } else {
        boxW = imgW
        boxH = boxW / targetAspect
        maxShiftX = 0f
        maxShiftY = imgH - boxH
    }

    val boxX = imgLeft + (if (maxShiftX > 0f) maxShiftX * normX else 0f)
    val boxY = imgTop + (if (maxShiftY > 0f) maxShiftY * normY else 0f)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(targetAspect, maxShiftX, maxShiftY, normX, normY) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newNormX = if (maxShiftX > 0f) {
                            (normX + dragAmount.x / maxShiftX).coerceIn(0f, 1f)
                        } else normX

                        val newNormY = if (maxShiftY > 0f) {
                            (normY + dragAmount.y / maxShiftY).coerceIn(0f, 1f)
                        } else normY

                        onNormOffsetChanged(newNormX, newNormY)
                    }
                }
        ) {
            val scrimColor = Color(0x99000000)

            // Scrim Top
            if (boxY > 0f) {
                drawRect(scrimColor, Offset.Zero, Size(size.width, boxY))
            }
            // Scrim Bottom
            if (boxY + boxH < size.height) {
                drawRect(scrimColor, Offset(0f, boxY + boxH), Size(size.width, size.height - (boxY + boxH)))
            }
            // Scrim Left
            if (boxX > 0f) {
                drawRect(scrimColor, Offset(0f, boxY), Size(boxX, boxH))
            }
            // Scrim Right
            if (boxX + boxW < size.width) {
                drawRect(scrimColor, Offset(boxX + boxW, boxY), Size(size.width - (boxX + boxW), boxH))
            }

            // Crop Box Border
            drawRect(
                color = Color.White,
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Rule-of-Thirds Grid Lines
            val gridColor = Color(0x73FFFFFF)
            val strokeW = 1.dp.toPx()

            // Vertical 1 & 2
            drawLine(gridColor, Offset(boxX + boxW / 3f, boxY), Offset(boxX + boxW / 3f, boxY + boxH), strokeWidth = strokeW)
            drawLine(gridColor, Offset(boxX + 2f * boxW / 3f, boxY), Offset(boxX + 2f * boxW / 3f, boxY + boxH), strokeWidth = strokeW)

            // Horizontal 1 & 2
            drawLine(gridColor, Offset(boxX, boxY + boxH / 3f), Offset(boxX + boxW, boxY + boxH / 3f), strokeWidth = strokeW)
            drawLine(gridColor, Offset(boxX, boxY + 2f * boxH / 3f), Offset(boxX + boxW, boxY + 2f * boxH / 3f), strokeWidth = strokeW)

            // Corner Brackets
            val cornerLen = 14.dp.toPx()
            val cornerThick = 3.dp.toPx()

            // Top-Left
            drawLine(Color.White, Offset(boxX - 1f, boxY), Offset(boxX + cornerLen, boxY), strokeWidth = cornerThick)
            drawLine(Color.White, Offset(boxX, boxY - 1f), Offset(boxX, boxY + cornerLen), strokeWidth = cornerThick)

            // Top-Right
            drawLine(Color.White, Offset(boxX + boxW + 1f, boxY), Offset(boxX + boxW - cornerLen, boxY), strokeWidth = cornerThick)
            drawLine(Color.White, Offset(boxX + boxW, boxY - 1f), Offset(boxX + boxW, boxY + cornerLen), strokeWidth = cornerThick)

            // Bottom-Left
            drawLine(Color.White, Offset(boxX - 1f, boxY + boxH), Offset(boxX + cornerLen, boxY + boxH), strokeWidth = cornerThick)
            drawLine(Color.White, Offset(boxX, boxY + boxH + 1f), Offset(boxX, boxY + boxH - cornerLen), strokeWidth = cornerThick)

            // Bottom-Right
            drawLine(Color.White, Offset(boxX + boxW + 1f, boxY + boxH), Offset(boxX + boxW - cornerLen, boxY + boxH), strokeWidth = cornerThick)
            drawLine(Color.White, Offset(boxX + boxW, boxY + boxH + 1f), Offset(boxX + boxW, boxY + boxH - cornerLen), strokeWidth = cornerThick)
        }

        // Draggable Hint Pill (positioned near top so it doesn't collide with bottom floating inspector)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            shape = RoundedCornerShape(9999.dp),
            color = StudioGlassOverlay
        ) {
            Text(
                text = stringResource(R.string.crop_hint),
                color = StudioTextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
