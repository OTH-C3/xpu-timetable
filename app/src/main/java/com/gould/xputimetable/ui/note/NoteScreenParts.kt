/*
 * NoteScreenParts.kt —— 待办页的小构件：空态、已完成分组头、折叠状态判定
 *
 * 为什么要拆：NoteScreen.kt 承担了列表装配 + 删除确认两件事，已经接近 300 行门禁；
 * 这些纯展示的小构件单独成文件后，主文件一眼能看出结构。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.domain.model.TodoList
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.components.clickableNoRipple
import com.gould.xputimetable.ui.theme.Note

/** 空态：清单图标 + 「没有待办」（参考图同款：图标在上、文字在下居中）。 */
@Composable
internal fun NoteEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(AppIcons.listTodo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(Note.EmptyIconSize),
        )
        Spacer(Modifier.height(Note.Gap))
        Text(
            text = "没有待办",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 「已完成 N」分组头（需求 3：整组完成后归到这里，可再展开查看）。 */
@Composable
internal fun CompletedHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ⚠️ 整行可点，热区铺满整行宽度。
    // 上一版把 clickable 只挂在行尾一个 40dp 的 Spacer 上，实测那个 Spacer 落在
    // 屏幕最右侧 [1215,348][1395,528]，而用户的直觉是去点左边的箭头（x≈94）——
    // 点箭头毫无反应，"已完成"分组看起来像坏了。
    // 现在整行都是热区，点哪都行；图标本身只有 18dp，视觉不变。
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick = onToggle)
            .padding(top = Note.Gap, bottom = Note.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                if (expanded) AppIcons.chevronUp else AppIcons.chevronDown,
            ),
            contentDescription = if (expanded) "收起已完成" else "展开已完成",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Note.ExpandIcon),
        )
        Spacer(Modifier.width(Note.GapSmall))
        Text(
            text = "已完成 $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 这张清单当前该展开还是折叠（需求 3 的判定，纯函数）。
 *
 * @param overrides 用户手动点过的折叠状态（id → 展开?）。为 null 表示用户没管过这张清单，
 *                 此时按"整组是否完成"自动折叠。
 */
fun isExpanded(list: TodoList, overrides: Map<Long, Boolean>): Boolean =
    overrides[list.id] ?: !list.allDone

/**
 * 折叠状态表的取反（不可变更新，避免每次重组都造新 Map）。
 *
 * ⚠️ 必须把**当前实际显示的展开态**传进来，不能像原先那样在表里没记录时就假定
 * 「现在是展开的」。原因：已完成清单的初始态来自 [isExpanded] 的自动判定（折叠），
 * 表里并没有记录。原先 `base = current ?: true` 会让第一次点击写成
 * `id to false` —— 折叠的折叠，还是折叠，**点第一下完全没反应**，
 * 看起来像折叠按钮坏了（2026-10-04 真机实测：点「已完成」里那张卡片要等两下才展开）。
 * 现在改成以界面真实状态为准，点一下必定切换。
 *
 * @param currentExpanded 该清单**此刻在界面上**是否展开（= isExpanded(list, this)）
 */
fun Map<Long, Boolean>.toggle(id: Long, currentExpanded: Boolean): Map<Long, Boolean> =
    this + (id to !currentExpanded)

/**
 * 勾上这一条之后，它所在的清单会不会变成「整组已完成」（M13 需求 3 的判定，纯函数）。
 *
 * 需求是「答完后先出彩带，再进入已完成」，而落库即分组 —— 一写库这张清单就被
 * 划进已完成区，卡片当场离开屏幕，彩带还没播完就没了。所以要在这里先算出
 * "这次勾选是否会让整组完成"，让调用方决定要不要推迟落库。
 *
 * 判据：除当前这条外，其余条目**此刻都已完成**（当前这条的旧值必然是未完成，
 * 否则这次勾选就不是"完成"而只是"再勾一次已完成的东西"）。
 *
 * 放在这里而不是 ViewModel 私有方法：ViewModel 依赖 repository 与 viewModelScope，
 * 纯 JVM 单测跑不起来；提成顶层纯函数后这条时序规则能被单测钉住。
 *
 * @param lists 当前全部清单（进行中 + 已完成）
 * @param itemId 本次要勾的条目
 * @return true = 勾完就整组完成 → 该推迟落库等彩带
 */
fun wouldCompleteGroup(lists: List<TodoList>, itemId: Long): Boolean {
    val list = lists.firstOrNull { l -> l.items.any { it.id == itemId } } ?: return false
    // 空清单：勾任何东西都谈不上"整组完成"（与 allDone 判空的口径一致）
    if (list.items.isEmpty()) return false
    return list.items.all { it.id == itemId || it.done }
}

/**
 * 条目在**当前这一刻**的勾选态（乐观值优先，纯函数）。
 *
 * M14 修 BUG「点最后一项没划线动效」：勾完整组的最后一项时落库被推迟 660ms
 * （让彩带先播），而划线动画读的是这个值。若只读库里的 `done`，
 * 划线要等 660ms 才开始，那时清单已经被收进「已完成」—— 用户看不到那条线。
 *
 * @param itemDone   库里的值（权威，只是有延迟）
 * @param optimistic 乐观值（用户刚点的状态）；null = 没有待落库的改动
 */
fun resolvedDone(itemDone: Boolean, optimistic: Boolean?): Boolean = optimistic ?: itemDone

/**
 * 彩带该不该在**这一轮**喷（纯函数，M14 需求「所有未完成代办全部完成才能喷」）。
 *
 * 四个条件缺一不可：
 *  1. `hadListsBefore` —— 上一轮页面有清单。挡掉"进页面时本来就全空"，
 *     那时并没有"刚刚完成"这件事，不该喷。
 *  2. `!wasSettled`   —— 上一轮**还有**未完成的清单。否则空页面刷新也会喷。
 *  3. `settledNow`    —— 这一轮一张"进行中"的清单都不剩。
 *  4. `allChanged`    —— 清单内容确实变了。
 *     挡掉"数据没变但重组了"（例如 Room 推送了等值结果）。
 *
 * @param hadListsBefore 上一轮清单数 > 0
 * @param wasSettled     上一轮是否已全部完成
 * @param settledNow     这一轮是否已全部完成
 * @param allChanged     这一轮清单内容是否变化过
 */
fun shouldBurstConfetti(
    hadListsBefore: Boolean,
    wasSettled: Boolean,
    settledNow: Boolean,
    allChanged: Boolean,
): Boolean = hadListsBefore && !wasSettled && settledNow && allChanged

/**
 * 组装一张清单卡片的全套接线（NoteScreen 用）。
 *
 * 为什么把这一大段从 NoteScreen 搬出来：那些参数本质上是**卡片的接线**
 * （卡片要的每一项都从哪来），跟着卡片走更自然；页面只留"把两段清单喂进去"。
 *
 * 回调一律方法引用、不内联 lambda：项目既有纪律（避免整页重组）。
 *
 * @param onToggleExpanded 折叠态切换 —— 必须由页面写回 state，所以是回调而非读值
 */
@Composable
internal fun noteCardFor(
    ui: NoteUiState,
    viewModel: NoteViewModel,
    isExpandedNow: (TodoList) -> Boolean,
    onToggleExpanded: (TodoList) -> Unit,
    onDeleteList: (TodoList) -> Unit,
    onDeleteItem: (Long, String) -> Unit,
): @Composable (TodoList) -> Unit = { list ->
    NoteListCard(
        list = list,
        expanded = isExpandedNow(list),
        onToggleExpand = { onToggleExpanded(list) },
        onToggleItem = viewModel::toggleItem,
        onDeleteList = { onDeleteList(list) },
        onDeleteItem = onDeleteItem,
        onAddItem = { listId -> viewModel.addItem(listId) },
        // 乐观勾选态：整组最后一项的落库被推迟 660ms 让彩点先撒，
        // 划线动画读这个值才能**立刻**开始（否则线永远看不到）
        optimisticDone = { itemId ->
            resolvedDone(list.items.first { it.id == itemId }.done, ui.optimisticDone[itemId])
        },
        editingTitle = ui.editingTitleOf == list.id,
        titleDraft = ui.titleDraft,
        onStartEditTitle = { viewModel.startEditingTitle(list.id, list.title) },
        onTitleDraftChange = viewModel::setTitleDraft,
        onCommitTitle = viewModel::commitTitle,
        onOpenCompose = { viewModel.openCompose(list.id) },
        enterFromBottom = ui.newListId == list.id,
        onEnterAnimationDone = viewModel::consumeNewListAnimation,
    )
}
