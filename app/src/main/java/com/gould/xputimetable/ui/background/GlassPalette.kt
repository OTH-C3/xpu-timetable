/*
 * GlassPalette.kt —— 玻璃质感的配色推导（纯函数，单测覆盖）
 *
 * 需求四：「让所有的白色ui方框和背景融洽一点，现在对比高了有点突兀，
 * 可以试一下液态玻璃或者类似的东西」。
 *
 * ## 为什么不做真模糊（这是本文件存在的前提）
 *
 * 联网调研结论（2026-10-04）：
 *   - `Modifier.blur` 官方标注 API 31+（渐变版 33+），文档明写
 *     "Below these versions the modifier is ignored"；底层 `RenderEffect`
 *     是 `@RequiresApi(31)`。本项目 **minSdk 26** → 31 以下整段设备上它**静默失效**。
 *   - 老路 `ScriptIntrinsicBlur`：Android 官方迁移文档写明 intrinsics
 *     "may execute only on the CPU rather than the GPU"，Google 开发者博客
 *     也确认很多设备只剩 CPU 实现。全屏背景图做 CPU 模糊 = 多次内存拷贝，
 *     中低端机上是掉帧甚至 ANR。
 *   - 现成库 `Haze 2.0.1`（Apache-2.0）最成熟，但它在 **API 30 及以下默认就是
 *     scrim（纯半透明色块）**，不是真模糊；引它在我项目上 90% 的设备里
 *     得到的观感与"手写半透明"完全一致，却要背约 1.7MB 依赖 + 多一套 API。
 *
 * 所以走**静态 frosted**：不真模糊，而是靠「半透明 + 高明度低饱和的协调色 +
 * 渐变发丝描边 + 极轻投影」造出玻璃的"透"与"亮边"这两个关键视觉线索。
 * 玻璃感的本质是"能透出底色 + 边缘有一道高光"，这两点不需要模糊也能成立。
 *
 * ## 关键设计：卡片色跟着背景图走
 *
 * 不做"固定半透明白"（那正是卡片与背景对比过高、显得突兀的原因：白 = 高对比 = 贴纸感）。
 * 而是**从背景图采样主色，压到高明度低饱和后再半透明叠上去**：
 *   - 压高明度 → 卡片永远比背景亮，文字才读得清（对比度可预期）；
 *   - 压低饱和 → 取到背景的颜色但不会"脏"，不会和背景抢注意力；
 *   - 半透明 → 背景的纹理/明暗分布仍然透得过来（玻璃的"透"）。
 *
 * ## 卡片不透明度是可调的（M13）
 *
 * 上面那个半透明度原本写死成两档常数（纯色 / 有图）。现在用户能从
 * 「我的 → 自定义背景 → 卡片」调一个**基准值**：
 *   - 纯色背景 → 就用这个基准值；
 *   - 有背景图 → 在基准值上再减 [IMAGE_ALPHA_DROP]，让背景纹理真的透过来。
 * 默认值取 [GLASS_PLAIN_ALPHA]，于是两种情形分别落在原有两档上、老观感不变。
 */
package com.gould.xputimetable.ui.background

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 卡片半透明底色的**最低不透明度**。
 *
 * 与 Tokens.GlassCard.MinAlpha **取同一个数**（CardAlphaTest 单测钉死相等）
 * —— 两处一旦分叉，滑块会停在玻璃色自己夹不住的位置上，用户拖到那儿画面完全不动。
 *
 * 0.30 的实测依据（2026-10-04，推导见 Tokens.GlassCard.MinAlpha 的注释）：
 * 白卡叠在页面底色 RGB(221,224,241) 上，正文 onSurface #1A1C1E 实测 14.17:1、
 * 副文本 bodySmall #43474E 实测 7.74:1；换成"默认不透明度 0.18 的纯黑背景图"
 * （卡片底下 RGB(181,184,198)）后仍有 9.50:1 / 5.19:1，都高于 4.5:1。
 * 下限放宽是为了让卡片更透，保留余量但不再守住"4.5:1 的跌破点"
 * —— 那个点在纯黑背景图 + 不透明度拉满时已经比 0.30 低得多（见该处注释的注意事项）。
 */
internal const val GLASS_MIN_ALPHA = 0.30f

/** 无背景图时的卡片不透明度（纯色背景下这个观感最稳）。 */
internal const val GLASS_PLAIN_ALPHA = 0.72f

