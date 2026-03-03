package com.xjtu.toolbox.lms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjtu.toolbox.ui.components.AppFilterChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private const val TAG = "LmsScreen"

// ════════════════════════════════════════
//  导航状态
// ════════════════════════════════════════

private sealed class LmsPage {
    data object CourseList : LmsPage()
    data class ActivityList(val course: LmsCourseSummary) : LmsPage()
    data class ActivityDetail(val course: LmsCourseSummary, val activity: LmsActivity) : LmsPage()
}

// ════════════════════════════════════════
//  入口
// ════════════════════════════════════════

@Composable
fun LmsScreen(login: LmsLogin, onBack: () -> Unit) {
    val context = LocalContext.current
    val api = remember { LmsApi(login) }

    var currentPage by remember { mutableStateOf<LmsPage>(LmsPage.CourseList) }

    // 首次使用提示
    val prefs = remember { context.getSharedPreferences("feature_hints", Context.MODE_PRIVATE) }
    val showHint = remember { mutableStateOf(!prefs.getBoolean("lms_hint_shown", false)) }

    if (showHint.value) {
        BackHandler { showHint.value = false; prefs.edit().putBoolean("lms_hint_shown", true).apply() }
        SuperBottomSheet(
            show = showHint,
            title = "功能说明",
            onDismissRequest = {
                showHint.value = false
                prefs.edit().putBoolean("lms_hint_shown", true).apply()
            }
        ) {
            Column(Modifier.padding(bottom = 16.dp).navigationBarsPadding()) {
                Text(
                    "思源学堂（lms.xjtu.edu.cn）是学校新一代课程管理平台，数据来源为 LMS 系统。",
                    style = MiuixTheme.textStyles.body1
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "支持查看课程列表、作业提交记录、课件附件以及课堂回放视频。\n" +
                        "课堂回放下载链接可在浏览器中打开下载。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        showHint.value = false
                        prefs.edit().putBoolean("lms_hint_shown", true).apply()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("知道了") }
            }
        }
    }

    // 返回处理
    BackHandler(enabled = currentPage !is LmsPage.CourseList) {
        currentPage = when (currentPage) {
            is LmsPage.ActivityDetail -> LmsPage.ActivityList((currentPage as LmsPage.ActivityDetail).course)
            is LmsPage.ActivityList -> LmsPage.CourseList
            else -> LmsPage.CourseList
        }
    }

    AnimatedContent(
        targetState = currentPage,
        transitionSpec = {
            val forward = when {
                targetState is LmsPage.ActivityList && initialState is LmsPage.CourseList -> true
                targetState is LmsPage.ActivityDetail && initialState is LmsPage.ActivityList -> true
                else -> false
            }
            if (forward) {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it / 3 } + fadeOut())
            } else {
                (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { it / 3 } + fadeOut())
            }
        },
        label = "LmsPage"
    ) { page ->
        when (page) {
            is LmsPage.CourseList -> CourseListPage(
                api = api,
                onBack = onBack,
                onCourseSelected = { currentPage = LmsPage.ActivityList(it) }
            )
            is LmsPage.ActivityList -> ActivityListPage(
                api = api,
                course = page.course,
                onBack = { currentPage = LmsPage.CourseList },
                onActivitySelected = { currentPage = LmsPage.ActivityDetail(page.course, it) }
            )
            is LmsPage.ActivityDetail -> ActivityDetailPage(
                api = api,
                activity = page.activity,
                onBack = { currentPage = LmsPage.ActivityList(page.course) }
            )
        }
    }
}

// ════════════════════════════════════════
//  页面 1 — 课程列表
// ════════════════════════════════════════

