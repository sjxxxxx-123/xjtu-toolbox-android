package com.xjtu.toolbox.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.xjtu.toolbox.ui.components.AppDropdownMenu
import com.xjtu.toolbox.ui.components.AppDropdownMenuItem
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 自定义课程编辑弹窗
 * @param existing 编辑已有课程时传入，为 null 表示新增
 * @param termCode 当前学期代码
 * @param onSave 保存回调
 * @param onDelete 删除回调（仅编辑模式）
 * @param onDismiss 关闭回调
 */
@Composable
fun CustomCourseDialog(
    show: MutableState<Boolean> = mutableStateOf(true),
    existing: CustomCourseEntity? = null,
    termCode: String,
    onSave: (CustomCourseEntity) -> Unit,
    onDelete: ((CustomCourseEntity) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isEdit = existing != null

    var courseName by remember { mutableStateOf(existing?.courseName ?: "") }
    var teacher by remember { mutableStateOf(existing?.teacher ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var dayOfWeek by remember { mutableIntStateOf(existing?.dayOfWeek ?: 1) }
    var startSection by remember { mutableIntStateOf(existing?.startSection ?: 1) }
    var endSection by remember { mutableIntStateOf(existing?.endSection ?: 2) }
    var selectedWeeks by remember {
        mutableStateOf(
            if (existing != null) {
                existing.weekBits.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }.toSet()
            } else {
                (1..16).toSet()  // 默认1-16周
            }
        )
    }

    val showDeleteConfirm = remember { mutableStateOf(false) }
    val nameError = courseName.isBlank()

    if (existing != null) {
        SuperBottomSheet(
            show = showDeleteConfirm,
            title = "确认删除",
            onDismissRequest = { showDeleteConfirm.value = false }
        ) {
            Text("确定要删除「${existing.courseName}」吗？")
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { showDeleteConfirm.value = false },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.secondaryContainer)
                ) { Text("取消", color = MiuixTheme.colorScheme.onSecondaryContainer) }
                Button(onClick = { onDelete?.invoke(existing); onDismiss() }, modifier = Modifier.weight(1f)) { Text("删除") }
            }
        }
    }

    SuperBottomSheet(
        show = show,
        title = if (isEdit) "编辑课程" else "添加课程",
        onDismissRequest = { show.value = false; onDismiss() }
    ) {
        if (isEdit) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { showDeleteConfirm.value = true }) {
                    Icon(Icons.Default.Delete, "删除", tint = MiuixTheme.colorScheme.error)
                }
            }
        }
        Column(
            modifier = Modifier.overScrollVertical().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                // 课程名
                TextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = "课程名称 *",
                    borderColor = if (nameError && courseName.isNotEmpty()) MiuixTheme.colorScheme.error else Color.Unspecified,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 教师 + 教室
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = teacher,
                        onValueChange = { teacher = it },
                        label = "教师",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = location,
                        onValueChange = { location = it },
                        label = "教室",
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 星期几
                Text("星期", style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Bold)
                TabRowWithContour(
                    tabs = listOf("一", "二", "三", "四", "五", "六", "日"),
                    selectedTabIndex = dayOfWeek - 1,
                    onTabSelected = { dayOfWeek = it + 1 },
                    modifier = Modifier.fillMaxWidth()
                )

                // 节次
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("第", style = MiuixTheme.textStyles.body1)
                    SectionPicker(value = startSection, onValueChange = {
                        startSection = it
                        if (endSection < it) endSection = it
                    }, modifier = Modifier.weight(1f))
                    Text("→", style = MiuixTheme.textStyles.body1)
                    SectionPicker(value = endSection, onValueChange = {
                        endSection = it
                        if (startSection > it) startSection = it
                    }, modifier = Modifier.weight(1f))
                    Text("节", style = MiuixTheme.textStyles.body1)
                }

                // 周次选择
                Text("上课周次", style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(text = "全选", onClick = { selectedWeeks = (1..20).toSet() })
                    TextButton(text = "单周", onClick = { selectedWeeks = (1..20).filter { it % 2 == 1 }.toSet() })
                    TextButton(text = "双周", onClick = { selectedWeeks = (1..20).filter { it % 2 == 0 }.toSet() })
                    TextButton(text = "清空", onClick = { selectedWeeks = emptySet() })
                }
                WeekCheckboxGrid(selectedWeeks = selectedWeeks, onToggle = { week ->
                    selectedWeeks = if (week in selectedWeeks) selectedWeeks - week else selectedWeeks + week
                })

                // 备注
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "备注",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            Button(
                onClick = { show.value = false; onDismiss() },
                modifier = Modifier.weight(1f),
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.secondaryContainer)
            ) { Text("取消", color = MiuixTheme.colorScheme.onSecondaryContainer) }
            Button(
                onClick = {
                    val weekBitsStr = (1..20).joinToString("") { if (it in selectedWeeks) "1" else "0" }
                    val entity = (existing ?: CustomCourseEntity(
                        courseName = "", teacher = "", location = "", weekBits = "",
                        dayOfWeek = 1, startSection = 1, endSection = 1, termCode = termCode
                    )).copy(
                        courseName = courseName.trim(),
                        teacher = teacher.trim(),
                        location = location.trim(),
                        weekBits = weekBitsStr,
                        dayOfWeek = dayOfWeek,
                        startSection = startSection,
                        endSection = endSection,
                        termCode = termCode,
                        note = note.trim()
                    )
                    onSave(entity)
                    show.value = false
                    onDismiss()
                },
                enabled = courseName.isNotBlank() && selectedWeeks.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isEdit) "保存" else "添加")
            }
        }
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun SectionPicker(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            shape = RoundedCornerShape(12.dp),
            color = MiuixTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$value", style = MiuixTheme.textStyles.body1, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..12).forEach { section ->
                AppDropdownMenuItem(
                    text = { Text("$section") },
                    onClick = { onValueChange(section); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun WeekCheckboxGrid(selectedWeeks: Set<Int>, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in listOf(1..10, 11..20)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (week in row) {
                    val isSelected = week in selectedWeeks
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 32.dp).clickable { onToggle(week) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MiuixTheme.colorScheme.onPrimary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("$week", style = MiuixTheme.textStyles.footnote1)
                        }
                    }
                }
            }
        }
    }
}
