package com.xjtu.toolbox.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.SinkFeedback

/**
 * Miuix 风格弹出菜单。
 * 圆角 + scaleIn/fadeIn 动画 + 半透明蒙版 + SinkFeedback 按压质感。
 * @param alignment 菜单弹出位置：TopStart(左上) / TopEnd(右上，默认)
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopEnd,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    if (expanded) {
        // 根据对齐方式决定动画起点
        val transformOrigin = when (alignment) {
            Alignment.TopStart -> TransformOrigin(0f, 0f)
            Alignment.TopEnd -> TransformOrigin(1f, 0f)
            Alignment.BottomStart -> TransformOrigin(0f, 1f)
            Alignment.BottomEnd -> TransformOrigin(1f, 1f)
            else -> TransformOrigin(1f, 0f)
        }
        Popup(
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true)
        ) {
            // 用 LaunchedEffect 驱动动画：mounted 后从 false -> true 触发 enter 动画
            var animVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { animVisible = true }

            Box(Modifier.fillMaxSize()) {
                // 半透明蒙版 — 淡入
                AnimatedVisibility(
                    visible = animVisible,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(120))
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.10f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onDismissRequest() }
                    )
                }
                // 菜单体 — 根据 alignment 定位
                val menuAlignment = alignment
                val menuPadding = when (alignment) {
                    Alignment.TopStart -> Modifier.padding(top = offset.y + 56.dp, start = 8.dp)
                    Alignment.TopEnd -> Modifier.padding(top = offset.y + 56.dp, end = 8.dp)
                    else -> Modifier.padding(top = offset.y + 56.dp, end = 8.dp)
                }
                AnimatedVisibility(
                    visible = animVisible,
                    enter = scaleIn(
                        animationSpec = tween(220),
                        initialScale = 0.7f,
                        transformOrigin = transformOrigin
                    ) + fadeIn(tween(180)),
                    exit = scaleOut(
                        animationSpec = tween(150),
                        targetScale = 0.8f,
                        transformOrigin = transformOrigin
                    ) + fadeOut(tween(100)),
                    modifier = Modifier
                        .align(menuAlignment)
                        .then(menuPadding)
                ) {
                    Surface(
                        modifier = modifier.widthIn(min = 180.dp, max = 300.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MiuixTheme.colorScheme.surfaceVariant,
                    ) {
                        Column(
                            Modifier
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 4.dp),
                            content = content
                        )
                    }
                }
            }
        }
    }
}

/**
 * 菜单项 — SinkFeedback 原生按压质感 + 圆角clip。
 */
@Composable
fun AppDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = SinkFeedback(),
                enabled = enabled
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.weight(1f)) {
            text()
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(12.dp))
            trailingIcon()
        }
    }
}
