/*
 * BackgroundScreen.kt —— 自定义背景二级页（M12 需求二）
 *
 * 需求原文对应的三件事：
 *   1. 「支持选择或上传图片并设为全局背景」→ SAF 选图（OpenDocument，MIME 传 image/ 加通配符）；
 *   2. 「提供透明度调节滑块；拖动滑块时全屏实时预览课表主页（只显示课表主页内容和透明度条），
 *      松手即应用当前透明度」→ [BackgroundOpacitySheet] 这条全屏覆盖层；
 *   3. 「可以选择自定义背景应用在哪个页面，便签，课表，我的，全局」→ [BackgroundScopeSection]
 *      这一栏：四项都可勾选（原先是四选一），且「全局」是与其他档位并列的一条普通选项；
 *   4. M13「为白色UI方框增加透明度调节」→ 卡片不透明度，与上面第 2 条那档背景图不透明度
 *      是两码事（图有多淡 / 方框有多透），故单独一条入口，共用同一套全屏预览壳。
 *
 * ⚠️ 选图后必须取持久化读权限（takePersistableUriPermission）：
 *   SAF 返回的是临时授权，进程被杀后就读不到图，表现为"重启后背景图消失"。
 *   ACTION_OPEN_DOCUMENT 明确支持 persistable grant，这是唯一能长期读到的办法。
 *
 * 关于"实时预览"的实现取舍：预览层渲染的是**真实的周视图**（TimetableScreen），
 * 而不是静态截图——这样拖动滑块时看到的就是真实叠加效果，所见即所得。
 */
package com.gould.xputimetable.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gould.xputimetable.data.prefs.BackgroundPrefs
import com.gould.xputimetable.data.prefs.DEFAULT_BACKGROUND_SETTINGS
import com.gould.xputimetable.data.prefs.UiPrefs
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.ui.components.GroupCard
import com.gould.xputimetable.ui.components.GroupHeader
import kotlinx.coroutines.launch

// ---------- 文件级文案常量 ----------
// 「作用范围」的标题与提示语在 BackgroundScopeSection.kt（那一栏自带文案）
private const val TITLE = "自定义背景"
private const val GROUP_IMAGE = "背景图片"
private const val ROW_PICK = "选择图片"
private const val ROW_CLEAR = "恢复默认"
private const val GROUP_OPACITY = "不透明度"
private const val GROUP_CARD = "卡片"
private const val OPACITY_VALUE = "%.0f%%"
private const val CARD_VALUE = "%.0f%%"
private const val HINT_NONE = "还没有选择图片，页面使用默认底色。"
private const val IMAGE_CHOSEN = "已选择"
private const val OPACITY_SUBTITLE = "点此全屏预览并实时调节"
private const val OPACITY_SUBTITLE_NO_IMAGE = "先在上面选一张图片"
private const val HINT_NO_IMAGE = "还没有背景图片，先在上面选一张"
private const val CARD_SUBTITLE = "白色方框能透出多少背景"

@Composable
internal fun BackgroundScreen(
    backgroundPrefs: BackgroundPrefs,
    uiPrefs: UiPrefs,
    repository: TimetableRepository,
    onBack: () -> Unit,
    onShowHint: (String) -> Unit,
) {
    val settings by backgroundPrefs.settings
        .collectAsStateWithLifecycle(initialValue = DEFAULT_BACKGROUND_SETTINGS)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var previewing by remember { mutableStateOf(false) }
    var previewingCard by remember { mutableStateOf(false) }
    val hasImage = !settings.imageUri.isNullOrBlank()

    // SAF 选图：必须取持久化授权，否则重启后读不到图
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // ⚠️ takePersistableUriPermission 会抛 SecurityException（若文件提供方不支持持久授权），
        // 所以 runCatching 收口：拿不到授权也要把 URI 存下来，至少本次进程内能看。
        runCatching {
            val resolver = context.contentResolver
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure {
            onShowHint("这张图片只在本页有效，重启后可能不显示")
        }
        // ⚠️ setImageUri 是 suspend（DataStore 的 edit 会挂起等磁盘），
        // 不能塞进 runCatching 的同步块里——必须先 launch 进协程，再在里面 try。
        scope.launch {
            runCatching { backgroundPrefs.setImageUri(uri.toString()) }
                .onFailure { onShowHint("保存图片失败，请换一张试试") }
        }
    }

    ProfileSubScaffold(title = TITLE, onBack = onBack) {
        // ---------- 背景图片 ----------
        GroupHeader(GROUP_IMAGE)
        GroupCard {
            SettingsNavRow(
                title = ROW_PICK,
                subtitle = if (hasImage) IMAGE_CHOSEN else HINT_NONE,
                onClick = { picker.launch(arrayOf("image/*")) },
                showDivider = hasImage,
            )
            if (hasImage) {
                SettingsActionRow(
                    title = ROW_CLEAR,
                    subtitle = "恢复默认底色",
                    onClick = { scope.launch { backgroundPrefs.clear() } },
                    showDivider = false,
                )
            }
        }

        // ---------- 作用范围（多选，见 BackgroundScopeSection）----------
        BackgroundScopeSection(
            scopes = settings.scopes,
            // 勾选语义放在调用方算：拿到旧集合才知道这一行点完之后是勾上还是取消。
            onToggle = { toggled ->
                val next = if (settings.scopes.contains(toggled)) {
                    settings.scopes - toggled
                } else {
                    settings.scopes + toggled
                }
                scope.launch { backgroundPrefs.setScopes(next) }
            },
        )

        // ---------- 不透明度（点进去才是全屏实时预览）----------
        GroupHeader(GROUP_OPACITY)
        GroupCard {
            SettingsActionRow(
                title = OPACITY_VALUE.format(settings.opacity * 100),
                subtitle = if (hasImage) OPACITY_SUBTITLE else OPACITY_SUBTITLE_NO_IMAGE,
                // 没选图时拦在门口：预览层画的是「课表主页」，没有图可铺时
                // 拖滑块屏幕纹丝不动，用户只会以为功能坏了。给一句话比给一个死按钮好。
                onClick = {
                    if (hasImage) previewing = true else onShowHint(HINT_NO_IMAGE)
                },
                showDivider = false,
            )
        }

        // ---------- 卡片不透明度（M13）----------
        // 卡片这一项**不要求先选背景图**：透不透是卡片自己的事，
        // 纯色背景下照样看得见差别，拦在门口会让一半的入口进不去。
        GroupHeader(GROUP_CARD)
        GroupCard {
            SettingsActionRow(
                title = CARD_VALUE.format(settings.cardAlpha * 100),
                subtitle = CARD_SUBTITLE,
                onClick = { previewingCard = true },
                showDivider = false,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    // ---------- 全屏实时预览（需求二.2）----------
    if (previewing) {
        BackgroundOpacitySheet(
            current = settings,
            uiPrefs = uiPrefs,
            repository = repository,
            onApply = { value -> scope.launch { backgroundPrefs.setOpacity(value) } },
            onDismiss = { previewing = false },
            // 预览层是只读的，没学期时点「创建本学期」给一句提示而不是静默失效
            onNoTerm = { onShowHint("还没有学期，先去「我的 → 学期设置」创建") },
        )
    }

    // ---------- 卡片不透明度的全屏实时预览 ----------
    if (previewingCard) {
        CardAlphaSheet(
            current = settings,
            onApply = { value -> scope.launch { backgroundPrefs.setCardAlpha(value) } },
            onDismiss = { previewingCard = false },
        )
    }
}