@Composable
private fun CourseListPage(
    api: LmsApi,
    onBack: () -> Unit,
    onCourseSelected: (LmsCourseSummary) -> Unit
) {
    var courses by remember { mutableStateOf<List<LmsCourseSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedSemester by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadCourses() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                courses = withContext(Dispatchers.IO) { api.getMyCourses() }
            } catch (e: Exception) {
                Log.e(TAG, "loadCourses error", e)
                errorMsg = "加载课程失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadCourses() }

    val semesters = remember(courses) {
        courses.map { it.semesterLabel }.distinct().sortedDescending()
    }

    val filtered = remember(courses, selectedSemester) {
        if (selectedSemester == null) courses
        else courses.filter { it.semesterLabel == selectedSemester }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "思源学堂",
                color = MiuixTheme.colorScheme.surfaceVariant,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && courses.isEmpty() -> LoadingIndicator("加载课程列表…")
                errorMsg != null && courses.isEmpty() -> ErrorRetry(errorMsg!!) { loadCourses() }
                courses.isEmpty() -> EmptyState(Icons.Default.School, "没有课程", "暂未加入任何课程")
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (semesters.size > 1) {
                            item(key = "semester_filter") {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppFilterChip(selected = selectedSemester == null, onClick = { selectedSemester = null }, label = "全部")
                                    semesters.forEach { sem ->
                                        AppFilterChip(selected = selectedSemester == sem, onClick = { selectedSemester = sem }, label = sem)
                                    }
                                }
                            }
                        }
                        item(key = "count") {
                            Text(
                                "共 ${filtered.size} 门课程" + if (selectedSemester != null) " ($selectedSemester)" else "",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                        items(filtered, key = { it.id }) { course ->
                            LmsCourseCard(course) { onCourseSelected(course) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LmsCourseCard(course: LmsCourseSummary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    course.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                if (course.instructors.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, Modifier.size(14.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            course.instructorNames,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (course.department.name.isNotEmpty()) {
                        Text(course.department.name, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (course.credit.isNotEmpty()) {
                        Text("${course.credit} 学分", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    Text(course.semesterLabel, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(20.dp))
        }
    }
}

// ════════════════════════════════════════
//  页面 2 — 活动列表
// ════════════════════════════════════════

@Composable
private fun ActivityListPage(
    api: LmsApi,
    course: LmsCourseSummary,
    onBack: () -> Unit,
    onActivitySelected: (LmsActivity) -> Unit
) {
    var activities by remember { mutableStateOf<List<LmsActivity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<LmsActivityType?>(null) }
    val scope = rememberCoroutineScope()

    fun loadActivities() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                activities = withContext(Dispatchers.IO) { api.getCourseActivities(course.id) }
            } catch (e: Exception) {
                Log.e(TAG, "loadActivities error", e)
                errorMsg = "加载活动失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadActivities() }

    val types = remember(activities) {
        activities.map { it.type }.distinct().sortedBy { it.ordinal }
    }

    val filtered = remember(activities, selectedType) {
        if (selectedType == null) activities
        else activities.filter { it.type == selectedType }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = course.name,
                color = MiuixTheme.colorScheme.surfaceVariant,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading && activities.isEmpty() -> LoadingIndicator("加载活动列表…")
                errorMsg != null && activities.isEmpty() -> ErrorRetry(errorMsg!!) { loadActivities() }
                activities.isEmpty() -> EmptyState(Icons.Default.Inbox, "暂无活动", "该课程还没有发布任何活动")
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (types.size > 1) {
                            item(key = "type_filter") {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppFilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = "全部")
                                    types.forEach { type ->
                                        AppFilterChip(
                                            selected = selectedType == type,
                                            onClick = { selectedType = type },
                                            label = type.displayName()
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "count") {
                            Text(
                                "共 ${filtered.size} 个活动",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                        items(filtered, key = { it.id }) { activity ->
                            LmsActivityCard(activity) { onActivitySelected(activity) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LmsActivityCard(activity: LmsActivity, onClick: () -> Unit) {
    val (icon, color) = activityTypeVisual(activity.type)
    Card(
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    activity.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        activity.type.displayName(),
                        fontSize = 12.sp,
                        color = color
                    )
                    activity.startTime?.let {
                        Text(
                            formatLmsTime(it),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

// ════════════════════════════════════════
//  页面 3 — 活动详情
// ════════════════════════════════════════

@Composable
private fun ActivityDetailPage(
    api: LmsApi,
    activity: LmsActivity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<LmsActivity?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadDetail() {
        scope.launch {
            isLoading = true
            errorMsg = null
            try {
                detail = withContext(Dispatchers.IO) { api.getActivityDetail(activity.id) }
            } catch (e: Exception) {
                Log.e(TAG, "loadDetail error", e)
                errorMsg = "加载详情失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadDetail() }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = activity.title,
                color = MiuixTheme.colorScheme.surfaceVariant,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> LoadingIndicator("加载活动详情…")
                errorMsg != null -> ErrorRetry(errorMsg!!) { loadDetail() }
                detail != null -> {
                    val d = detail!!
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // 基本信息卡
                        item(key = "info") { ActivityInfoCard(d) }

                        // 作业描述
                        if (!d.description.isNullOrBlank()) {
                            item(key = "desc") {
                                SectionHeader("作业描述")
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text(
                                        d.description!!,
                                        style = MiuixTheme.textStyles.body2,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        // 附件
                        if (d.uploads.isNotEmpty()) {
                            item(key = "uploads_header") { SectionHeader("附件 (${d.uploads.size})") }
                            items(d.uploads, key = { "upload_${it.id}" }) { upload ->
                                UploadCard(upload, context)
                            }
                        }

                        // 作业提交记录
                        if (d.type == LmsActivityType.HOMEWORK && d.submissionList != null) {
                            val submissions = d.submissionList!!
                            if (submissions.list.isNotEmpty()) {
                                item(key = "sub_header") { SectionHeader("提交记录 (${submissions.list.size})") }
                                items(submissions.list, key = { "sub_${it.id}" }) { sub ->
                                    SubmissionCard(sub, context)
                                }
                            }
                        }

                        // 课堂回放
                        if (d.type == LmsActivityType.LESSON && d.replayVideos.isNotEmpty()) {
                            item(key = "replay_header") { SectionHeader("课堂回放 (${d.replayVideos.size})") }
                            items(d.replayVideos, key = { "replay_${it.id}" }) { video ->
                                ReplayVideoCard(video, context)
                            }
                        }

                        // 直播链接
                        if (d.type == LmsActivityType.LECTURE_LIVE) {
                            item(key = "live_header") { SectionHeader("直播信息") }
                            item(key = "live_info") { LiveInfoCard(d, context) }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════
//  详情子组件
// ════════════════════════════════════════

@Composable
private fun ActivityInfoCard(activity: LmsActivity) {
    val (icon, color) = activityTypeVisual(activity.type)
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(activity.type.displayName(), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = color)
            }
            Spacer(Modifier.height(8.dp))
            Text(activity.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            if (activity.startTime != null || activity.endTime != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        buildString {
                            activity.startTime?.let { append(formatLmsTime(it)) }
                            activity.endTime?.let { append(" ~ "); append(formatLmsTime(it)) }
                        },
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            // 作业特有信息
            if (activity.type == LmsActivityType.HOMEWORK) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (activity.submitByGroup) {
                        InfoChip("小组提交", Icons.Default.Group)
                    }
                    if (activity.userSubmitCount > 0) {
                        InfoChip("已提交 ${activity.userSubmitCount} 次", Icons.Default.CheckCircle)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = MiuixTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 12.sp, color = MiuixTheme.colorScheme.primary)
    }
}

@Composable
private fun UploadCard(upload: LmsUpload, context: Context) {
    Card(
        onClick = {
            val url = upload.downloadUrl.ifEmpty { upload.previewUrl }
            if (url.isNotEmpty()) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
            }
        },
        pressFeedbackType = PressFeedbackType.Sink,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(fileTypeIcon(upload.type), null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(upload.name, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (upload.readableSize.isNotEmpty()) {
                    Text(upload.readableSize, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Icon(Icons.Default.Download, null, tint = MiuixTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SubmissionCard(sub: LmsSubmissionItem, context: Context) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (sub.status) {
                        "graded" -> Color(0xFF4CAF50)
                        "submitted" -> MiuixTheme.colorScheme.primary
                        "returned" -> Color(0xFFFF9800)
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                    }
                    Text(sub.statusLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = statusColor)
                    if (sub.isResubmitted) {
                        Spacer(Modifier.width(8.dp))
                        Text("(重新提交)", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
                Text(sub.scoreDisplay, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.primary)
            }

            if (sub.submittedAt != null) {
                Spacer(Modifier.height(4.dp))
                Text("提交于 ${formatLmsTime(sub.submittedAt!!)}", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }

            if (sub.content.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(sub.content, fontSize = 13.sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
            }

            if (sub.instructorComment.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Comment, null, Modifier.size(14.dp), tint = Color(0xFFFF9800))
                    Spacer(Modifier.width(4.dp))
                    Text("教师评语: ${sub.instructorComment}", fontSize = 13.sp, color = Color(0xFFFF9800))
                }
            }

            // 批改附件
            val correctUploads = sub.submissionCorrect.uploads
            if (correctUploads.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("批改附件", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                correctUploads.forEach { upload ->
                    UploadCard(upload, context)
                }
            }

            // 提交附件
            if (sub.uploads.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("提交附件", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                sub.uploads.forEach { upload ->
                    UploadCard(upload, context)
                }
            }
        }
    }
}

@Composable
private fun ReplayVideoCard(video: LmsReplayVideo, context: Context) {
    Card(
        onClick = {
            if (video.downloadUrl.isNotEmpty()) {
                try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.downloadUrl))) } catch (_: Exception) {}
            }
        },
        pressFeedbackType = PressFeedbackType.Sink,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayCircle, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.readableLabel, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (video.readableSize.isNotEmpty()) {
                        Text(video.readableSize, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (video.mute) {
                        Text("静音", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                }
            }
            if (video.downloadUrl.isNotEmpty()) {
                Icon(Icons.Default.Download, null, tint = MiuixTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LiveInfoCard(activity: LmsActivity, context: Context) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (!activity.liveRoom.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MeetingRoom, null, Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Spacer(Modifier.width(6.dp))
                    Text("直播间: ${activity.liveRoom}", fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            if (!activity.viewLive.isNullOrBlank()) {
                Button(
                    onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.viewLive))) } catch (_: Exception) {} },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("观看直播") }
                Spacer(Modifier.height(8.dp))
            }
            if (!activity.viewRecord.isNullOrBlank()) {
                Button(
                    onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activity.viewRecord))) } catch (_: Exception) {} },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("观看录像") }
            }
        }
    }
}

// ════════════════════════════════════════
//  通用组件
// ════════════════════════════════════════

@Composable
private fun BoxScope.LoadingIndicator(text: String) {
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
        Text(text, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun BoxScope.ErrorRetry(message: String, onRetry: () -> Unit) {
    Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MiuixTheme.colorScheme.error)
        Spacer(Modifier.height(8.dp))
        TextButton(text = "重试", onClick = onRetry)
    }
}

@Composable
private fun BoxScope.EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = MiuixTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

// ════════════════════════════════════════
//  工具函数
// ════════════════════════════════════════

private fun LmsActivityType.displayName(): String = when (this) {
    LmsActivityType.HOMEWORK -> "作业"
    LmsActivityType.MATERIAL -> "资料"
    LmsActivityType.LESSON -> "课堂"
    LmsActivityType.LECTURE_LIVE -> "直播"
    LmsActivityType.UNKNOWN -> "其他"
}

private fun activityTypeVisual(type: LmsActivityType): Pair<ImageVector, Color> = when (type) {
    LmsActivityType.HOMEWORK -> Icons.AutoMirrored.Filled.Assignment to Color(0xFFE65100)
    LmsActivityType.MATERIAL -> Icons.Default.Description to Color(0xFF1565C0)
    LmsActivityType.LESSON -> Icons.Default.OndemandVideo to Color(0xFF512DA8)
    LmsActivityType.LECTURE_LIVE -> Icons.Default.LiveTv to Color(0xFFC62828)
    LmsActivityType.UNKNOWN -> Icons.Default.HelpOutline to Color(0xFF757575)
}

private fun fileTypeIcon(type: String): ImageVector = when {
    type.contains("pdf", true) -> Icons.Default.PictureAsPdf
    type.contains("image", true) || type.contains("png", true) || type.contains("jpg", true) -> Icons.Default.Image
    type.contains("video", true) -> Icons.Default.VideoFile
    type.contains("audio", true) -> Icons.Default.AudioFile
    type.contains("zip", true) || type.contains("rar", true) -> Icons.Default.FolderZip
    else -> Icons.Default.InsertDriveFile
}

/**
 * 格式化 LMS 时间字符串 (ISO 8601 → 友好显示)
 */
private fun formatLmsTime(raw: String): String {
    return try {
        val zdt = java.time.ZonedDateTime.parse(raw)
        zdt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
    } catch (_: Exception) {
        try {
            val ldt = java.time.LocalDateTime.parse(raw.replace(" ", "T"))
            ldt.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
        } catch (_: Exception) {
            raw.take(16).replace("T", " ")
        }
    }
}
