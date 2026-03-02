package com.xjtu.toolbox.library

import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.max

// seat map canvas v3 - pinch zoom + pan + short IDs

private val SW  = 64.dp
private val SH  = 30.dp
private val PAD = 3.dp
private val DESK_PAD = 6.dp
private val GROUP_GAP = 14.dp
private val WALL_W  = 28.dp
private val MARGIN  = 12.dp
private val CORRIDOR_H = 48.dp
private val LABEL_H = 22.dp
private val COLUMN_GAP = 36.dp

private val SEP_COLORS = listOf(
    Color(0xFFE94560), Color(0xFF4ECCA3), Color(0xFFFFD460), Color(0xFF7EC8E3),
    Color(0xFFC084FC), Color(0xFFFB923C), Color(0xFFA3E635), Color(0xFFF472B6),
)

private sealed class LayoutType {
    object Standard : LayoutType()
    object TwoColumn : LayoutType()
    object Middle : LayoutType()
}

private data class RowSpec(
    val upperGroups: List<List<String>>,
    val lowerGroups: List<List<String>>,
    val upperWallAfter: Int = -1,
    val lowerWallAfter: Int = -1,
    val upperLabel: String = "north(upper)",
    val lowerLabel: String = "south(lower)",
    val layout: LayoutType = LayoutType.Standard,
    val upperEntranceSide: Int = 0,
    val lowerEntranceSide: Int = 0,
)

private fun buildRowSpec(areaCode: String): RowSpec? {
    val groups = SeatNeighborData.getAreaGroups(areaCode) ?: return null
    return when (areaCode) {
        "north4east" -> RowSpec(
            upperGroups    = groups.subList(0, 10).reversed() + groups.subList(24, 26),
            lowerGroups    = groups.subList(10, 20).reversed() + groups.subList(20, 24),
            upperWallAfter = 9, lowerWallAfter = 9,
            upperLabel = "\u5317\u4fa7(\u4e0a\u884c)", lowerLabel = "\u5357\u4fa7(\u4e0b\u884c)",
            upperEntranceSide = -1, lowerEntranceSide = -1,
        )
        "north4west" -> RowSpec(
            upperGroups    = groups.subList(0, 11).reversed() + groups.subList(25, 27),
            lowerGroups    = groups.subList(11, 21).reversed() + groups.subList(21, 25),
            upperWallAfter = 10, lowerWallAfter = 9,
            upperLabel = "\u5317\u4fa7(\u4e0a\u884c)", lowerLabel = "\u5357\u4fa7(\u4e0b\u884c)",
            upperEntranceSide = -1, lowerEntranceSide = -1,
        )
        "north4middle" -> RowSpec(
            upperGroups    = groups.subList(6, 12),
            lowerGroups    = groups.subList(0, 6),
            upperLabel     = "\u4e0a\u534a\u533a", lowerLabel = "\u4e0b\u534a\u533a",
            layout         = LayoutType.Middle,
            upperEntranceSide = -1, lowerEntranceSide = -1,
        )
        "north4southwest" -> RowSpec(
            upperGroups    = groups.subList(0, 8),
            lowerGroups    = groups.subList(8, 17),
            upperLabel     = "\u53f3\u5217(001\u2013064)", lowerLabel = "\u5de6\u5217(065\u2013136)",
            layout         = LayoutType.TwoColumn,
            upperEntranceSide = 1, lowerEntranceSide = 1,
        )
        "north4southeast" -> RowSpec(
            upperGroups    = groups.subList(0, 9),
            lowerGroups    = groups.subList(9, 17),
            upperLabel     = "\u5de6\u5217(001\u2013072)", lowerLabel = "\u53f3\u5217(073\u2013136)",
            layout         = LayoutType.TwoColumn,
            upperEntranceSide = 1, lowerEntranceSide = 1,
        )
        else -> null
    }
}

/** strip common prefix "N4" and leading zeros for shorter display */
private fun shortSeatId(seatId: String): String {
    val stripped = if (seatId.startsWith("N4")) seatId.substring(2) else seatId
    return stripped.replace(Regex("0+(\\d)"), "${'$'}1")
}

