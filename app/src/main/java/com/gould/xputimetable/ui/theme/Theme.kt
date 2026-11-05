/*
 * Theme.kt —— 应用主题（Material 3）
 *
 * 设计依据（UIUX 文档 §4.2 双层色彩系统）：
 * - 第一层「框架色」：Android 12+（API 31+）使用系统动态取色，让界面跟随壁纸；旧系统回落到 Color.kt 中的静态品牌色。
 * - 第二层「课程色」：固定 12 色调色板，绝不跟随动态取色 —— 具体见 CoursePalette。
 * - 亮/暗双轨跟随系统（深色是同等公民）。
 *
 * 本文件只覆盖 M3 必要色槽；接入完整设计令牌时整体替换。
 */
package com.gould.xputimetable.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun XpuTimetableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 动态取色开关（Spec §8：设置页会提供「跟随系统取色」开关，此处先做能力预留）
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkColorScheme(
            primary = DarkPrimary,
            onPrimary = DarkOnPrimary,
            primaryContainer = DarkPrimaryContainer,
            onPrimaryContainer = DarkOnPrimaryContainer,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = DarkOutline,
        )

        else -> lightColorScheme(
            primary = LightPrimary,
            onPrimary = LightOnPrimary,
            primaryContainer = LightPrimaryContainer,
            onPrimaryContainer = LightOnPrimaryContainer,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline,
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = XpuTypography,
        content = content,
    )
}
