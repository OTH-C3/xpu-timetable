/*
 * Destinations.kt —— 导航目标类型与目的地装配（M6 自 AppNav.kt 拆出，守 300 行门禁）
 *
 * 内容：EditTarget / AppScreen（M9 增 TermSetup 与 ProfileSub）/ simpleFactory 工厂 /
 * 三个「我的」相关目的地（根页 Profile / 学期设置 TermSetup / 二级页 ProfileSub）。
 * 与 AppNav.kt 同包（ui.navigation），互引无需 import。
 *
 * M9：导入中心的 WakeUp CSV 通道已删除（产品负责人要求）——本文件不再需要 wakeupImporter。
 */
package com.gould.xputimetable.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gould.xputimetable.data.prefs.BackgroundPrefs
import com.gould.xputimetable.data.prefs.UiPrefs
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.importer.api.ImportResult
import com.gould.xputimetable.importer.api.ScheduleImporter
import com.gould.xputimetable.ui.import_.ImportHubScreen
import com.gould.xputimetable.ui.import_.ImportHubViewModel
import com.gould.xputimetable.ui.import_.readImportPayload
import com.gould.xputimetable.ui.settings.ProfileSubPage
import com.gould.xputimetable.ui.settings.ProfileSubScreen
import com.gould.xputimetable.ui.settings.SettingsScreen
import com.gould.xputimetable.ui.settings.SettingsViewModel
import com.gould.xputimetable.ui.settings.TermSetupScreen
import com.gould.xputimetable.ui.transfer.readQrImagePayload
import kotlinx.coroutines.launch

// M6：JSON 文件与二维码截图的 SAF 选择 MIME（JSON 放宽到 text/plain 与 octet-stream：
// 实测部分文件管理器把 .json 报成 octet-stream）
private val MIME_JSON_FILES = arrayOf("application/json", "text/plain", "application/octet-stream")
private val MIME_IMAGES = arrayOf("image/*")

/** 编辑目标：New = 新增课程；Edit = 编辑既有课程。 */
internal sealed interface EditTarget {
    data class New(val termId: Long?, val week: Int, val totalWeeks: Int) : EditTarget
    data class Edit(val courseId: String, val sessionId: Long) : EditTarget
}

/** 全部页面状态（M9 起十状态：新增学期设置 TermSetup 与「我的」二级页 ProfileSub）。 */
internal sealed interface AppScreen {
    data object Timetable : AppScreen
    data class CourseEdit(val target: EditTarget) : AppScreen
    data object ImportHub : AppScreen
    data object CourseCapture : AppScreen
    data object Cleanup : AppScreen
    data class ImportPreview(val needsConfirm: ImportResult.NeedsConfirm) : AppScreen
    data object Profile : AppScreen
    data object QrShare : AppScreen

    /** 待办清单页（M12 需求五）：底栏第三项，对应「便签」这一档背景作用域。 */
    data object Note : AppScreen

    /**
     * 学期设置页（M9）：起始日 + 总周数，改完**自动保存**。
     * [fromEmptyState] = true 表示从课表空态的「创建本学期」进来（创建成功后回课表）。
     */
    data class TermSetup(val fromEmptyState: Boolean) : AppScreen

    /** 「我的」页的二级页（M9）：权限 / 关于。 */
    data class ProfileSub(val page: ProfileSubPage) : AppScreen
}

/** 简易 ViewModel 工厂（避免为每个 ViewModel 各写一个匿名对象）。 */
internal inline fun <reified VM : ViewModel> simpleFactory(crossinline create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }

/**
 * 「我的」/「学期设置」/「我的二级页」共用同一个 SettingsViewModel。
 *
 * 这些页面在同一个 ViewModelStore（Activity）下取同名 VM，因此拿到的是**同一实例** ——
 * 于是"在学期设置页改完 → 回到我的页立刻看到新值"无需任何额外同步。
 * 也正因如此，三个入口都要传真实的 canScheduleExact / onDataChanged，
 * 否则先进入的页面用空实现创建了 VM，后进入的页面会一直用那个空实现。
 */
@Composable
private fun settingsViewModel(
    repository: TimetableRepository,
    canScheduleExact: () -> Boolean,
    onDataChanged: suspend () -> Unit,
    uiPrefs: UiPrefs,
): SettingsViewModel = viewModel(
    factory = simpleFactory {
        SettingsViewModel(
            repository = repository,
            canScheduleExact = canScheduleExact,
            onTermChanged = onDataChanged,
            showTeacherFlow = uiPrefs.showTeacher,
            saveShowTeacher = uiPrefs::setShowTeacher,
        )
    },
)

