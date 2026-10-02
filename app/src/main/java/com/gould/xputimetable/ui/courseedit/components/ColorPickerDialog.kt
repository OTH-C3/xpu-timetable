/*
 * ColorPickerDialog.kt —— 课程颜色选择器（自绘 HSV，M11-第三批）
 *
 * 为什么自己画：这类库动辄几千行还带自己的状态约定，而我们要的只是一块 S×V 平面 +
 * 一条色相条 + 一个 hex 框，且颜色要直接落到 `courses.color_tag`（Int 列）。自己画 180 行，
 * 形状完全可控，也不跟着库的 API 漂移坏掉。
 *
 * 性能取舍：S×V 平面（横向饱和度 / 纵向明度）如果按像素格去画，要么每帧重算上百次 HSV→RGB
 * 发烫，要么得一坨 GridCell 数据再加缓存——两条路都把"一块渐变盘"写成了几百行。
 * 这里改成**两层 Brush 叠加**，恒定两次 drawRect：
 *   ① 横向 白 → 当前色相的最纯色（覆盖整块，把饱和度铺开）；
 *   ② 纵向 透明 → 黑（覆盖整块，越往下越黑，把明度压下来）。
 * 两层叠完，(x=w,y=0) 是纯色、(x=0,y=0) 是白、(y=h) 是黑——正好是 HSV 平面该有的样子。
 * 于是 hue 变化时只换一层 brush，平面内拖动时两层都不变，每帧只多画指示圈。
 *
 * 交互：拖动 + 点按都能取色（手势见文件尾的 pickGesture，按下先给一次，之后跟手）。
 * 指针换算全部在 px 侧做，并且 clamp 到 0..1——手指落在角上不能算出 -0.2 把整块染黑。
 *
 * 手输 hex：解析失败**静默不改色**（输入框每敲一个字都走一次，抛异常等于敲错一个字就崩）。
 */
package com.gould.xputimetable.ui.courseedit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.courseedit.ColorPickerMath
import com.gould.xputimetable.ui.theme.ListRow

// ---------- 文件级常量 ----------
private const val HANDLE_RADIUS_PX = 9f
private const val HANDLE_STROKE_PX = 3f
private val HUE_BAR_WIDTH = 24.dp     // 右侧色相条宽度
private val HUE_BAR_GAP = 12.dp        // 与平面的间距

/**
 * 色相条的 7 个停靠点。
 * 头尾都是 0°红：色相是环，360° 与 0° 同色，条子首尾才接得上、不露出接缝。
 */
private val HUE_STOPS: List<Pair<Float, Color>> = listOf(
    0f, 60f, 120f, 180f, 240f, 300f, 360f,
).map { h -> (h / 360f) to Color(ColorPickerMath.hsvToArgb(h, 1f, 1f)) }

/**
 * 取色面板。
 *
 * @param initialArgb 打开时回填的颜色（课程当前色）
 * @param onDismiss   取消 / 点外部关闭
 * @param onConfirm   确定，回传不透明 ARGB int（可直接写进 color_tag）
 */
@Composable
internal fun ColorPickerDialog(
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initial = ColorPickerMath.argbToHsv(initialArgb)
    var hue by remember { mutableStateOf(initial[0]) }
    var sat by remember { mutableStateOf(initial[1]) }
    var value by remember { mutableStateOf(initial[2]) }
    // hex 文本框：手输时它是"源"，其余时候是"显示"，两边互为镜像
    var hexText by remember { mutableStateOf(ColorPickerMath.formatHex(initialArgb)) }

    fun applyHsv(h: Float, s: Float, v: Float) {
        hue = h
        sat = s
        value = v
        hexText = ColorPickerMath.formatHex(ColorPickerMath.hsvToArgb(h, s, v))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        // ⚠️ 必须关掉"点外面就关"：取色盘本身就是一大片可点区域，用户每次点盘子取色
        // 都会被当成"点外部"把面板关掉，取色器等于没法用（2026-10-02 真机/模拟器实测复现）。
        // 只有「取消」「保存」两个按钮能关面板，中途点空处不该丢进度。
        // ⚠️ 本版本 material3 的 AlertDialog 没有 dismissOnClickOutside / dismissOnBackPress
        // 这两个具名参数，两个开关都挂在 DialogProperties 上（写成具名参数会整片编译失败）。
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("课程颜色") },
        text = {
            BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
                val side = (maxWidth - HUE_BAR_WIDTH - HUE_BAR_GAP).coerceAtLeast(200.dp)
                // 直接乘 density：toPx 扩展在本版本解析不到，不值得为一个乘法引依赖
                val sidePx = side.value * LocalDensity.current.density
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HUE_BAR_GAP),
                    ) {
                        HueCanvas(
                            hue = hue,
                            saturation = sat,
                            value = value,
                            onPick = { s, v -> applyHsv(hue, s, v) },
                            sidePx = sidePx,
                            modifier = Modifier.size(side),
                        )
                        HueSlider(
                            hue = hue,
                            onPick = { h -> applyHsv(h, sat, value) },
                            heightPx = sidePx,
                            modifier = Modifier.size(HUE_BAR_WIDTH, side),
                        )
                    }

                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text
                            // 解析不出来就保持现状：不回滚输入框（用户还在打），也不改色
                            ColorPickerMath.parseHex(text)?.let { picked ->
                                val hsv = ColorPickerMath.argbToHsv(picked)
                                hue = hsv[0]
                                sat = hsv[1]
                                value = hsv[2]
                            }
                        },
                        singleLine = true,
                        label = { Text("#RRGGBB") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .heightIn(min = ListRow.MinHeight),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ColorPickerMath.hsvToArgb(hue, sat, value)) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 「按下就取色 + 跟着手指走」的手势，**点按和拖动都生效**。
 *
 * ⚠️ 不能用 `detectDragGestures`：它内部要等越过 touchSlop 才进 Dragging 状态（本版本是
 * AwaitDown → AwaitTouchSlop → Dragging 状态机），**不动手点一下根本不会触发 onDragStart**。
 * 而"点一下选个颜色"恰恰是取色器最常用的操作——2026-10-02 模拟器实测：点平面 hex 纹丝不动，
 * 得拖一下才变；右侧色相条点色带同样没反应（同一处毛病）。
 *
 * 于是自己走一遍 `awaitEachGesture`：按下即取一次色（点按有反馈），之后每个事件都跟手更新，
 * 读到抬起就收尾。指针离开平面时坐标照样 clamp 过，不会算出 0.8 这种越界比例。
 */
