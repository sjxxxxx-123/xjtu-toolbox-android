package com.xjtu.toolbox.jwapp

import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.utils.overScrollVertical

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xjtu.toolbox.auth.JwxtLogin
import com.xjtu.toolbox.ui.components.LoadingState
import com.xjtu.toolbox.ui.components.ErrorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 培养进度追踪页面
 */
@Composable
fun CurriculumScreen(
    jwxtLogin: JwxtLogin,
    jwappLogin: com.xjtu.toolbox.auth.JwappLogin? = null,
    studentId: String = "",
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<CurriculumProgress?>(null) }

    // 展开状态
    var expandedSection by remember { mutableStateOf<String?>("overview") }

    fun loadData() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val pyfaApi = PyfaApi(jwxtLogin)

                    // 获取已修成绩
                    val allScores = mutableListOf<ScoreItem>()
                    if (jwappLogin != null) {
                        try {
                            val jwappApi = JwappApi(jwappLogin)
                            val grades = jwappApi.getGrade(null)
                            allScores.addAll(grades.flatMap { it.scoreList })
                        } catch (e: Exception) {
                            android.util.Log.w("Curriculum", "JWAPP 成绩获取失败: ${e.message}")
                        }
                    }
                    // CjcxApi enrichment
                    try {
                        val cjcxApi = CjcxApi(jwxtLogin)
                        val precise = cjcxApi.getAllScores()
                        val lookup = cjcxApi.buildLookup(precise)
                        val preciseByKch = precise.associateBy { it.kch }

                        // 给 JWAPP 成绩补充 courseCategory + courseCode
                        for (i in allScores.indices) {
                            val score = allScores[i]
                            val key = "${score.termCode}|${CjcxApi.normalizeName(score.courseName)}"
                            val match = lookup[key]
                                ?: score.courseCode?.let { preciseByKch[it] }
                            if (match != null) {
                                allScores[i] = score.copy(
                                    scoreValue = match.zcj,
                                    gpa = match.xfjd,
                                    courseCategory = match.kclbdm.ifBlank { null },
                                    courseCode = match.kch.ifBlank { score.courseCode }
                                )
                            }
                        }

                        // 补充 CjcxApi 独有的课程
                        val existingKeys = allScores.map {
                            "${it.termCode}|${CjcxApi.normalizeName(it.courseName)}"
                        }.toSet()
                        for (cs in precise) {
                            val key = "${cs.termCode}|${CjcxApi.normalizeName(cs.courseName)}"
                            if (key !in existingKeys) {
                                allScores.add(ScoreItem(
                                    id = "cjcx_${cs.termCode}_${cs.kch}",
                                    termCode = cs.termCode,
                                    courseName = cs.courseName,
                                    score = cs.zcj.toString(),
                                    scoreValue = cs.zcj,
                                    passFlag = cs.passFlag,
                                    specificReason = null,
                                    coursePoint = cs.xf,
                                    examType = "",
                                    majorFlag = null,
                                    examProp = cs.examProp,
                                    replaceFlag = false,
                                    gpa = cs.xfjd,
                                    courseCategory = cs.kclbdm.ifBlank { null },
                                    courseCode = cs.kch.ifBlank { null }
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("Curriculum", "CjcxApi 获取失败: ${e.message}")
                    }

                    // 获取当前学期
                    val currentTerm = try {
                        if (jwappLogin != null) JwappApi(jwappLogin).getTimeTableBasis().termCode
                        else "2024-2025-2"
                    } catch (_: Exception) { "2024-2025-2" }

                    progress = pyfaApi.buildProgress(allScores, currentTerm)
                }
            } catch (e: Exception) {
                errorMessage = "加载培养方案失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadData() }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            TopAppBar(
                title = "培养进度",
                largeTitle = "培养进度",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!isLoading) {
                        IconButton(onClick = { loadData() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> LoadingState(message = "正在加载培养方案...", modifier = Modifier.fillMaxSize().padding(padding))
            errorMessage != null -> ErrorState(message = errorMessage!!, onRetry = { loadData() }, modifier = Modifier.fillMaxSize().padding(padding))
            progress != null -> {
                val p = progress!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).nestedScroll(scrollBehavior.nestedScrollConnection).overScrollVertical().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // ── 概览卡片 ──
                    item {
                        ProgressOverviewCard(
                            summary = p.summary,
                            insufficientCount = p.insufficientGroups.size,
                            groupTree = p.groupTree,
                            creditMap = p.groupCreditMap
                        )
                    }

                    // ── 提醒 ──
                    item {
                        Text(
                            "⚠ 本页严格基于方案树课程号匹配，通识选修要求、模块学分认定等不在其内，毕业结论请以教务系统为准。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // ── 学分进度条（按门槛 capped 进度）──
                    item {
                        val cappedTotal = p.groupTree.sumOf { root ->
                            p.groupCreditMap[root.kzh]?.cappedCredits ?: 0.0
                        }
                        CreditProgressCard(
                            completedCredits = cappedTotal,
                            totalRequired = p.summary.totalRequired
                        )
                    }

                    // ── 学分不足模块 ──
                    if (p.insufficientGroups.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "学分不足 (${p.insufficientGroups.size})",
                                icon = Icons.Default.Warning,
                                color = MiuixTheme.colorScheme.error,
                                expanded = expandedSection == "insufficient",
                                onToggle = { expandedSection = if (expandedSection == "insufficient") null else "insufficient" }
                            )
                        }
                        if (expandedSection == "insufficient") {
                            items(p.insufficientGroups, key = { it.group.kzh }) { deficit ->
                                InsufficientGroupCard(deficit)
                            }
                        }
                    }

                    // ── 方案外课程 ──
                    if (p.outOfPlanCourses.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "方案外课程 (${p.outOfPlanCourses.size})",
                                icon = Icons.Default.CallMissedOutgoing,
                                color = MiuixTheme.colorScheme.primaryVariant,
                                expanded = expandedSection == "outOfPlan",
                                onToggle = { expandedSection = if (expandedSection == "outOfPlan") null else "outOfPlan" }
                            )
                        }
                        if (expandedSection == "outOfPlan") {
                            items(p.outOfPlanCourses, key = { it.id }) { score ->
                                OutOfPlanCourseCard(score)
                            }
                        }
                    }

                    // ── 课组树进度 ──
                    item {
                        SectionHeader(
                            title = "课组进度 (${p.groupTree.size} 大类)",
                            icon = Icons.Default.AccountTree,
                            color = MiuixTheme.colorScheme.primary,
                            expanded = expandedSection == "tree",
                            onToggle = { expandedSection = if (expandedSection == "tree") null else "tree" }
                        )
                    }
                    if (expandedSection == "tree") {
                        p.groupTree.forEach { root ->
                            item(key = "root_${root.kzh}") {
                                GroupTreeNode(
                                    node = root,
                                    depth = 0,
                                    creditMap = p.groupCreditMap
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ── 概览卡片 ──────────────────────────────

@Composable
fun ProgressOverviewCard(
    summary: PyfaSummary,
    insufficientCount: Int,
    groupTree: List<CourseGroupNode>,
    creditMap: Map<String, GroupCreditStatus>
) {
    // 方案内实修学分（各根节点 raw 加总，含超出门槛的部分）
    val inPlanRawCredits = groupTree.sumOf { root ->
        creditMap[root.kzh]?.completedCredits ?: 0.0
    }
    // 按门槛 capped 的进度学分
    val cappedProgress = groupTree.sumOf { root ->
        creditMap[root.kzh]?.cappedCredits ?: 0.0
    }
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                summary.pyfamc,
                style = MiuixTheme.textStyles.subtitle,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${summary.college} · ${summary.className}",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatColumn("方案内已修", "%.1f".format(inPlanRawCredits), MiuixTheme.colorScheme.onPrimaryContainer)
                StatColumn("总需学分", "%.0f".format(summary.totalRequired), MiuixTheme.colorScheme.onPrimaryContainer)
                StatColumn("待补学分", if (insufficientCount > 0) "$insufficientCount 组" else "✓", MiuixTheme.colorScheme.onPrimaryContainer)
                StatColumn("门槛进度", "%.0f%%".format(cappedProgress / summary.totalRequired * 100), MiuixTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MiuixTheme.textStyles.footnote1, color = color.copy(alpha = 0.7f))
    }
}

// ── 学分进度条 ──────────────────────────────

@Composable
fun CreditProgressCard(completedCredits: Double, totalRequired: Double) {
    val ratio = (completedCredits / totalRequired).toFloat().coerceIn(0f, 1f)
    top.yukonga.miuix.kmp.basic.Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("门槛进度（按模块最低要求 cap）", style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium)
                Text("%.1f / %.0f".format(completedCredits, totalRequired),
                    style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = ratio,
                modifier = Modifier.fillMaxWidth(),
                height = 12.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(backgroundColor = MiuixTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

// ── 区块标题 ──────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onToggle() },
        pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = color.copy(alpha = 0.15f))
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.Medium, color = color, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── 学分不足课组卡片 ──────────────────────────────

@Composable
fun InsufficientGroupCard(status: GroupCreditStatus) {
    val deficit = status.requiredCredits - status.cappedCredits   // 按门槛 cap 后的差额
    val ratio = (status.cappedCredits / status.requiredCredits).toFloat().coerceIn(0f, 1f)
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, null, tint = MiuixTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(status.group.name, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium)
                    Text(
                        if (status.isOpen) "开放选课 · 还需 %.1f 学分".format(deficit)
                        else "还需 %.1f 学分 · 进度 %.1f/%.1f".format(deficit, status.cappedCredits, status.requiredCredits),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = ratio,
                modifier = Modifier.fillMaxWidth(),
                height = 6.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = MiuixTheme.colorScheme.error, backgroundColor = MiuixTheme.colorScheme.errorContainer),
            )
        }
    }
}

// ── 方案外课程卡片 ──────────────────────────────

@Composable
fun OutOfPlanCourseCard(score: ScoreItem) {
    top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(score.courseName, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium)
                Text(
                    "${score.coursePoint}学分 · ${score.termCode}",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(score.score, style = MiuixTheme.textStyles.subtitle, fontWeight = FontWeight.Bold,
                    color = if (score.passFlag) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error)
                Surface(shape = RoundedCornerShape(4.dp), color = MiuixTheme.colorScheme.outline.copy(alpha = 0.3f)) {
                    Text("方案外", style = MiuixTheme.textStyles.footnote1, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
        }
    }
}

// ── 课组树节点 ──────────────────────────────

@Composable
fun GroupTreeNode(
    node: CourseGroupNode,
    depth: Int,
    creditMap: Map<String, GroupCreditStatus>
) {
    var expanded by remember { mutableStateOf(depth == 0) }

    val status = creditMap[node.kzh]
    val completedCredits = status?.completedCredits ?: 0.0
    val cappedCredits = status?.cappedCredits ?: 0.0
    val hasMinRequirement = node.minCredits > 0
    val isOpen = status?.isOpen == true
    val isSufficient = !hasMinRequirement || cappedCredits >= node.minCredits

    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .animateContentSize(),
        onClick = { expanded = !expanded },
        pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = when (depth) {
                0 -> MiuixTheme.colorScheme.surfaceVariant
                1 -> MiuixTheme.colorScheme.surfaceVariant
                else -> MiuixTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        node.name,
                        style = if (depth == 0) MiuixTheme.textStyles.body1 else MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium
                    )
                    val subtitle = buildString {
                        if (hasMinRequirement) {
                            append("%.1f/%.1f 学分".format(cappedCredits, node.minCredits))
                            if (completedCredits > cappedCredits) append("（实修%.1f）".format(completedCredits))
                            if (!isSufficient) append(" · 还需%.1f".format(node.minCredits - cappedCredits))
                        } else {
                            append("%.1f 学分".format(completedCredits))
                            if (node.totalCredits > 0) append("（可选%.1f）".format(node.totalCredits))
                            else append("（无最低要求）")
                        }
                        if (isOpen) append(" · 开放选课")
                    }
                    Text(
                        subtitle,
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (isSufficient) MiuixTheme.colorScheme.onSurfaceVariantSummary
                               else MiuixTheme.colorScheme.error
                    )
                }
                if (hasMinRequirement && node.minCredits > 0) {
                    val ratio = (cappedCredits / node.minCredits).toFloat().coerceIn(0f, 1f)
                    CircularProgressIndicator(
                        progress = ratio,
                        size = 32.dp,
                        strokeWidth = 3.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = if (isSufficient) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
                            backgroundColor = MiuixTheme.colorScheme.surfaceVariant
                        ),
                    )
                }
                if (node.children.isNotEmpty() || node.courses.isNotEmpty()) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 展开：显示子节点和课程
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 子课组
                    node.children.forEach { child ->
                        GroupTreeNode(node = child, depth = depth + 1, creditMap = creditMap)
                    }
                    // 固定课表的课程列表
                    if (!isOpen) {
                        val matchedNames = status?.matchedScores?.map { CjcxApi.normalizeName(it.courseName) }?.toSet() ?: emptySet()
                        node.courses.forEach { course ->
                            val isCompleted = CjcxApi.normalizeName(course.name) in matchedNames
                            PlanCourseRow(course, isCompleted)
                        }
                    } else {
                        // 开放选课组显示已修课程
                        status?.matchedScores?.forEach { score ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(score.courseName, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.weight(1f))
                                Text("${score.coursePoint}分", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanCourseRow(course: PlanCourse, isCompleted: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isCompleted) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline
        )
        Spacer(Modifier.width(8.dp))
        Text(
            course.name,
            style = MiuixTheme.textStyles.footnote1,
            modifier = Modifier.weight(1f),
            color = if (isCompleted) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            "${course.credits}分",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )
        Text(
            course.requiredFlag,
            style = MiuixTheme.textStyles.footnote1,
            color = if (course.requiredFlag == "必修") MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.primaryVariant,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

