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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import com.gould.xputimetable.data.prefs.BackgroundSettings
import com.gould.xputimetable.data.prefs.DEFAULT_CARD_ALPHA
import com.gould.xputimetable.ui.theme.LightPageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * @param scope    当前页面所属作用域；不在 settings.scopes 里时**不画图**（只留底色）
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
    // 需求四：背景主色，卡片色跟着它走（否则一律死白 = 老大手里的"贴纸感"）
    var dominant by remember { mutableStateOf<Color?>(null) }
    // M13：卡片不透明度。读在组合期（settings 由上层订阅），不额外开 Flow
    val cardAlpha = settings?.cardAlpha?.coerceIn(0f, 1f) ?: DEFAULT_CARD_ALPHA
    // 卡片**正下方**的实际颜色 = 页面底色 ⊕ 背景图×不透明度。
    //
    // 为什么必须算这个而不是直接用页面底色：卡片是半透明的，字的可读性由
    // "卡片底色叠在它正下方那个东西上"的结果决定。用户选了张暗图又把背景不透明度
    // 拉满时，正下方接近纯黑 —— 此时若还按浅色页面底色给透明度，
    // 字会糊成一片（2026-10-05 实测：alpha 0.30 时副文本对比度仅 1.07:1）。
    // 这个值交给 GlassPalette 去做"只补到刚好能读"的补偿。
    val base = pageBaseColor()
    val backdrop = if (shouldDrawBackground(settings?.scopes, scope)) {
        val imgOpacity = settings?.opacity ?: 0f
        dominant?.let { blend(base, it, imgOpacity) } ?: base
    } else {
        base
    }
    Box(modifier = modifier.fillMaxSize().background(base)) {
        if (shouldDrawBackground(settings?.scopes, scope)) {
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
            // 主色直接从**已解码的这张图**上取，不另开一次流重解码 ——
            // 背景图已经为了显示解码过一次，为取色再来一遍是纯浪费
            // （一张 2000×1500 的图解码约 6MB，重复解码在中低端机上能感到卡顿）。
            val src = bitmap
            LaunchedEffect(src) {
                dominant = if (src == null) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching { dominantColor(src.asAndroidBitmap()) }.getOrNull()
                    }
                }
            }
        } else {
            // 作用域不匹配（这张页面不画图）时必须把主色清空，
            // 否则会残留上一张图的颜色，卡片跟着一个"不存在的背景"调色。
            LaunchedEffect(settings?.imageUri) { dominant = null }
        }
        // 两个玻璃参数一起下发：主色决定卡片什么颜色，基准值决定它有多透。
        // 走 CompositionLocal 而不是让每个卡片自己去读 DataStore ——
        // 卡片是叶子构件，为拿一个浮点数去订阅存储，会在列表里同时挂上几十个订阅。
        // 必须在 Box 内 provide：content 是 BoxScope 接收者函数，provide 挪到 Box 外编译不过。
        CompositionLocalProvider(
            LocalGlassTint provides dominant,
            LocalCardAlpha provides cardAlpha,
            LocalGlassBackdrop provides backdrop,
        ) {
            content()
        }
    }
}

/**
 * 把背景图按不透明度叠在页面底色上（纯函数）。
 *
 * @param base   页面底色（不透明）
 * @param over   背景图主色
 * @param alpha  背景图不透明度（0f = 只有底色，1f = 完全盖住底色）
 * @return 合成后的实际颜色
 */
fun blend(base: Color, over: Color, alpha: Float): Color {
    val a = alpha.coerceIn(0f, 1f)
    return Color(
        red = over.red * a + base.red * (1f - a),
        green = over.green * a + base.green * (1f - a),
        blue = over.blue * a + base.blue * (1f - a),
        alpha = 1f,
    )
}

/**
 * 卡片正下方的实际颜色（M13 新增）。
 *
 * 由 [PageBackground] 算好下发；卡片只管读，不必自己去合成 —— 合成要读
 * 背景图不透明度与主色，让每个叶子构件都去算一遍既重复又容易漏。
 * null = 纯色背景（此时按页面底色兜底，见 GlassPalette.readableFloorFor）。
 */
val LocalGlassBackdrop = compositionLocalOf<Color?> { null }

/** 读当前卡片正下方的实际颜色。 */
@Composable
fun glassBackdrop(): Color? = LocalGlassBackdrop.current

/** 背景主色（需求四）。null = 纯色背景，卡片用固定半透明白。 */
val LocalGlassTint = compositionLocalOf<Color?> { null }

/** 读当前背景主色。 */
@Composable
fun glassTint(): Color? = LocalGlassTint.current

/**
 * 卡片不透明度基准值（M13：需求「为白色UI方框增加透明度调节」）。
 *
 * 由 [PageBackground] 下发，缺省即玻璃色的默认档 —— 不在背景层里的页面
 * （例如 App 启动前的预览）也就自动是老观感，不会白屏一片。
 */
val LocalCardAlpha = compositionLocalOf { DEFAULT_CARD_ALPHA }

/**
 * 当前页面该不该画背景图（纯函数，单测覆盖 —— 这是需求二"作用范围"的行为核心）。
 *
 * 语义一句话：**勾了「全局」，或这一页的档位被勾着，就画。**
 *
 * @param chosen 用户勾选的作用范围集合；null = 老版本存过但解析不出、新键也还没写过
 * @param page   当前页面所属作用域
 */
fun shouldDrawBackground(chosen: Set<BackgroundScope>?, page: BackgroundScope): Boolean =
    // 集合或 null 一律走 contains：空集合（用户把四项全取消）与 null 都落在 false 上，
    // 那个"没设置过"的分支由 BackgroundPrefs 在读取侧兜默认值，判定函数这里不再区分两者。
    chosen?.let { it.contains(BackgroundScope.GLOBAL) || it.contains(page) } ?: false
