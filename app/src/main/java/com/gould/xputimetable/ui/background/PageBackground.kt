/*
 * PageBackground.kt —— 页面背景层（M12 需求一.2 + 需求二）
 *
 * 这个文件同时解决两个需求，因为它们本来就是同一件事：
 *   需求一.2「删除"课表"和"我的"页面后方多余的白色背景层，让其与课表主页共用同一个背景」
 *     —— 根因是两条独立的背景来源：周视图 Scaffold 显式给了 LightPageBackground（淡蓝紫），
 *        而「我的」页 Scaffold 没给 containerColor，走 M3 默认的 colorScheme.surface；
 *        Android 12+ 主题走动态取色，surface 往往接近白色 → 切到「我的」整页发白。
 *        修法不是"再给「我的」也涂一次色"（那只是把两个色值对齐），而是**抽一个唯一来源**：
 *        所有页面的 Scaffold 一律 containerColor = Color.Transparent，
 *        底色与背景图统一由本文件这层 Box 提供。这样加背景图时也只有一个插入点。
 *
 * 需求二「自定义背景」直接复用这一层：纯色 → 纯色 + 图片（按透明度叠加）。
 *
 * 层级顺序（自下而上）：
 *   1. 底色（亮色 LightPageBackground / 深色 surface）—— 需求一.2 的"同一个背景"
 *   2. 背景图（不透明度 = 透明度滑块的值，Crop 铺满）
 *   3. 页面内容（由调用方通过 content() 传入）
 *
 * 为什么不用 Surface：Surface 会额外画一层 elevation 叠加与边框；
 * 背景要的是"一层纯色 + 可选图"，多画任何东西都会在换图时露出破绽。
 */
package com.gould.xputimetable.ui.background

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.gould.xputimetable.data.prefs.BackgroundSettings
import com.gould.xputimetable.ui.theme.LightPageBackground

/**
 * 页面底色（纯色背景的唯一来源）。
 *
 * 亮色固定产品色 LightPageBackground（M5 起周视图就在用它），深色用 surface。
 * 所有页面的 Scaffold 都应传 containerColor = Color.Transparent，把底色交给本函数，
 * 不要再各自写一份色值 —— 那是需求一.2「多出一层白底」的同一个坑。
 */
@Composable
fun pageBaseColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else LightPageBackground

/**
 * 页面背景层：底色 + 可选背景图 + 内容。
 *
 * @param settings 当前背景设置（imageUri 为 null 或加载失败时等价于纯色）
 * @param scope    当前页面所属作用域；与 settings.scope 不匹配时**不画图**（只留底色）
 * @param maxPixels 图片降采样的最大边长（由调用方传屏幕尺寸）
 * @param content  页面内容
 */
@Composable
fun PageBackground(
    settings: BackgroundSettings?,
    scope: BackgroundScope,
    maxPixels: Int,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(pageBaseColor())) {
        if (shouldDrawBackground(settings?.scope, scope)) {
            val bitmap by rememberBackgroundBitmap(settings?.imageUri, maxPixels)
            bitmap?.let { image ->
                Image(
                    bitmap = image,
                    contentDescription = null,
                    // contentScale = Crop：铺满不留黑边，背景图本来就是被裁着用的
                    contentScale = ContentScale.Crop,
                    alpha = settings?.opacity ?: 0f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        content()
    }
}

/**
 * 当前页面该不该画背景图（纯函数，单测覆盖 —— 这是需求二"作用范围"的行为核心）。
 *
 * @param chosen 用户选的作用范围；null = 从未设置过
 * @param page   当前页面所属作用域
 */
fun shouldDrawBackground(chosen: BackgroundScope?, page: BackgroundScope): Boolean = when (chosen) {
    // 没设置过 → 任何页面都不画（此时也没有图可画，等价于纯色背景）
    null -> false
    else -> chosen == BackgroundScope.GLOBAL || chosen == page
}
