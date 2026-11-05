/*
 * CaptureDiagnostics.kt —— 教务直连的诊断卡片
 *
 * 为什么单独成文件：CourseCaptureScreen 已接近 300 行门禁，而这块是"出问题才看"
 * 的辅助 UI，与主管的 WebView 宿主没有耦合，拆出来两边都好读。
 *
 * 用途：学校改版后 App 抓不到课表时，把 WebView **实际请求过**的数据接口路径列出来。
 * 只显示路径，绝不显示 Cookie、token 或响应体 —— 这条既是隐私红线，也是让用户
 * 敢把这块截图发给开发者的前提。
 */
package com.gould.xputimetable.ui.web

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val DIAG_TITLE = "未识别到课表时，这里会显示学校实际请求的接口"
private const val DIAG_EMPTY = "还没有数据请求。完成登录、进入课表页后会自动出现。"

@Composable
internal fun CaptureDiagnostics(paths: List<String>, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = DIAG_TITLE,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (paths.isEmpty()) {
                Text(
                    text = DIAG_EMPTY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                paths.forEach { p ->
                    Text(
                        text = p,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