/** 「我的」页根页目的地（M9：分组列表 + 二级页入口）。根页无返回箭头。 */
@Composable
internal fun ProfileDestination(
    repository: TimetableRepository,
    canScheduleExact: () -> Boolean,
    onDataChanged: suspend () -> Unit,
    /** M11：界面偏好（「显示老师姓名」）。 */
    uiPrefs: UiPrefs,
    /** M12：背景偏好（自定义背景入口）。 */
    backgroundPrefs: BackgroundPrefs,
    onShowHint: (String) -> Unit,
    onOpenTermSetup: () -> Unit,
    onOpenSubPage: (ProfileSubPage) -> Unit,
    onShareQr: () -> Unit,
) {
    SettingsScreen(
        viewModel = settingsViewModel(repository, canScheduleExact, onDataChanged, uiPrefs),
        repository = repository,
        backgroundPrefs = backgroundPrefs,
        onOpenTermSetup = onOpenTermSetup,
        onOpenSubPage = onOpenSubPage,
        onShareQr = onShareQr,
        onShowHint = onShowHint,
    )
}

/** 「我的」页二级页目的地（权限 / 关于）。 */
@Composable
internal fun ProfileSubDestination(
    page: ProfileSubPage,
    repository: TimetableRepository,
    canScheduleExact: () -> Boolean,
    onDataChanged: suspend () -> Unit,
    uiPrefs: UiPrefs,
    backgroundPrefs: BackgroundPrefs,
    onShowHint: (String) -> Unit,
    onBack: () -> Unit,
) {
    ProfileSubScreen(
        page = page,
        viewModel = settingsViewModel(repository, canScheduleExact, onDataChanged, uiPrefs),
        backgroundPrefs = backgroundPrefs,
        uiPrefs = uiPrefs,
        repository = repository,
        onShowHint = onShowHint,
        onBack = onBack,
    )
}

/** 学期设置页目的地（M9）：创建与编辑共用，自动保存。 */
@Composable
internal fun TermSetupDestination(
    fromEmptyState: Boolean,
    repository: TimetableRepository,
    canScheduleExact: () -> Boolean,
    onDataChanged: suspend () -> Unit,
    uiPrefs: UiPrefs,
    onShowHint: (String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    TermSetupScreen(
        viewModel = settingsViewModel(repository, canScheduleExact, onDataChanged, uiPrefs),
        fromEmptyState = fromEmptyState,
        onBack = onBack,
        onCreated = onDone,
        onShowHint = onShowHint,
    )
}

/** 导入中心目的地（M6 起含 JSON 文件与二维码截图两条新通道；SAF launcher 在此装配）。 */
@Composable
internal fun ImportHubDestination(
    repository: TimetableRepository,
    jsonFileImporter: ScheduleImporter,
    notice: String?,
    onNoticeShown: () -> Unit,
    onBack: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenCleanup: () -> Unit,
    onManualAdd: (termId: Long?, totalWeeks: Int) -> Unit,
    onParsed: (ImportResult.NeedsConfirm) -> Unit,
) {
    val vm: ImportHubViewModel = viewModel(
        factory = simpleFactory { ImportHubViewModel(jsonFileImporter, repository) },
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // M6 需求 6：两条新通道的 SAF launcher；读取/解图在 UI 层完成后
    // 以普通文本载荷走 submitJson（importer 不感知二维码）
    val pickJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val payload = readImportPayload(context, uri)
            if (payload == null) vm.markReadFailure(null) else vm.submitJson(payload)
        }
    }
    val pickQrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val payload = readQrImagePayload(context, uri)
            if (payload == null) vm.markReadFailure(null) else vm.submitJson(payload)
        }
    }
    ImportHubScreen(
        viewModel = vm,
        onBack = onBack,
        onOpenWeb = onOpenWeb,
        onOpenCleanup = onOpenCleanup,
        onManualAdd = onManualAdd,
        onPickJsonFile = { pickJsonLauncher.launch(MIME_JSON_FILES) },
        onPickQrImage = { pickQrLauncher.launch(MIME_IMAGES) },
        onParsed = onParsed,
        notice = notice,
        onNoticeShown = onNoticeShown,
    )
}
