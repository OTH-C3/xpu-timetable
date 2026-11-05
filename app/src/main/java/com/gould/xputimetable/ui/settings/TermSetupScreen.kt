/*
 * TermSetupScreen.kt —— 「学期设置」二级页（M9；取代旧的 TermSettingsSection.kt）
 *
 * 两个入口共用本页：
 *   ① 课表空态「创建本学期」（fromEmptyState = true）：**进页即弹日期选择器**，
 *      用户选起始日即创建学期（旧实现写死"本周一 + 18 周"，起始日错了周次全错）；
 *   ② 「我的」→「学期设置」（编辑语义）：改起始日 / 总周数即**自动保存**，无保存按钮。
 *
 * 自动保存：VM 内去抖 350ms（连点步进器只写最后一次）。保存成功在页内以一行小字
 * 「已自动保存」短暂反馈（不弹提示，避免每改一次就打扰）；失败才走全局底部提示。
 *
 * 界面铁律：Scaffold + 自绘顶栏 statusBarsPadding；文案文件级常量；图标只经 AppIcons。
 */package com.gould.xputimetable.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.WeekCalc
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.IconSize
import com.gould.xputimetable.ui.theme.ListRow
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneOffset

// ---------- 文件级文案常量 ----------
private const val TITLE = "学期设置"
private const val LABEL_START_DATE = "起始日（第一周周一）"
private const val LABEL_WEEKS = "总周数"
private const val VALUE_PICK_DATE = "点击选择"
private const val HINT_CREATE_ON_SELECT = "选好起始日即创建本学期；总周数稍后可改"
private const val HINT_AUTO_SAVED = "已自动保存"
private const val HINT_TODAY_WEEK_PREFIX = "今天是第 "
private const val HINT_TODAY_WEEK_SUFFIX = " 周"
private const val HINT_BEFORE_START = "起始日在未来（尚未开学）"
private const val HINT_AFTER_END = "本学期已结束（今天已是第 "
private const val HINT_TERM_CREATED = "学期已创建"
private const val CD_BACK = "返回"
private const val CD_WEEKS_MINUS = "减少总周数"
private const val CD_WEEKS_PLUS = "增加总周数"
private const val SUFFIX_WEEK = " 周"
private const val ACTION_CONFIRM = "确定"
private const val ACTION_CANCEL = "取消"
private const val TNUM = "tnum"
private const val MILLIS_PER_DAY = 86_400_000L
private const val SAVED_FLAG_MILLIS = 1_600L

@Composable
internal fun TermSetupScreen(
    viewModel: SettingsViewModel,
    /** true = 从课表空态的「创建本学期」进来：创建成功后回课表。 */
    fromEmptyState: Boolean,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    onShowHint: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var savedFlagVisible by remember { mutableStateOf(false) }
    var announcedOnce by remember { mutableStateOf(false) }

    // 创建语义：进页即弹日期选择器（产品负责人要求"直接让选择学期起始日"）
    var autoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) {
        if (!state.loading && !autoOpened && !state.hasTerm && state.startDate.isBlank()) {
            autoOpened = true
            showDatePicker = true
        }
    }

    // 自动保存反馈：首次保存 = 创建（全局提示 + 回课表）；之后只在页内显示「已自动保存」
    LaunchedEffect(state.savedTick) {
        if (state.savedTick == 0) return@LaunchedEffect
        if (!announcedOnce) {
            announcedOnce = true
            onShowHint(HINT_TERM_CREATED)
            if (fromEmptyState) onCreated()
            return@LaunchedEffect
        }
        savedFlagVisible = true
        delay(SAVED_FLAG_MILLIS)
        savedFlagVisible = false
    }

    // 保存失败等必须让用户知道的信息 → 全局底部提示
    LaunchedEffect(state.error) {
        state.error?.let {
            onShowHint(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        // M12 需求一.2：底色与背景图由导航根的 PageBackground 统一提供，这里必须透明
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            TermSetupTopBar(onBack = onBack)
            if (state.loading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            Text(
                text = if (state.hasTerm) state.termName else HINT_CREATE_ON_SELECT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            SettingsNavRow(
                title = LABEL_START_DATE,
                subtitle = state.startDate.ifBlank { VALUE_PICK_DATE },
                onClick = { showDatePicker = true },
            )
            WeeksStepperRow(
                weeks = state.totalWeeks,
                onAdjust = viewModel::adjustWeeks,
            )

            Spacer(Modifier.height(12.dp))
            TermWeekHint(startDate = state.startDate, totalWeeks = state.totalWeeks)
            if (savedFlagVisible) {
                Text(
                    text = HINT_AUTO_SAVED,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(state.startDate) {
            runCatching { LocalDate.parse(state.startDate) }
                .getOrNull()
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()?.toEpochMilli()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        viewModel.selectStartDate(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY).toString())
                    }
                    showDatePicker = false
                }) { Text(ACTION_CONFIRM) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(ACTION_CANCEL) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** 顶栏：返回箭头 + 标题（二级页需要返回控件，不能用系统返回键当唯一退路）。 */
@Composable
private fun TermSetupTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(AppIcons.chevronLeft),
                contentDescription = CD_BACK,
                modifier = Modifier.size(IconSize.Medium),
            )
        }
        Text(text = TITLE, style = MaterialTheme.typography.titleLarge)
    }
}

/** 总周数步进器：每次点击即改即（去抖）自动保存。 */
@Composable
private fun WeeksStepperRow(weeks: Int, onAdjust: (Int) -> Unit) {
    // 等宽数字对齐（与 WeekGrid 同口径）：fontFeatureSettings 接收的是特性字符串
    val valueStyle = MaterialTheme.typography.titleMedium.copy(
        fontFeatureSettings = TNUM,
        fontWeight = FontWeight.SemiBold,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ListRow.MinHeight)
            .padding(horizontal = ListRow.HorizontalPadding, vertical = ListRow.VerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = LABEL_WEEKS,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onAdjust(-1) },
            enabled = weeks > MIN_TOTAL_WEEKS,
        ) {
            Icon(
                painter = painterResource(AppIcons.minus),
                contentDescription = CD_WEEKS_MINUS,
                modifier = Modifier.size(IconSize.Medium),
            )
        }
        Text(text = "$weeks$SUFFIX_WEEK", style = valueStyle)
        IconButton(
            onClick = { onAdjust(1) },
            enabled = weeks < MAX_TOTAL_WEEKS,
        ) {
            Icon(
                painter = painterResource(AppIcons.plus),
                contentDescription = CD_WEEKS_PLUS,
                modifier = Modifier.size(IconSize.Medium),
            )
        }
    }
}

/** 起始日校正辅助：让用户一眼看出"选得对不对"（周次判定完全依赖起始日）。 */
@Composable
private fun TermWeekHint(startDate: String, totalWeeks: Int) {
    if (startDate.isBlank()) return
    val text = remember(startDate, totalWeeks) {
        runCatching { LocalDate.parse(startDate) }.getOrNull()?.let { start ->
            val auto = WeekCalc.currentWeek(start, LocalDate.now())
            when {
                auto == null -> HINT_BEFORE_START
                auto > totalWeeks -> "$HINT_AFTER_END$auto$HINT_TODAY_WEEK_SUFFIX）"
                else -> "$HINT_TODAY_WEEK_PREFIX$auto$HINT_TODAY_WEEK_SUFFIX"
            }
        }.orEmpty()
    }
    if (text.isBlank()) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