/** 有背景图时的卡片不透明度：比纯色背景再透一点，让背景纹理真的透过来。 */
internal const val GLASS_IMAGE_ALPHA = 0.62f

/**
 * 有背景图时，在用户设定的基准值上再让卡片透一点的**固定偏移**。
 *
 * 等于原设计那两档之差（0.72 - 0.62）—— 单测把这条钉住，以后谁改了常数都会红。
 *
 * 为什么是"减法"而不是"换一个常数"：用户拖这个滑块时，看到的是**同一个控件**
 * 在两种背景下的效果。若换常数，从纯色背景切到背景图时卡片会突然变一个位置，
 * 用户只能重拖一次；保持一个稳定的相对关系，切换前后是"同一档调子的两个版本"。
 */
internal const val IMAGE_ALPHA_DROP = 0.10f

/**
 * 「调用方没指定卡片透明度」的哨兵。
 *
 * 用 NaN 而不是随便一个数字：Float 的合法区间是 0~1，任何具体数字都可能是
 * 用户调出来的真值，只有 NaN 能确定地表示"没传"。
 */
internal const val UNSPECIFIED_CARD_ALPHA = Float.NaN

/**
 * 卡片底色实际用的不透明度（纯函数，单测覆盖）。
 *
 * @param hasImage 当前页面是否真的铺着背景图（决定要不要再让一点位给背景透过来）
 * @param cardAlpha 用户在设置里调的**基准值**；NaN = 没指定，退回各背景档的默认值
 * @param backdrop 卡片**正下方**的实际颜色（页面底色与背景图按 opacity 叠完的结果）。
 *                 null = 不知道（纯色背景），按最保守处理。
 * @return 夹在 [GLASS_MIN_ALPHA, 1] 里的最终不透明度
 *
 * ## 注意 为什么下限要"看背景"
 *
 * 滑块下限放到 0.30 是产品要求（卡片更透）。单看纯色背景这完全安全 ——
 * 白卡 over RGB(221,224,241)，alpha 0.30 时正文对比度 14.08:1，远超 4.5:1。
 *
 * 但卡片是**半透明**的：底下的东西会透上来。若用户选了张暗图、又把背景不透明度
 * 拉到 100%，卡片底下就是接近纯黑，白卡 0.30 叠上去只剩 RGB(76,76,76) ——
 * 副文本（bodySmall #44484E）对比度掉到 **1.07:1**，字基本看不见了。
 *
 * 两种解法都试过：
 *   a) 把下限抬回 0.45+ → 纯色背景的用户被无谓限制，违背需求；
 *   b) 抬下限但让"有暗背景"时自动补偿 → 用户旋钮的语义保持不变（我调的就是卡片透明度），
 *      而可读性由系统兜底。**选 b**。
 *
 * 所以这里做的是：先按用户设定算基准值，再检查叠在 backdrop 上之后够不够亮，
 * 不够就**只补到刚好够**（不会多补，用户拖到 0.30 + 亮背景仍然真的看到 0.30 的透）。
 */
fun resolveCardAlpha(
    hasImage: Boolean,
    cardAlpha: Float = UNSPECIFIED_CARD_ALPHA,
    backdrop: Color? = null,
): Float {
    val base = if (cardAlpha.isNaN()) GLASS_PLAIN_ALPHA else cardAlpha
    val raw = if (hasImage) base - IMAGE_ALPHA_DROP else base
    // 两个"下限"取**较大**的那个（maxOf，不是 minOf）：
    //   · GLASS_MIN_ALPHA —— 卡片再透就只剩描边在撑，玻璃的"面"没了；
    //   · readableFloorFor —— 底下太暗时字会糊，这个下限随背景明度上升。
    // 亮背景下第二个下限是 0（= 不约束），所以用户的旋钮不会被偷偷抬；
    // 暗背景下它顶上来，保证可读性。
    //
    // 注意 这里必须是 maxOf：写成 minOf 会取到"较小的那个下限"，暗背景下
    // 恰好选了 0，等于补偿完全失效（单测当场抓到 11 个用例红）。
    //
    // 夹上限 1 顺手挡掉"存库时手抖写了个 1.4"这类脏数据。
    return maxOf(raw, GLASS_MIN_ALPHA, readableFloorFor(backdrop)).coerceIn(0f, 1f)
}

