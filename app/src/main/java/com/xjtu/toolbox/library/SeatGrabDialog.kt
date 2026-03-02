package com.xjtu.toolbox.library

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.SinkFeedback
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * 定时抢座设置对话框
 */
@Composable
fun SeatGrabDialog(
    show: MutableState<Boolean>,
    currentFavorites: Set<String>,
    selectedArea: String,
    onDismiss: () -> Unit,
    onConfirm: (SeatGrabConfig) -> Unit
) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(SeatGrabConfigStore.load(context)) }
    var manualSeatId by remember { mutableStateOf("") }
    var manualAreaName by remember { mutableStateOf(selectedArea) }

    // 时间选择
    var hourStr by remember { mutableStateOf(config.triggerTimeStr.substringBefore(":")) }
    var minuteStr by remember { mutableStateOf(config.triggerTimeStr.split(":").getOrElse(1) { "00" }) }
    var secondStr by remember { mutableStateOf(config.triggerTimeStr.split(":").getOrElse(2) { "00" }) }

    // 权限状态
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasExactAlarmPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            } else true
        )
    }

    // 通知权限请求
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotificationPermission = granted }

    // 从系统设置返回后刷新权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                } else true
                hasExactAlarmPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
                } else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SuperBottomSheet(
        show = show,
        title = "定时抢座",
        onDismissRequest = { show.value = false; onDismiss() }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().overScrollVertical()
        ) {
                // ── 权限检查 ──
                if (!hasNotificationPermission || !hasExactAlarmPermission) {
                    item {
                        top.yukonga.miuix.kmp.basic.Card(
                            colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = MiuixTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "⚠ 需要授权",
                                    style = MiuixTheme.textStyles.subtitle,
                                    color = MiuixTheme.colorScheme.onErrorContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    TextButton(text = "授予通知权限", onClick = {
                                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    })
                                }
                                if (!hasExactAlarmPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    TextButton(text = "授予精确闹钟权限", onClick = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        )
                                    })
                                }
                            }
                        }
                    }
                }

                // ── 进程保活提醒 ──
                item {
                    top.yukonga.miuix.kmp.basic.Card(
                        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "⚡ 进程保活提醒",
                                style = MiuixTheme.textStyles.subtitle,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "为确保定时抢座成功，请：\n" +
                                    "1. 将本应用加入电池优化白名单\n" +
                                    "2. 允许后台运行（设置→应用→特殊权限）\n" +
                                    "3. 锁屏后不要手动清理后台\n" +
                                    "4. 部分厂商 ROM 需在安全中心/手机管家中设置自启动",
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // ── 触发时间 ──
                item {
                    Text("触发时间", style = MiuixTheme.textStyles.subtitle)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = hourStr,
                            onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) hourStr = it },
                            modifier = Modifier.width(64.dp),
                            label = "时",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        TextField(
                            value = minuteStr,
                            onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) minuteStr = it },
                            modifier = Modifier.width(64.dp),
                            label = "分",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Text(":", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        TextField(
                            value = secondStr,
                            onValueChange = { if (it.length <= 2 && it.all { c -> c.isDigit() }) secondStr = it },
                            modifier = Modifier.width(64.dp),
                            label = "秒",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                // ── 目标座位列表 ──
                item {
                    Text("目标座位（按优先级排列）", style = MiuixTheme.textStyles.subtitle)
                }

                // 已添加的座位
                itemsIndexed(config.targetSeats) { index, seat ->
                    top.yukonga.miuix.kmp.basic.Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 8.dp
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}.",
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(seat.seatId, style = MiuixTheme.textStyles.body1)
                                Text(
                                    seat.areaName.ifEmpty { seat.areaCode },
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            // 上移
                            if (index > 0) {
                                IconButton(
                                    onClick = {
                                        val list = config.targetSeats.toMutableList()
                                        val temp = list[index]
                                        list[index] = list[index - 1]
                                        list[index - 1] = temp
                                        config = config.copy(targetSeats = list.mapIndexed { i, s -> s.copy(priority = i) })
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) { Icon(Icons.Default.KeyboardArrowUp, "上移", Modifier.size(18.dp)) }
                            }
                            // 下移
                            if (index < config.targetSeats.size - 1) {
                                IconButton(
                                    onClick = {
                                        val list = config.targetSeats.toMutableList()
                                        val temp = list[index]
                                        list[index] = list[index + 1]
                                        list[index + 1] = temp
                                        config = config.copy(targetSeats = list.mapIndexed { i, s -> s.copy(priority = i) })
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) { Icon(Icons.Default.KeyboardArrowDown, "下移", Modifier.size(18.dp)) }
                            }
                            // 删除
                            IconButton(
                                onClick = {
                                    config = config.copy(
                                        targetSeats = config.targetSeats.filterIndexed { i, _ -> i != index }
                                            .mapIndexed { i, s -> s.copy(priority = i) }
                                    )
                                },
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Close, "删除", Modifier.size(18.dp)) }
                        }
                    }
                }

                // 从收藏添加按钮
                if (currentFavorites.isNotEmpty()) {
                    item {
                        val existingIds = config.targetSeats.map { it.seatId }.toSet()
                        val addable = currentFavorites.filter { it !in existingIds }
                        if (addable.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }
                            Surface(
                                modifier = Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = SinkFeedback()
                                    ) { expanded = !expanded },
                                shape = RoundedCornerShape(20.dp),
                                color = MiuixTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("从收藏添加 (${addable.size})")
                                }
                            }
                            AnimatedVisibility(expanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    addable.forEach { favSeatId ->
                                        val areaCode = LibraryApi.guessAreaCode(favSeatId)
                                            ?: LibraryApi.AREA_MAP[selectedArea] ?: ""
                                        val areaName = LibraryApi.AREA_MAP_REVERSE[areaCode]
                                            ?: selectedArea

                                        TextButton(
                                            text = "★ $favSeatId ($areaName)",
                                            onClick = {
                                                val newSeat = TargetSeat(
                                                    seatId = favSeatId,
                                                    areaCode = areaCode,
                                                    areaName = areaName,
                                                    priority = config.targetSeats.size
                                                )
                                                config = config.copy(
                                                    targetSeats = config.targetSeats + newSeat
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 手动输入
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = manualSeatId,
                            onValueChange = { manualSeatId = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = "座位号",
                            textStyle = MiuixTheme.textStyles.body2
                        )
                        Button(
                            onClick = {
                                if (manualSeatId.isNotBlank()) {
                                    val guessedArea = LibraryApi.guessAreaCode(manualSeatId.trim())
                                    val areaCode = guessedArea
                                        ?: LibraryApi.AREA_MAP[manualAreaName]
                                        ?: LibraryApi.AREA_MAP[selectedArea]
                                        ?: ""
                                    val areaName = if (guessedArea != null) {
                                        LibraryApi.AREA_MAP.entries.find { it.value == guessedArea }?.key ?: manualAreaName
                                    } else manualAreaName
                                    val newSeat = TargetSeat(
                                        seatId = manualSeatId.trim(),
                                        areaCode = areaCode,
                                        areaName = areaName,
                                        priority = config.targetSeats.size
                                    )
                                    config = config.copy(targetSeats = config.targetSeats + newSeat)
                                    manualSeatId = ""
                                }
                            },
                            enabled = manualSeatId.isNotBlank()
                        ) { Text("添加") }
                    }
                }

                // ── 高级设置 ──
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text("高级设置", style = MiuixTheme.textStyles.subtitle)
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = config.maxRetries.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { n ->
                                    config = config.copy(maxRetries = n.coerceIn(1, 20))
                                }
                            },
                            modifier = Modifier.width(100.dp),
                            label = "重试次数",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        TextField(
                            value = (config.retryIntervalMs / 1000).toString(),
                            onValueChange = {
                                it.toLongOrNull()?.let { sec ->
                                    config = config.copy(retryIntervalMs = (sec * 1000).coerceIn(500, 30000))
                                }
                            },
                            modifier = Modifier.width(100.dp),
                            label = "间隔(秒)",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("已有预约时自动换座", Modifier.weight(1f), style = MiuixTheme.textStyles.body1)
                        Switch(
                            checked = config.autoSwap,
                            onCheckedChange = { config = config.copy(autoSwap = it) }
                        )
                    }
                }

            // 底部留白
            item { Spacer(Modifier.height(8.dp)) }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { show.value = false; onDismiss() },
                modifier = Modifier.weight(1f),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.secondaryContainer)
            ) { Text("取消", color = MiuixTheme.colorScheme.onSecondaryContainer) }
            Button(
                onClick = {
                    val h = hourStr.toIntOrNull()?.coerceIn(0, 23) ?: 22
                    val m = minuteStr.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val s = secondStr.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val timeStr = "%02d:%02d:%02d".format(h, m, s)
                    val finalConfig = config.copy(
                        enabled = true,
                        triggerTimeStr = timeStr
                    )
                    show.value = false
                    onConfirm(finalConfig)
                },
                enabled = config.targetSeats.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("设定闹钟")
            }
        }
        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}