@Composable
fun SeatMapCanvas(
    seats: List<SeatInfo>,
    areaCode: String,
    favorites: Set<String> = emptySet(),
    recommendedSeats: List<SeatInfo> = emptyList(),
    onSeatClick: (SeatInfo) -> Unit = {},
    onSeatLongClick: (String) -> Unit = {},
    onUnavailableSeatClick: () -> Unit = {},
) {
    val spec = buildRowSpec(areaCode) ?: return
    val cs = MiuixTheme.colorScheme
    val canvasBg   = cs.surfaceVariant
    val corrBg     = cs.surfaceVariant
    val wallClr    = cs.outline
    val occupClr   = Color(0xFF9E9E9E)
    val availClr   = Color(0xFF4CAF50)
    val recommClr  = cs.error
    val favClr     = cs.primaryVariant
    val textClr    = Color.White
    val occTextClr = Color.White.copy(alpha = 0.7f)
    val labelClr   = cs.onSurfaceVariantSummary

    val density = LocalDensity.current
    val swPx = with(density) { SW.toPx() }
    val shPx = with(density) { SH.toPx() }
    val padPx = with(density) { PAD.toPx() }
    val deskPadPx = with(density) { DESK_PAD.toPx() }
    val groupGapPx = with(density) { GROUP_GAP.toPx() }
    val wallWPx = with(density) { WALL_W.toPx() }
    val marginPx = with(density) { MARGIN.toPx() }
    val corridorHPx = with(density) { CORRIDOR_H.toPx() }
    val labelHPx = with(density) { LABEL_H.toPx() }
    val cornerR = with(density) { 4.dp.toPx() }

    val unitW: Float
    val unitH: Float
    if (spec.layout is LayoutType.TwoColumn) {
        unitW = 4 * (swPx + padPx) + deskPadPx
        unitH = 2 * (shPx + padPx)
    } else {
        unitW = 2 * (swPx + padPx) + deskPadPx
        unitH = 6 * (shPx + padPx)
    }

    fun rowWidthPx(groups: List<List<String>>, wallAfter: Int): Float {
        var w = groups.size * (unitW + groupGapPx) - groupGapPx
        if (wallAfter >= 0) w += wallWPx - groupGapPx
        return w
    }

    val upperW = rowWidthPx(spec.upperGroups, spec.upperWallAfter)
    val lowerW = rowWidthPx(spec.lowerGroups, spec.lowerWallAfter)
    val contentW = max(upperW, lowerW)
    val columnGapPx = with(density) { COLUMN_GAP.toPx() }

    val canvasW: Int
    val canvasH: Int
    if (spec.layout is LayoutType.TwoColumn) {
        canvasW = (marginPx * 2 + unitW + columnGapPx + unitW).toInt()
        val leftColH  = spec.upperGroups.size * (unitH + groupGapPx) - groupGapPx
        val rightColH = spec.lowerGroups.size * (unitH + groupGapPx) - groupGapPx
        val extraBottom = if (spec.upperEntranceSide == 1 || spec.lowerEntranceSide == 1) with(density) { 28.dp.toPx() } else 0f
        canvasH = (marginPx * 2 + labelHPx + maxOf(leftColH, rightColH) + extraBottom).toInt()
    } else {
        canvasW = (marginPx * 2 + contentW).toInt()
        canvasH = (marginPx * 2 + 2 * (labelHPx + unitH) + corridorHPx).toInt()
    }

    val seatAvail = remember(seats) { seats.associate { it.seatId to it.available } }
    val recommendedSet = remember(recommendedSeats) { recommendedSeats.map { it.seatId }.toSet() }
    val favoriteSet = remember(favorites) { favorites }

    data class SeatRect(val seatId: String, val x: Float, val y: Float, val w: Float, val h: Float)
    val seatRects = remember { mutableListOf<SeatRect>() }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    fun DrawScope.drawSeatText(
        canvas: androidx.compose.ui.graphics.Canvas,
        seatId: String, centerX: Float, cy: Float, seatH: Float, textPaint: AndroidPaint
    ) {
        val display = shortSeatId(seatId)
        val measured = textPaint.measureText(display)
        val maxW = (swPx - padPx) * 0.88f
        if (measured > maxW) {
            val s = maxW / measured
            if (s < 0.55f) return
            textPaint.textScaleX = s
        }
        val fm = textPaint.fontMetrics
        val textH = fm.descent - fm.ascent
        val baseline = cy + seatH / 2f - textH / 2f - fm.ascent
        canvas.nativeCanvas.drawText(display, centerX, baseline, textPaint)
        textPaint.textScaleX = 1f
    }

    fun DrawScope.drawUnit(
        group: List<String>, gx: Float, gy: Float,
        colorIdx: Int, isUpperRow: Boolean, rects: MutableList<SeatRect>
    ) {
        val minSize = if (spec.layout is LayoutType.TwoColumn) 8 else 12
        if (group.size < minSize) return
        val color = SEP_COLORS[colorIdx % 8]
        val corner = CornerRadius(cornerR, cornerR)
        val sz = Size(swPx - padPx, shPx - padPx)
        val textPaint = AndroidPaint().apply {
            this.textSize = with(density) { 10.dp.toPx() }
            isAntiAlias = true
            textAlign = AndroidPaint.Align.CENTER
            isFakeBoldText = true
        }

        if (spec.layout is LayoutType.TwoColumn) {
            for (half in 0..1) {
                val xOff = if (half == 0) 0f else 2 * (swPx + padPx) + deskPadPx
                for (r in 0..1) {
                    for (c in 0..1) {
                        val seatId = group[half * 4 + r * 2 + c]
                        val cx = gx + xOff + c * (swPx + padPx)
                        val cy = gy + r * (shPx + padPx)
                        val available = seatAvail[seatId]
                        val isRecom = seatId in recommendedSet
                        val isFav = seatId in favoriteSet
                        val baseColor = when {
                            isRecom && available == true -> recommClr
                            isFav && available == true -> favClr
                            available == true -> availClr
                            available == false -> occupClr; else -> availClr.copy(alpha = 0.35f)
                        }
                        drawRoundRect(baseColor, Offset(cx, cy), sz, corner)
                        if (isRecom) drawRoundRect(cs.onError, Offset(cx, cy), sz, corner, style = Stroke(2.5f))
                        if (isFav && !isRecom) drawRoundRect(cs.onPrimaryVariant, Offset(cx, cy), sz, corner, style = Stroke(2f))
                        drawIntoCanvas { canvas ->
                            textPaint.color = (if (available == false) occTextClr else textClr).toArgb()
                            drawSeatText(canvas, seatId, cx + sz.width / 2, cy, sz.height, textPaint)
                        }
                        rects.add(SeatRect(seatId, cx, cy, sz.width, sz.height))
                    }
                }
            }
            return
        }

        val left: List<String>; val right: List<String>; val sepRow: Int
        if (spec.layout is LayoutType.Middle) {
            left = group.subList(0, 6); right = group.subList(6, 12); sepRow = 3
        } else if (isUpperRow) {
            left = listOf(group[6], group[7], group[8], group[9], group[10], group[11])
            right = listOf(group[0], group[1], group[2], group[3], group[4], group[5]); sepRow = 2
        } else {
            left = listOf(group[8], group[9], group[10], group[11], group[6], group[7])
            right = listOf(group[2], group[3], group[4], group[5], group[0], group[1]); sepRow = 4
        }

        listOf(left, right).forEachIndexed { col, column ->
            val cx = gx + col * (swPx + padPx + deskPadPx / 2)
            column.forEachIndexed { r, seatId ->
                val cy = gy + r * (shPx + padPx)
                val available = seatAvail[seatId]
                val isRecom = seatId in recommendedSet
                val isFav = seatId in favoriteSet
                val baseColor = when {
                    isRecom && available == true -> recommClr
                    isFav && available == true -> favClr
                    available == true -> availClr
                    available == false -> occupClr; else -> availClr.copy(alpha = 0.35f)
                }
                drawRoundRect(baseColor, Offset(cx, cy), sz, corner)
                if (isRecom) drawRoundRect(cs.onError, Offset(cx, cy), sz, corner, style = Stroke(2.5f))
                if (isFav && !isRecom) drawRoundRect(cs.onPrimaryVariant, Offset(cx, cy), sz, corner, style = Stroke(2f))
                drawIntoCanvas { canvas ->
                    textPaint.color = (if (available == false) occTextClr else textClr).toArgb()
                    drawSeatText(canvas, seatId, cx + sz.width / 2, cy, sz.height, textPaint)
                }
                rects.add(SeatRect(seatId, cx, cy, swPx - padPx, shPx - padPx))
            }
        }
        val lineY = gy + sepRow * (shPx + padPx) - padPx / 2f
        for (dx in 0..(2 * (swPx + padPx) + deskPadPx).toInt() step 6) {
            drawLine(color.copy(alpha = 0.6f), Offset(gx + dx, lineY), Offset(gx + dx + 3f, lineY), 1f)
        }
    }

    fun DrawScope.drawDoorMarker(doorCenterX: Float, doorTopY: Float, doorH: Float) {
        val dw = with(density) { 14.dp.toPx() }
        val dh = doorH * 0.55f
        val dy = doorTopY + (doorH - dh) / 2f
        val dx = doorCenterX - dw / 2f
        val doorColor = cs.primaryVariant
        drawRoundRect(doorColor.copy(alpha = 0.18f), Offset(dx, dy), Size(dw, dh), CornerRadius(3f, 3f))
        drawRoundRect(doorColor, Offset(dx, dy), Size(dw, dh), CornerRadius(3f, 3f), style = Stroke(1.5f))
        drawLine(doorColor.copy(alpha = 0.6f), Offset(doorCenterX, dy), Offset(doorCenterX, dy + dh), 1f)
        drawIntoCanvas { canvas ->
            val tp = AndroidPaint().apply {
                textSize = with(density) { 7.5.dp.toPx() }
                color = doorColor.toArgb(); isAntiAlias = true
                textAlign = AndroidPaint.Align.CENTER
            }
            canvas.nativeCanvas.drawText("\u5165\u53e3", doorCenterX, dy - with(density) { 2.dp.toPx() }, tp)
        }
    }

    fun DrawScope.drawRow(
        groups: List<List<String>>, wallAfter: Int, rowY: Float,
        isUpperRow: Boolean, label: String, baseGroupColorIdx: Int,
        entranceSide: Int, rects: MutableList<SeatRect>
    ) {
        drawIntoCanvas { canvas ->
            val paint = AndroidPaint().apply {
                textSize = with(density) { 10.dp.toPx() }; color = labelClr.toArgb(); isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(label, marginPx, rowY - 3f, paint)
        }
        val rowWidth = rowWidthPx(groups, wallAfter)
        val doorAreaTopY = rowY + labelHPx
        if (entranceSide == 0 || entranceSide == 2) drawDoorMarker(marginPx - with(density) { 9.dp.toPx() }, doorAreaTopY, unitH)
        if (entranceSide == 1 || entranceSide == 2) drawDoorMarker(marginPx + rowWidth + with(density) { 9.dp.toPx() }, doorAreaTopY, unitH)
        var curX = marginPx
        groups.forEachIndexed { i, grp ->
            if (wallAfter >= 0 && i == wallAfter + 1) {
                drawRect(wallClr, Offset(curX, rowY + labelHPx), Size(4f, unitH))
                drawIntoCanvas { canvas ->
                    val paint = AndroidPaint().apply {
                        textSize = with(density) { 8.dp.toPx() }; color = labelClr.toArgb()
                        isAntiAlias = true; textAlign = AndroidPaint.Align.CENTER
                    }
                    canvas.nativeCanvas.drawText("\u5899", curX + 10f, rowY + labelHPx + unitH / 2f, paint)
                }
                curX += wallWPx
            }
            drawUnit(grp, curX, rowY + labelHPx, baseGroupColorIdx + i, isUpperRow, rects)
            curX += unitW + groupGapPx
        }
    }

    fun DrawScope.drawColumn(
        groups: List<List<String>>, colX: Float, label: String,
        baseGroupColorIdx: Int, entranceSide: Int, rects: MutableList<SeatRect>
    ) {
        drawIntoCanvas { canvas ->
            val paint = AndroidPaint().apply {
                textSize = with(density) { 10.dp.toPx() }; color = labelClr.toArgb(); isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(label, colX, marginPx + labelHPx - 3f, paint)
        }
        val colCenterX = colX + unitW / 2f
        val colH = groups.size * (unitH + groupGapPx) - groupGapPx
        val doorOffset = with(density) { 10.dp.toPx() }
        if (entranceSide == 0 || entranceSide == 2) drawDoorMarker(colCenterX, marginPx + labelHPx - doorOffset * 2.5f, doorOffset * 2f)
        if (entranceSide == 1 || entranceSide == 2) drawDoorMarker(colCenterX, marginPx + labelHPx + colH + doorOffset * 0.5f, doorOffset * 2f)
        var curY = marginPx + labelHPx
        groups.forEachIndexed { i, grp ->
            drawUnit(grp, colX, curY, baseGroupColorIdx + i, isUpperRow = false, rects)
            curY += unitH + groupGapPx
        }
    }

    with(density) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .background(canvasBg)
                .pointerInput(areaCode) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.3f, 3f)
                        offsetX = offsetX * (newScale / scale) + pan.x
                        offsetY = offsetY * (newScale / scale) + pan.y
                        scale = newScale
                    }
                }
                .pointerInput(areaCode, seats) {
                    detectTapGestures(
                        onTap = { offset ->
                            val canvasX = (offset.x - offsetX) / scale
                            val canvasY = (offset.y - offsetY) / scale
                            val hit = seatRects.firstOrNull { r ->
                                canvasX >= r.x && canvasX <= r.x + r.w &&
                                canvasY >= r.y && canvasY <= r.y + r.h
                            }
                            when {
                                hit == null -> {}
                                seatAvail[hit.seatId] == true ->
                                    seats.find { it.seatId == hit.seatId }?.let { onSeatClick(it) }
                                else -> onUnavailableSeatClick()
                            }
                        },
                        onLongPress = { offset ->
                            val canvasX = (offset.x - offsetX) / scale
                            val canvasY = (offset.y - offsetY) / scale
                            val hit = seatRects.firstOrNull { r ->
                                canvasX >= r.x && canvasX <= r.x + r.w &&
                                canvasY >= r.y && canvasY <= r.y + r.h
                            }
                            if (hit != null) onSeatLongClick(hit.seatId)
                        }
                    )
                },
            contentAlignment = Alignment.TopStart
        ) {
            Canvas(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    )
                    .size(canvasW.toDp(), canvasH.toDp())
            ) {
                seatRects.clear()
                if (spec.layout is LayoutType.TwoColumn) {
                    val leftColX  = marginPx
                    val rightColX = marginPx + unitW + columnGapPx
                    val sepX = marginPx + unitW + columnGapPx / 2f
                    val colH = (maxOf(spec.upperGroups.size, spec.lowerGroups.size) * (unitH + groupGapPx) - groupGapPx)
                    drawLine(wallClr, Offset(sepX, marginPx), Offset(sepX, marginPx + labelHPx + colH), 2f)
                    drawColumn(spec.lowerGroups, leftColX, spec.lowerLabel, 0, spec.lowerEntranceSide, seatRects)
                    drawColumn(spec.upperGroups, rightColX, spec.upperLabel, spec.lowerGroups.size, spec.upperEntranceSide, seatRects)
                } else {
                    val upperRowY = marginPx
                    val lowerRowY = marginPx + labelHPx + unitH + corridorHPx
                    drawRect(corrBg, Offset(0f, upperRowY + labelHPx + unitH), Size(size.width, corridorHPx))
                    drawIntoCanvas { canvas ->
                        val midX = size.width / 2f
                        val corrMidY = upperRowY + labelHPx + unitH + corridorHPx / 2f
                        val paint = AndroidPaint().apply {
                            textSize = with(density) { 12.dp.toPx() }; color = labelClr.toArgb()
                            isAntiAlias = true; textAlign = AndroidPaint.Align.CENTER
                        }
                        canvas.nativeCanvas.drawText("\u2190 \u8d70\u5eca \u2192", midX, corrMidY + with(density) { 5.dp.toPx() }, paint)
                    }
                    val corrTopY = upperRowY + labelHPx + unitH
                    val corrMidYf = corrTopY + corridorHPx / 2f
                    drawDoorMarker(size.width / 2f, corrMidYf - with(density) { 14.dp.toPx() } / 2f, with(density) { 14.dp.toPx() })
                    drawRow(spec.upperGroups, spec.upperWallAfter, upperRowY, true, spec.upperLabel, 0, spec.upperEntranceSide, seatRects)
                    drawRow(spec.lowerGroups, spec.lowerWallAfter, lowerRowY, false, spec.lowerLabel, spec.upperGroups.size, spec.lowerEntranceSide, seatRects)
                }
            }

            if (scale == 1f) {
                Text(
                    "\u53cc\u6307\u6368\u5408\u7f29\u653e \u00b7 \u62d6\u52a8\u5e73\u79fb",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = labelClr
                )
            }
        }
    }
}