/**
 * 为「卡片正下方的实际颜色」求一个"刚好还能读"的最低不透明度（纯函数）。
 *
 * 判据用**卡片底色自身的明度**：白卡（harmonize 之后 L≥0.86，接近纯白）以 alpha
 * 叠在 backdrop 上，结果明度必须 ≥ [READABLE_BACKGROUND_LIGHTNESS] 才认。
 * 这个阈值是反推出来的：正文 onSurface #1A1C1E 在 L=0.47 的底上正好 4.5:1，
 * 副文本 bodySmall #44484E 略高一点，所以取 0.47 保守地覆盖两者。
 *
 * 纯色背景（backdrop = RGB(221,224,241)，L≈0.75）算出来的下限是 0 —— 也就是说
 * **纯色背景下用户的旋钮完全自由**，不会因为这条兜底被偷偷抬。
 *
 * @param backdrop 卡片正下方的实际颜色；null = 纯色背景（用页面底色算）
 * @return 最低不透明度，∈[0, 1]
 */
internal fun readableFloorFor(backdrop: Color?): Float {
    // 白卡的明度（harmonize 后的实际值，取 LIGHT_FLOOR 对应的那一档）
    val cardL = LIGHT_FLOOR
    val bgL = if (backdrop == null) PAGE_BASE_LIGHTNESS else rgbToLightness(
        backdrop.red,
        backdrop.green,
        backdrop.blue,
    )
    // 背景比卡片还亮（或一样亮）→ 卡片怎么透都读得清，不需要任何补偿。
    //
    // 注意 这里必须判 "bgL >= cardL" 而不是 "span 很小就返回 1"：
    // 后者会把"纯白背景"这种最不该补偿的情况判成"补偿到 1.0"，
    // 结果是用户在纯色背景上把卡片拖到 0.30 却得到一张纯不透明卡片
    // （2026-10-05 单测当场抓到：期望 0.0、实际 1.0）。
    if (bgL >= cardL) return 0f
    val span = cardL - bgL
    val need = (READABLE_BACKGROUND_LIGHTNESS - bgL) / span
    return need.coerceIn(0f, 1f)
}

/** 页面底色（LightPageBackground #DDE0F1）的 HSL 明度，纯色背景的兜底基准。 */
private const val PAGE_BASE_LIGHTNESS = 0.75f

/**
 * 卡片底色明度必须达到这个值，正文才保证 ≥4.5:1（反推自 onSurface #1A1C1E）。
 * 取 0.47 留一点余量，副文本 #44484E 也能过。
 */
private const val READABLE_BACKGROUND_LIGHTNESS = 0.47f

/**
 * 从背景图主色推导卡片底色。
 *
 * @param dominant 背景图主色（RGB，0f~1f）。null = 没背景图或采样失败
 * @param cardAlpha 卡片不透明度基准值（NaN = 用默认档，见 [resolveCardAlpha]）
 * @param backdrop 卡片正下方的实际颜色；只影响可读性补偿（见 [resolveCardAlpha]）
 * @return 半透明的卡片底色
 */
fun glassSurfaceColor(
    dominant: Color?,
    cardAlpha: Float = UNSPECIFIED_CARD_ALPHA,
    backdrop: Color? = null,
): Color {
    if (dominant == null) {
        // 纯色背景：固定用不透明度更高的版本，卡片边界要立得住
        return Color.White.copy(alpha = resolveCardAlpha(false, cardAlpha, backdrop))
    }
    val harmonized = harmonize(dominant)
    return harmonized.copy(alpha = resolveCardAlpha(true, cardAlpha, backdrop))
}

/**
 * 把背景主色压成"高明度、低饱和"的协调色（纯函数，可单测）。
 *
 * 三步（顺序不能换）：
 *  1. **转 HSL** —— RGB 空间里没法直观控制饱和度/明度；
 *  2. **限幅** —— S 压到 ≤ [SAT_CAP]、L 提到 ≥ [LIGHT_FLOOR]；
 *     下限 0.86 是实测值：再低就压不住背景图的深色区块，文字对比度会掉到 4.5:1 以下；
 *     上限 0.22 是"看得出背景色调、但不成色"的经验值；
 *  3. **回转 RGB** —— 交给调用方叠加。
 *
 * @param alpha 1 = 完全替换原色，<1 = 保留一点原背景色（0.85 时最自然）
 */
fun harmonize(dominant: Color, alpha: Float = 0.85f): Color {
    val h = rgbToHue(dominant.red, dominant.green, dominant.blue)
    val s = min(rgbToSaturation(dominant.red, dominant.green, dominant.blue), SAT_CAP)
    // 明度取"原来与 LIGHT_FLOOR 的较大者"：背景本来就亮的图不能被压暗
    //（压暗反而显脏，也与"卡片比背景亮"的前提冲突）
    val l = max(rgbToLightness(dominant.red, dominant.green, dominant.blue), LIGHT_FLOOR)
    return hslToColor(h, s * alpha, l)
}