private suspend fun PointerInputScope.pickGesture(pick: (Offset) -> Unit) {
    awaitEachGesture {
        pick(awaitFirstDown().position)
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull() ?: break
            // 本版本的 PointerInputChange 没有 changedToUp()，靠 pressed 的回落判抬起
            if (!change.pressed && change.previousPressed) break
            pick(change.position)
        }
    }
}

/**
 * S×V 平面：横向是饱和度（左白右纯色），纵向是明度（下亮上黑）。
 *
 * @param sidePx 边长（px）：指针换算要用，尺寸由外层量好传进来，省掉 onSizeChanged 的时序判断
 */
@Composable
private fun HueCanvas(
    hue: Float,
    saturation: Float,
    value: Float,
    sidePx: Float,
    onPick: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun pick(x: Float, y: Float) = onPick((x / sidePx).coerceIn(0f, 1f), (1f - y / sidePx).coerceIn(0f, 1f))
    val shape = RoundedCornerShape(ListRow.CardCorner)

    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            // ⚠️ clip 必须加：drawRect 铺的是**直角**，而 border 画的是**圆角**，两者形状不齐，
            // 渐变会从圆角弧外面戳出去，四个角各漏一块黑色/深红的小三角（底下两角最明显）。
            // clip 之后填充跟着同一个 shape 走，角就干净了。这条孤立地看不出毛病，
            // 是 2026-10-02 老大问"为什么四个角有三角形"才揪出来的。
            .clip(shape)
            .pointerInput(Unit) { pickGesture { offset -> pick(offset.x, offset.y) } },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // 两层 brush 铺满整块：
            // ① 横向 白→纯色，把饱和度从 0 铺到 100%（左端永远是白）
            // ② 纵向 透明→黑，把明度从 100% 压到 0%（下端永远是黑）
            // 叠加结果就是标准 HSV 平面，且跟 hue 怎么变无关，只有两层 brush 在换。
            val pure = Color(ColorPickerMath.hsvToArgb(hue, 1f, 1f))
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, pure), 0f, size.width))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, size.height))
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            val x = saturation.coerceIn(0f, 1f) * size.width
            val y = (1f - value.coerceIn(0f, 1f)) * size.height
            // 外圈白 + 内圈真实色：拖到浅色区也看得清游标在哪
            drawHandle(Color.White, x, y, HANDLE_RADIUS_PX + HANDLE_STROKE_PX)
            drawHandle(Color(ColorPickerMath.hsvToArgb(hue, saturation, value)), x, y, HANDLE_RADIUS_PX)
        }
    }
}

private fun DrawScope.drawHandle(fill: Color, x: Float, y: Float, r: Float) {
    drawCircle(color = fill, radius = r, center = Offset(x, y))
}

/**
 * 色相竖条：0° 红在顶、360° 红回到底（色相环闭合，首尾同色）。
 * 游标白圈黑描边，跟平面指示点同一套视觉语言。
 */
@Composable
private fun HueSlider(
    hue: Float,
    heightPx: Float,
    onPick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun pick(y: Float) = onPick((1f - y / heightPx).coerceIn(0f, 1f) * 360f)
    // 同 HueCanvas：clip 和 border 必须是同一个 shape，否则彩虹细条两头也会漏出同款小三角
    val shape = RoundedCornerShape(ListRow.CardCorner)

    Box(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape)
            .pointerInput(Unit) { pickGesture { offset -> pick(offset.y) } },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // 一条纵向渐变把 7 个停靠点插值开：红→黄→绿→青→蓝→洋红→红，
            // 段与段之间不再有硬边（之前是 6 段纯色方块，看着就是"只有这几档颜色"）。
            // ⚠️ 带停靠点的重载是 vararg，调用必须写具名参数 + 展开符，位置传参会匹配到 List<Color> 那个重载上
            drawRect(brush = Brush.verticalGradient(*HUE_STOPS.toTypedArray(), startY = 0f, endY = size.height))
        }
        Canvas(modifier = Modifier.matchParentSize()) {
            val y = (hue.coerceIn(0f, 360f) / 360f) * size.height
            drawHandle(Color.White, size.width / 2f, y, HANDLE_RADIUS_PX + HANDLE_STROKE_PX)
        }
    }
}
