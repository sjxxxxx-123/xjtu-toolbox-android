package com.xjtu.toolbox.library

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * v2: rounded-rect seats with full ID, fixed-size grid cells, pinch zoom + pan
 */
@Composable
fun SeatMapView(
    seats: List<SeatInfo>,
    areaCode: String,
    favorites: Set<String>,
    recommendedSeatIds: Set<String>,
    onSeatClick: (String) -> Unit,
    onSeatLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    data class MappedSeat(
        val seat: SeatInfo, val gridX: Int, val gridY: Int,
        val isFavorite: Boolean, val isRecommended: Boolean
    )

    val mappedSeats = remember(seats, areaCode, favorites, recommendedSeatIds) {
        seats.mapNotNull { seat ->
            val pos = SeatNeighborData.getSeatPosition(seat.seatId, areaCode)
            if (pos.gridX < 0 || pos.gridY < 0) return@mapNotNull null
            MappedSeat(seat, pos.gridX, pos.gridY,
                seat.seatId in favorites, seat.seatId in recommendedSeatIds)
        }
    }

    if (mappedSeats.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("该区域暂不支持地图视图", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        return
    }

    val maxGridX = mappedSeats.maxOf { it.gridX }
    val maxGridY = mappedSeats.maxOf { it.gridY }

    val density = LocalDensity.current
    // Fixed cell & seat sizes
    val cellW = with(density) { 72.dp.toPx() }
    val cellH = with(density) { 36.dp.toPx() }
    val seatW = with(density) { 66.dp.toPx() }
    val seatH = with(density) { 28.dp.toPx() }
    val cornerR = with(density) { 7.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val textSizePx = with(density) { 10.dp.toPx() }

    val canvasW = (margin * 2 + (maxGridX + 1) * cellW).toInt()
    val canvasH = (margin * 2 + (maxGridY + 1) * cellH).toInt()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val colorAvailable = MiuixTheme.colorScheme.primary
    val colorOccupied = MiuixTheme.colorScheme.outline
    val colorFavorite = MiuixTheme.colorScheme.primaryVariant
    val colorRecommended = MiuixTheme.colorScheme.error
    val colorText = MiuixTheme.colorScheme.onPrimary
    val colorTextOcc = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val colorStroke = MiuixTheme.colorScheme.onPrimary

    Column(modifier = modifier) {
        // legend
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(color = colorAvailable, label = "空座")
            LegendDot(color = colorOccupied, label = "已占")
            LegendDot(color = colorRecommended, label = "推荐")
            LegendDot(color = colorFavorite, label = "收藏")
            Spacer(Modifier.weight(1f))
            Text("双指缩放·拖动", style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f))
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(areaCode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.25f, 4f)
                        offsetX = offsetX * (newScale / scale) + pan.x
                        offsetY = offsetY * (newScale / scale) + pan.y
                        scale = newScale
                    }
                }
                .pointerInput(mappedSeats) {
                    detectTapGestures(
                        onTap = { tapOffset ->
                            val rx = (tapOffset.x - offsetX) / scale
                            val ry = (tapOffset.y - offsetY) / scale
                            val hit = mappedSeats.firstOrNull { ms ->
                                val sx = margin + ms.gridX * cellW + (cellW - seatW) / 2f
                                val sy = margin + ms.gridY * cellH + (cellH - seatH) / 2f
                                rx >= sx && rx <= sx + seatW && ry >= sy && ry <= sy + seatH
                            }
                            if (hit != null && hit.seat.available) {
                                onSeatClick(hit.seat.seatId)
                            }
                        },
                        onLongPress = { tapOffset ->
                            val rx = (tapOffset.x - offsetX) / scale
                            val ry = (tapOffset.y - offsetY) / scale
                            val hit = mappedSeats.firstOrNull { ms ->
                                val sx = margin + ms.gridX * cellW + (cellW - seatW) / 2f
                                val sy = margin + ms.gridY * cellH + (cellH - seatH) / 2f
                                rx >= sx && rx <= sx + seatW && ry >= sy && ry <= sy + seatH
                            }
                            if (hit != null) {
                                onSeatLongClick(hit.seat.seatId)
                            }
                        }
                    )
                }
        ) {
            Canvas(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    )
                    .size(with(density) { canvasW.toDp() }, with(density) { canvasH.toDp() })
            ) {
                val corner = CornerRadius(cornerR, cornerR)
                val sz = Size(seatW, seatH)
                val textPaint = AndroidPaint().apply {
                    this.textSize = textSizePx
                    isAntiAlias = true
                    textAlign = AndroidPaint.Align.CENTER
                    isFakeBoldText = true
                }
                val maxTextW = seatW * 0.90f

                mappedSeats.forEach { ms ->
                    val sx = margin + ms.gridX * cellW + (cellW - seatW) / 2f
                    val sy = margin + ms.gridY * cellH + (cellH - seatH) / 2f

                    val bgColor = when {
                        ms.isRecommended && ms.seat.available -> colorRecommended
                        ms.isFavorite && ms.seat.available -> colorFavorite
                        ms.seat.available -> colorAvailable
                        else -> colorOccupied
                    }

                    drawRoundRect(bgColor, Offset(sx, sy), sz, corner)
                    if (ms.isRecommended && ms.seat.available) {
                        drawRoundRect(colorStroke, Offset(sx, sy), sz, corner, style = Stroke(2f))
                    }

                    // full seat ID with short prefix
                    val display = shortSeatLabel(ms.seat.seatId)
                    textPaint.color = (if (ms.seat.available) colorText else colorTextOcc).toArgb()
                    val measured = textPaint.measureText(display)
                    if (measured > maxTextW) {
                        val s = maxTextW / measured
                        if (s >= 0.45f) textPaint.textScaleX = s
                    }
                    val fm = textPaint.fontMetrics
                    val baseline = sy + seatH / 2f - (fm.descent - fm.ascent) / 2f - fm.ascent
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(display, sx + seatW / 2f, baseline, textPaint)
                    }
                    textPaint.textScaleX = 1f
                }
            }
        }
    }
}

/** Strip "N4" prefix and leading zeros for shorter display */
private fun shortSeatLabel(seatId: String): String {
    val s = if (seatId.startsWith("N4")) seatId.substring(2) else seatId
    return s.replace(Regex("0+(\\d)"), "${'$'}1")
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(
            modifier = Modifier.size(width = 14.dp, height = 10.dp),
            shape = RoundedCornerShape(3.dp),
            color = color
        ) {}
        Text(label, style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

val MAP_SUPPORTED_AREAS = setOf(
    "north4east", "north4west", "north4middle",
    "north4southwest", "north4southeast"
)