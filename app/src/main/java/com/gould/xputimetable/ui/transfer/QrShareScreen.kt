/*
 * QrShareScreen.kt —— 二维码分享页（M6 需求 6-B，导出侧）
 *
 * 数据流：exportSnapshot → ScheduleCodec.encode（紧凑 JSON）→ QrCodec.toQrPayload
 * （deflate+base64+前缀，超 2000 字节返回 null → 页面提示改用文件导出）→ QrCodec.encode 生成位图。
 *
 * 颜色纪律的既定例外（规格 §7.3）：二维码本体必须纯黑纯白才可扫（QrCodec 绘制时写死），
 * 外面套 surface 色圆角 Card 与深浅主题协调——App 自身 UI 不出现硬编码色值。
 *
 * 顶栏返回控件用应用内导航（不用系统返回键，本项目已两次踩坑）。
 * 保存/分享走 MediaStore 写自己的图 + ACTION_SEND——零新权限（不申请 CAMERA / 存储）。
 * MediaStore 直写需 API 29+；更低版本提示改用「分享」（真机验收环境为 Android 16）。
 */
package com.gould.xputimetable.ui.transfer

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.data.transfer.QrCodec
import com.gould.xputimetable.data.transfer.ScheduleCodec
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.theme.IconSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- 文件级文案常量（Spec §10 铁律 2）----------

private const val QR_TITLE = "二维码分享课表"
private const val QR_HINT = "对方在本 App「扫二维码导入」里选择这张截图即可导入"
private const val QR_TOO_LARGE = "课表较大，超出单张二维码容量，请改用「导出课表文件」分享"
private const val QR_EMPTY = "还没有课表可分享"
private const val QR_SAVE_OK = "已保存到相册"
private const val QR_SAVE_FAIL = "保存失败（需 Android 10 及以上），请改用「分享」"
private const val QR_SHARE_TEXT = "西工程大课表二维码"
private const val LABEL_SAVE = "保存到相册"
private const val LABEL_SHARE = "分享"

/** 二维码生成结果（位图 / 超限 / 空课表三态互斥）。 */
private data class QrLoadResult(
    val bitmap: Bitmap? = null,
    val tooLarge: Boolean = false,
    val empty: Boolean = false,
)

@Composable
internal fun QrShareScreen(
    repository: TimetableRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var loading by remember { mutableStateOf(true) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var tooLarge by remember { mutableStateOf(false) }
    var empty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = runCatching {
            val snapshot = repository.exportSnapshot()
            when {
                snapshot == null -> QrLoadResult(empty = true)
                else -> {
                    val payload = QrCodec.toQrPayload(ScheduleCodec.encode(snapshot))
                    when {
                        payload == null -> QrLoadResult(tooLarge = true)
                        else -> QrLoadResult(bitmap = QrCodec.encode(payload))
                    }
                }
            }
        }.getOrNull() ?: QrLoadResult(empty = true)
        loading = false
        qrBitmap = result.bitmap
        tooLarge = result.tooLarge
        empty = result.empty
    }

    Scaffold(
        // M12 需求一.2：底色与背景图由导航根的 PageBackground 统一提供，这里必须透明
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { QrTopBar(onBack = onBack, modifier = Modifier.statusBarsPadding()) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = QR_HINT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bitmap = qrBitmap
                when {
                    loading -> CircularProgressIndicator()
                    bitmap != null -> QrCard(bitmap)
                    tooLarge -> CenterHint(QR_TOO_LARGE)
                    empty -> CenterHint(QR_EMPTY)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        val bitmap = qrBitmap ?: return@OutlinedButton
                        scope.launch {
                            val saved = saveToGallery(context, bitmap)
                            if (saved == null) {
                                snackbarHostState.showSnackbar(QR_SAVE_FAIL)
                            } else {
                                shareImage(context, saved)
                                snackbarHostState.showSnackbar(QR_SAVE_OK)
                            }
                        }
                    },
                    enabled = qrBitmap != null,
                ) { Text(text = LABEL_SHARE) }
                Button(
                    onClick = {
                        val bitmap = qrBitmap ?: return@Button
                        scope.launch {
                            val saved = saveToGallery(context, bitmap)
                            snackbarHostState.showSnackbar(if (saved == null) QR_SAVE_FAIL else QR_SAVE_OK)
                        }
                    },
                    enabled = qrBitmap != null,
                ) { Text(text = LABEL_SAVE) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 二维码展示：本体纯黑纯白（可扫），外套 surface 色圆角 Card 与主题协调（既定例外）。 */
@Composable
private fun QrCard(bitmap: Bitmap) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "课表二维码",
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(16.dp).size(280.dp),
        )
    }
}

@Composable
private fun CenterHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

/** 顶栏：返回 + 标题（自绘，须自吃状态栏内边距）。 */
@Composable
private fun QrTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(AppIcons.chevronLeft),
                contentDescription = "返回",
                modifier = Modifier.size(IconSize.Medium),
            )
        }
        Text(text = QR_TITLE, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * 写入相册（MediaStore Pictures，写的是本 App 生成的图，零权限）。
 * API < 29 无法免权限直写 MediaStore → 返回 null（UI 提示改用分享）。
 */
private suspend fun saveToGallery(context: Context, bitmap: Bitmap): Uri? =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) return@withContext null
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "xpu-timetable-qr-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val outUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            resolver.openOutputStream(outUri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            resolver.update(
                outUri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )
            outUri
        }.getOrNull()
    }

/** 系统分享刚写入 MediaStore 的 content uri（可授予读权限，无需 FileProvider）。 */
private fun shareImage(context: Context, imageUri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, imageUri)
        putExtra(Intent.EXTRA_TITLE, QR_SHARE_TEXT)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(send, QR_SHARE_TEXT)) }
}