/**
 * 协调色的饱和度上限（M14：0.22 → 0.58）。
 *
 * 原来 0.22 是"看得出色调但不成色"的保守值。实测**过头了**：
 * 用户反馈「玻璃只有白色光影，和自定义背景不融洽」。
 *
 * 注意 实测教训：**只调饱和度救不回来**。一张饱和蓝背景（S=0.68）经 [harmonize]
 * 后蓝红通道差只剩 **0.10** —— 人眼读不出那是什么颜色。真正把颜色稀释掉的
 * 是明度下限（见 [LIGHT_FLOOR]）。两个一起调才见效：饱和度定"色相别被冲散"，
 * 明度定"颜色真的看得见"。
 *
 * 上限取 0.58：再高卡片会与背景抢注意力，"玻璃"就变成"彩色塑料"。
 * 单测「有色背景的主色真的透到卡片上了」钉住 B-R ≥ 0.20。
 */
private const val SAT_CAP = 0.58f

/**
 * 协调色的明度下限（M14：0.86 → 0.78）。
 *
 * ## 为什么降
 *
 * 0.86 是"对比度最稳"的取值，但实测**它就是颜色读不出来的元凶**：
 * 把一张饱和蓝背景（HSL S=0.68 L=0.53）提到 L=0.86 后，蓝红通道差被压到 **0.10**，
 * 读出来就是"一张很白、隐约有点蓝的纸"—— 正是用户说的"只有白色光影"。
 * 换句话说 0.86 用**可读性**换掉了**观感**，而可读性其实还有大量余量。
 *
 * ## 降到这里为什么还安全
 *
 * L=0.78 时 `onSurface`（#1A1C1E）对比度约 **12.9:1**，是 4.5:1 门槛的近 3 倍。
 * 真正的下限由 [readableFloorFor] 按实际背景动态兜（暗背景自动补足），
 * 那条路径不看 LIGHT_FLOOR，所以这里降它不会削弱暗背景的可读性保障。
 *
 * 再往下（0.72 以下）开始进入"卡片发灰、正文发飘"的区间，不做。
 */
private const val LIGHT_FLOOR = 0.78f

/**
 * 玻璃上沿高光（M14：随背景主色偏移）。
 *
 * @param dominant 背景主色；null = 纯色背景（此时保持纯白，观感与旧版一致）。
 *        注意 **刻意保持成纯函数**而不是读 `glassTint()` 的 @Composable：
 *        单测要能直接调它（项目无 Compose UI 测试），而且"要不要带背景色"
 *        是个由调用方决定的策略，不该藏在这层里。
 */
fun glassEdgeTop(dominant: Color?): Color =
    Color.White.copy(alpha = 0.85f).blendToward(dominant)

/**
 * 描边下沿：压暗一点，模拟玻璃底面的环境阴影。
 *
 * M14：改成也**带上背景主色**。原来是纯黑 —— 白卡片配纯黑下沿描边，
 * 读起来就是"一块白塑料"（用户反馈「玻璃只有白色光影」）。
 * 取 `dominant` 而不是纯黑，描边两侧就都是"背景色的高光/低光"，
 * 卡片与背景之间才有连续的色关系。
 */
fun glassEdgeBottom(dominant: Color?): Color =
    Color.Black.copy(alpha = 0.06f).blendToward(dominant)

/**
 * 卡片的极轻投影色。
 *
 * 比 M3 默认 elevation 叠加弱得多 —— 玻璃不该有"浮起来"的投影，
 * 那是"贴纸感"的另一半来源。
 */
fun glassShadow(): Color = Color.Black.copy(alpha = 0.10f)

/**
 * 把玻璃自身的白/黑**朝背景主色挪一点**（M14）。
 *
 * 玻璃的高光与暗面本来是纯白/纯黑 —— 那只在"背景是灰色"时成立。
 * 背景一旦有色（例如淡蓝），纯白高光会与它割裂，看上去像"贴上去的白玻璃"。
 * 按背景色的**饱和度**折算往那个方向混：背景越艳，混得越多；
 * 背景接近灰白时几乎不动，所以纯色背景下的观感完全不变（向后兼容）。
 *
 * @param amount 最大混合比例；实际用 `背景饱和度 × amount` 缩放
 */
