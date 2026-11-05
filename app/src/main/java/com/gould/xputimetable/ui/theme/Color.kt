/*
 * Color.kt —— 主题颜色定义（骨架阶段的最小占位实现）
 *
 * 说明（重要）：
 * - 这些是 M3 主题的基础色槽；如需接入设计令牌，替换本文件取值即可。
 *   界面代码只引用 MaterialTheme.colorScheme，不直接引用这里的常量（Spec §8 红线：禁硬编码颜色）。
 * - fallback 主色取 Google Blue 体系（≈#0B57D0 档位），刻意避开被滥用的 Indigo #6366F1（UIUX 文档 §4.2）。
 * - Android 12+ 优先使用系统动态取色（见 Theme.kt）。
 */
package com.gould.xputimetable.ui.theme

import androidx.compose.ui.graphics.Color

// 浅色主题（fallback，Android 8–11 使用）
val LightPrimary = Color(0xFF0B57D0)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD3E3FD)
val LightOnPrimaryContainer = Color(0xFF001D36)
val LightBackground = Color(0xFFF8F9FC)
val LightOnBackground = Color(0xFF1A1C1E)
val LightSurface = Color(0xFFF8F9FC)
val LightOnSurface = Color(0xFF1A1C1E)
val LightSurfaceVariant = Color(0xFFDFE2EB)
val LightOnSurfaceVariant = Color(0xFF43474E)
val LightOutline = Color(0xFF73777F)
// M5 需求 2/3：主页（周视图）背景与底栏取色基准（产品指定；仅亮色使用，深色保持 DarkSurface）
val LightPageBackground = Color(0xFFDDE0F1)

// 深色主题（fallback）
val DarkPrimary = Color(0xFFA8C7FA)
val DarkOnPrimary = Color(0xFF002F65)
val DarkPrimaryContainer = Color(0xFF00458F)
val DarkOnPrimaryContainer = Color(0xFFD3E3FD)
val DarkBackground = Color(0xFF111318)
val DarkOnBackground = Color(0xFFE2E2E9)
val DarkSurface = Color(0xFF111318)
val DarkOnSurface = Color(0xFFE2E2E9)
val DarkSurfaceVariant = Color(0xFF43474E)
val DarkOnSurfaceVariant = Color(0xFFC3C6CF)
val DarkOutline = Color(0xFF8D9199)
