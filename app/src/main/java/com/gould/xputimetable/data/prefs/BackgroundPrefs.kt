/*
 * BackgroundPrefs.kt —— 自定义背景的偏好存储（DataStore Preferences，M12 需求二）
 *
 * 存三样东西：图片 URI、背景不透明度、作用范围。为什么放 DataStore 而不是 Room：
 *   与既有的"数据归 Room、界面偏好归 DataStore"分层一致（见 UiPrefs.kt 文件头），
 *   且这三样都是"每用户一个值"的展示偏好，没有查询与关联需求。
 *
 * 为什么单独一个 DataStore 文件而不是并进 ui_prefs：
 *   背景设置是 M12 新增的一整块需求（含 URI 字符串这种长值），独立成文件后
 *   将来若整体撤销该功能，只需删掉这一个文件，ui_prefs 的既有键不受任何影响。
 *
 * ⚠️ 图片 URI 权限：SAF 选图返回的是**临时读权限**，进程被杀后失效。
 *   因此取到 URI 时必须调 takePersistableUriPermission 把它升级为持久授权，
 *   否则下次冷启动背景图会变成空白（详见 BackgroundScreen 的取图回调）。
 */
package com.gould.xputimetable.data.prefs

import android.content.Context

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gould.xputimetable.ui.background.BackgroundScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 存储名（文件 background_prefs.pb）。改名字等于丢掉用户既有背景设置。 */
private const val STORE_NAME = "background_prefs"

/** 偏好键集中在这里，避免散落字符串。
 *  命名带 Bg 前缀：UiPrefs.kt 同包也有一份 private object Keys，
 *  Kotlin 的 private 是文件级可见性，同包不同文件仍然撞名。 */
private object BgKeys {
    /** 背景图片的 content:// URI；未设置时为 null（= 纯色背景）。 */
    val IMAGE_URI = stringPreferencesKey("image_uri")
    /** 背景不透明度，0f（全透，等于没有图）~ 1f（完全不透明盖住页面底色）。 */
    val OPACITY = floatPreferencesKey("opacity")
    /** 作用范围的枚举名（存 name() 而非 ordinal —— 调整枚举顺序不会串位）。 */
    val SCOPE = stringPreferencesKey("scope")
}

/**
 * 背景不透明度默认值。
 *
 * 取 0.18 而不是 1.0：需求要的是"能看见背景图，但不影响读表"。课表是密集信息界面，
 * 不透明铺满会让课程卡的白面与底图花纹打架；0.18 是"明显是自定义背景、但字仍然最清楚"的位置。
 * 用户可自行往上拖。
 */
const val DEFAULT_BACKGROUND_OPACITY = 0.18f

/** 一次读取拿齐三样值（避免 UI 层订阅三个 Flow 造成三帧不同步的闪烁）。 */
data class BackgroundSettings(
    val imageUri: String?,
    val opacity: Float,
    val scope: BackgroundScope,
)

/**
 * 「从未设置过」的初始值。
 *
 * 存在的理由：settings 是普通 Flow（不是 StateFlow），没有初值。
 * UI 层用 collectAsStateWithLifecycle 订阅时必须显式给初值，
 * 否则要等 DataStore 第一次落盘才渲染 —— 那一下白屏在冷启动时肉眼可见。
 */
val DEFAULT_BACKGROUND_SETTINGS = BackgroundSettings(
    imageUri = null,
    opacity = DEFAULT_BACKGROUND_OPACITY,
    scope = BackgroundScope.GLOBAL,
)

private val Context.backgroundPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = STORE_NAME,
)

/** 背景偏好的读写入口（构造注入，便于测试替身）。 */
class BackgroundPrefs(private val dataStore: DataStore<Preferences>) {

    /** 当前背景设置。读侧兜默认值，"从未设置过"即纯色背景。 */
    val settings: Flow<BackgroundSettings> = dataStore.data.map { prefs ->
        BackgroundSettings(
            imageUri = prefs[BgKeys.IMAGE_URI],
            opacity = (prefs[BgKeys.OPACITY] ?: DEFAULT_BACKGROUND_OPACITY).coerceIn(0f, 1f),
            // 名字反查失败（老版本存过已删除的枚举名）时回落到全局，不让背景整个消失
            scope = prefs[BgKeys.SCOPE]?.let { name ->
                BackgroundScope.entries.firstOrNull { it.name == name }
            } ?: BackgroundScope.GLOBAL,
        )
    }

    suspend fun setImageUri(uri: String?) {
        dataStore.edit { prefs ->
            if (uri == null) prefs.remove(BgKeys.IMAGE_URI) else prefs[BgKeys.IMAGE_URI] = uri
        }
    }

    suspend fun setOpacity(opacity: Float) {
        dataStore.edit { prefs -> prefs[BgKeys.OPACITY] = opacity.coerceIn(0f, 1f) }
    }

    suspend fun setScope(scope: BackgroundScope) {
        dataStore.edit { prefs -> prefs[BgKeys.SCOPE] = scope.name }
    }

    /** 恢复出厂（清除背景：删图片 URI，透明度与范围一并回默认）。 */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(BgKeys.IMAGE_URI)
            prefs[BgKeys.OPACITY] = DEFAULT_BACKGROUND_OPACITY
            prefs[BgKeys.SCOPE] = BackgroundScope.GLOBAL.name
        }
    }

    companion object {
        fun create(context: Context): BackgroundPrefs = BackgroundPrefs(context.backgroundPrefsDataStore)
    }
}
