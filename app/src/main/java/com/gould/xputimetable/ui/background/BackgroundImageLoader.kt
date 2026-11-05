/*
 * BackgroundImageLoader.kt —— 从 content:// URI 载入背景图（M12 需求二）
 *
 * 为什么自己写而不引 Coil / Glide：
 *   项目至今零网络依赖（ADR-003 的选型纪律），为一个"读一张本地图"引入几百 KB 的
 *   图片库不划算；这里的需求极简——按屏幕尺寸降采样、解码成 ImageBitmap。
 *   若将来要支持 GIF / 远程图，再评估换库（届时只需替换本文件，调用点不变）。
 *
 * 三条工程约束（都来自实际会翻车的地方）：
 *   1. **解码必须在 IO 线程**：BitmapFactory 是阻塞调用，主线程解码大图直接 ANR。
 *   2. **必须降采样**：一张 4000×3000 的手机照片按原尺寸解码约 24MB（RGB_565），
 *      高分屏再乘一次就 OOM。先只读尺寸（inJustDecodeBounds）算 2 的幂次 inSampleSize，
 *      再真解码。
 *   3. **URI 权限可能已失效**：用户换图 / 系统回收授权后 decodeStream 返回 null，
 *      此时必须安静降级为"没有背景图"，绝不能抛异常把设置页搞崩。
 */
package com.gould.xputimetable.ui.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val TAG = "BgImageLoader"

/**
 * 载入背景图；URI 为空 / 授权失效 / 解码失败时结果是 null（= 不画图）。
 *
 * @param uri       图片 content:// URI（由 SAF 选图得到）
 * @param maxPixels 允许的最大边长（像素）。传当前屏幕宽高的较大值即可——
 *                  背景图按 Crop 铺满，超过这个尺寸的像素纯属浪费内存。
 */
@Composable
fun rememberBackgroundBitmap(
    uri: String?,
    maxPixels: Int,
): State<ImageBitmap?> {
    // Context 读在组合期；它是稳定值（Activity 级），不会造成每帧重组。
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri, maxPixels) {
        value = if (uri.isNullOrBlank() || maxPixels <= 0) {
            null
        } else {
            loadBackgroundBitmap(context, Uri.parse(uri), maxPixels)
        }
    }
}

/**
 * 载入并解码（抽成独立函数：便于单测直接喂假 Context / Uri）。
 *
 * @return 解码后的位图；任一环节失败返回 null
 */
suspend fun loadBackgroundBitmap(
    context: Context,
    uri: Uri,
    maxPixels: Int,
): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver

        // 第一遍只读尺寸：不分配像素内存，但要开一次流。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            // 走不到内容：流为空或不是图片，当作"没有背景图"
            return@runCatching null
        }

        // 第二遍按采样解码。
        val opts = BitmapFactory.Options().apply {
            inSampleSize = decodeSampleSize(bounds.outWidth, bounds.outHeight, maxPixels)
            // 背景图是纯装饰，不需要带 alpha 通道的 ARGB_8888（内存直接砍半）
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        bitmap?.asImageBitmap()
    }.onFailure { e ->
        // 授权失效（SecurityException）与解码失败（OOM / 格式不支持）都在此收口：
        // 背景是纯装饰功能，绝不允许它把设置页或课表页带崩。
        Log.w(TAG, "背景图加载失败：${e.message}")
    }.getOrNull()
}

/**
 * 算降采样倍数（纯函数，单测覆盖）。
 *
 * 规则：只要**最长边**超过 maxPixels 就减半，直到不超过 —— 即结果长边 ≤ maxPixels。
 * 必须取 2 的幂 —— BitmapFactory 对非 2 幂的 inSampleSize 解码结果不可预期。
 *
 * ⚠️ 这里刻意用"超过就减半"而不是 Android 官方 calculateInSampleSize 的
 * "减半后仍 ≥ 目标才减"（那套会把结果长边放到目标的 2 倍以内）。
 * 差别在 4000×3000 的手机照片上很致命：官方写法在目标 2400 时返回 1，
 * 于是 4000×3000 按 RGB_565 **原样解码 = 24MB**，高分屏再叠加就是 OOM。
 * 本实现会返回 2（2000×1500 = 6MB）。
 *
 * 代价是背景图会被拉伸到屏幕尺寸（约 1.6 倍），略糊一点。但背景图是
 * alpha 压到 0.18 叠在纯色上的纯装饰 —— 宁可糊也不能 OOM 把 App 带崩。
 */
fun decodeSampleSize(width: Int, height: Int, maxPixels: Int): Int {
    if (width <= 0 || height <= 0 || maxPixels <= 0) return 1
    var sample = 1
    var longEdge = max(width, height)
    while (longEdge > maxPixels) {
        longEdge /= 2
        sample *= 2
    }
    return sample
}

/**
 * 从已解码的背景图里取主色（需求四：卡片色要跟着背景图走）。
 *
 * 为什么只取"平均色"而不是做 K-means 聚类：卡片是**一块一整片**半透明底，
 * 需要的恰恰是整张图的整体色调倾向，而不是局部主题色。聚类反而会让卡片
 * 颜色在图的不同区域之间跳变（用户一滚动就换色），比统一色更糟。
 *
 * 采样策略：最多取 32×32 = 1024 个点均匀铺满。为什么不是全图遍历：
 * 一张 2000×1500 的图逐像素 getPixel 要 300 万次 JNI 调用，主线程上直接卡顿。
 *
 * @return 平均色（alpha 恒为 1）；图为空返回 null
 */
fun dominantColor(bitmap: android.graphics.Bitmap): Color? {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null
    val stepX = (w / 32).coerceAtLeast(1)
    val stepY = (h / 32).coerceAtLeast(1)
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    var y = 0
    while (y < h) {
        var x = 0
        while (x < w) {
            val p = bitmap.getPixel(x, y)
            r += (p shr 16) and 0xFF
            g += (p shr 8) and 0xFF
            b += p and 0xFF
            n++
            x += stepX
        }
        y += stepY
    }
    if (n == 0L) return null
    return Color(
        red = r.toFloat() / n / 255f,
        green = g.toFloat() / n / 255f,
        blue = b.toFloat() / n / 255f,
        alpha = 1f,
    )
}
