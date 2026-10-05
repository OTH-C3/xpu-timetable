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
 *
 * 作用范围从"单个枚举"改成"集合"后为什么另起一个键 `scopes`（老键 `scope` 留作兼容）：
 * 老存档里那个键存的是 String，新代码按 StringSet 读，同一个键换类型中间的坑不该由
 * 升级的用户去踩；[parseScopes] 负责把老值翻译成单元素集合。
 */
package com.gould.xputimetable.data.prefs

import android.content.Context

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gould.xputimetable.ui.background.BackgroundScope
import com.gould.xputimetable.ui.background.GLASS_PLAIN_ALPHA
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
    /** 作用范围集合（元素存枚举名而非 ordinal —— 调整枚举顺序不会串位）。 */
    val SCOPES = stringSetPreferencesKey("scopes")
    /**
     * 老版本的作用范围（单值，2026-10-04 之前）。
     *
     * 键名故意和 SCOPES 不同 —— 同一个 proto 键在老数据里是 STRING、新代码按 STRING_SET
     * 读，两边类型不一致，老值会变成读不出来的脏数据。换一个新键名，老键当兼容层读一次
     * 就够，不用赌 DataStore 对"同键换类型"会怎么处理。
     */
    val LEGACY_SCOPE = stringPreferencesKey("scope")
    /** 卡片（白色方框）不透明度的基准值，下限同 GlassCard.MinAlpha，上限 1（完全不透明）。 */
    val CARD_ALPHA = floatPreferencesKey("card_alpha")
}

/**
 * 背景不透明度默认值。
 *
 * 取 0.18 而不是 1.0：需求要的是"能看见背景图，但不影响读表"。课表是密集信息界面，
 * 不透明铺满会让课程卡的白面与底图花纹打架；0.18 是"明显是自定义背景、但字仍然最清楚"的位置。
 * 用户可自行往上拖。
 */
const val DEFAULT_BACKGROUND_OPACITY = 0.18f

/**
 * 卡片（白色方框）不透明度的**基准值**，默认与玻璃色的纯色背景档对齐。
 *
 * 取 GLASS_PLAIN_ALPHA 而不是另写一个数：基准值一旦和玻璃色的默认档不同步，
 * 「老用户升级后卡片观感变了」这种事在真机上只能靠肉眼发现，且没人会报告。
 * 可读性下限（GLASS_MIN_ALPHA）不在这里夹 —— 那是玻璃色自己的职责，
 * 存库的应是用户原始选择，多一层夹取只会让「存进去什么、界面显示什么」对不上。
 */
const val DEFAULT_CARD_ALPHA = GLASS_PLAIN_ALPHA

/** 一次读取拿齐四样值（避免 UI 层订阅多个 Flow 造成多帧不同步的闪烁）。 */
data class BackgroundSettings(
    val imageUri: String?,
    val opacity: Float,
    val scopes: Set<BackgroundScope>,
    val cardAlpha: Float,
)

/**
 * 「从未设置过」的初始值。
 *
 * 存在的理由：settings 是普通 Flow（不是 StateFlow），没有初值。
 * UI 层用 collectAsStateWithLifecycle 订阅时必须显式给初值，
 * 否则要等 DataStore 第一次落盘才渲染 —— 那一下白屏在冷启动时肉眼可见。
 */
/**
 * 「一个作用范围都没存过」时的默认值：全局。
 *
 * 和老版本 `scope` 缺省就是 GLOBAL 保持同一个数 —— 升级上来没写过任何背景偏好的老用户，
 * 读出来必须是"所有页面都有背景"，而不是空集合导致他的背景图凭空消失。
 * 想表达"一张都不画"是**空集合**的活，不是缺省值的活。
 */
val DEFAULT_SCOPES: Set<BackgroundScope> = setOf(BackgroundScope.GLOBAL)

val DEFAULT_BACKGROUND_SETTINGS = BackgroundSettings(
    imageUri = null,
    opacity = DEFAULT_BACKGROUND_OPACITY,
    scopes = DEFAULT_SCOPES,
    cardAlpha = DEFAULT_CARD_ALPHA,
)

private val Context.backgroundPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = STORE_NAME,
)

/**
 * 解析作用范围集合，**向后兼容老版本的单值**（internal 便于单测直接覆盖这条迁移路径）。
 *
 * 三档返回：
 *  1. 新键 `scopes` 有值 → 以它为准，老键直接无视；
 *  2. 新键没有、老键 `scope` 有值 → 老值当**单元素集合**（这是 2026-10-04 之前所有用户的
 *     存档形态，解析出来就是他当初选的那一档，背景图不会自己消失）；
 *  3. 两个键都没有 → [DEFAULT_SCOPES]（全局），与老版本的默认值同一个数。
 *
 * 老键里的值解析不出合法枚举名（几乎不可能：枚举删过成员）时返回**空集合**而不是回落全局：
 * 那种值本来就是脏数据，替用户"猜"一个档位反而会让背景莫名出现；空集合的含义是
 * "一个页面都不画"，至少是可预期的。UI 上也能立刻看出四项全没勾上。
 */
internal fun parseScopes(stored: Set<String>?, legacy: String?): Set<BackgroundScope> {
    val names = stored ?: legacy?.let(::listOf) ?: return DEFAULT_SCOPES
    return names.mapNotNull { name ->
        BackgroundScope.entries.firstOrNull { it.name == name }
    }.toSet()
}

/** 背景偏好的读写入口（构造注入，便于测试替身）。 */
class BackgroundPrefs(private val dataStore: DataStore<Preferences>) {

    /** 当前背景设置。读侧兜默认值，"从未设置过"即纯色背景。 */
    val settings: Flow<BackgroundSettings> = dataStore.data.map { prefs ->
        BackgroundSettings(
            imageUri = prefs[BgKeys.IMAGE_URI],
            opacity = (prefs[BgKeys.OPACITY] ?: DEFAULT_BACKGROUND_OPACITY).coerceIn(0f, 1f),
            scopes = parseScopes(prefs[BgKeys.SCOPES], prefs[BgKeys.LEGACY_SCOPE]),
            // 首次打开时从没写过这个键 → 取默认值，而不是 0（0 = 卡片全透明，字全看不清）
            cardAlpha = (prefs[BgKeys.CARD_ALPHA] ?: DEFAULT_CARD_ALPHA).coerceIn(0f, 1f),
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

    suspend fun setScopes(scopes: Set<BackgroundScope>) {
        dataStore.edit { prefs ->
            prefs[BgKeys.SCOPES] = scopes.map { it.name }.toSet()
        }
    }

    suspend fun setCardAlpha(alpha: Float) {
        dataStore.edit { prefs -> prefs[BgKeys.CARD_ALPHA] = alpha.coerceIn(0f, 1f) }
    }

    /** 恢复出厂（清除背景：删图片 URI，两档透明度与范围一并回默认）。 */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(BgKeys.IMAGE_URI)
            prefs[BgKeys.OPACITY] = DEFAULT_BACKGROUND_OPACITY
            prefs[BgKeys.SCOPES] = DEFAULT_SCOPES.map { it.name }.toSet()
            // 顺手把兼容键删掉，否则它永远挂在 proto 里当"影子值"
            prefs.remove(BgKeys.LEGACY_SCOPE)
            prefs[BgKeys.CARD_ALPHA] = DEFAULT_CARD_ALPHA
        }
    }

    companion object {
        fun create(context: Context): BackgroundPrefs = BackgroundPrefs(context.backgroundPrefsDataStore)
    }
}
