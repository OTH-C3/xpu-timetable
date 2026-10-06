/*
 * NoteComposeSheet.kt —— 底部「补充待办」输入面板（M14 需求）
 *
 * 需求原话：「删除清单下面的添加待办，改为点击新建清单直接弹出这个，如果想要再补充的话。」
 * 参考小米便签的交互：
 *   - 点右下角「+」→ **先建一张空清单**，同时从底部升起输入面板；
 *   - 面板里回车加一条，继续回车可以连加（每加一条清单多一条）；
 *   - 点「完成」收面板。
 *
 * ## 为什么面板挂在**页面底部**而不是跟着卡片走
 *
 * 清单卡片在 LazyColumn 里，位置会随滚动变。面板若挂在卡片下方，
 * 用户加完两条往下滚，面板就跟着跑了；且键盘弹起时会与卡片抢空间。
 * 固定在页面底部（叠在底栏之上）则永远在拇指可达的位置 ——
 * 这也是参考图里它的位置。
 *
 * ## 为什么要"先建清单再输入"
 *
 * 反过来（先输入再建）会有一个尴尬的空窗期：用户敲完字点确认，才突然出现一张卡片。
 * 而"点 + 立刻看到一张空卡片 + 输入框"是即时反馈，
 * 也复刻了参考图第 3 张那张（空清单 + 底部输入面板）的观感。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.theme.ListRow
import com.gould.xputimetable.ui.theme.Note

/** 面板上的文案。 */
private const val HINT_INPUT = "回车即可连续添加待办"
private const val BTN_DONE = "完成"

/**
 * 底部「补充待办」输入面板。
 *
 * @param draft     当前输入
 * @param onDraftChange 输入变化
 * @param onSubmit  回车 → 加一条
 * @param onDismiss 点「完成」或收起
 * @param modifier  由调用方决定叠在页面底部的偏移
 */
@Composable
internal fun NoteComposeSheet(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // ⚠️ 这里**必须用不透明底色**，不能用玻璃面：
            // 面板是叠在 Scaffold **之外**的，会盖住底栏；玻璃面半透明会让
            // 底栏的"待办/课表/我的"和 FAB 透上来，与输入框叠在一起（2026-10-06
            // 模拟器实测：面板下方浮着半透明的 FAB 和三个底栏标签）。
            // 玻璃只留在这层**面板卡片本身**，整条底栏那条用不透明面。
            .background(MaterialTheme.colorScheme.surface)
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = ListRow.CardPadding, vertical = ListRow.CardPaddingV),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = HINT_INPUT,
                onSubmit = onSubmit,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Note.Gap))
            TextButton(onClick = onDismiss) {
                Text(
                    text = BTN_DONE,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 新建 / 补充的目标（M14）。
 *
 * 用 sealed interface 而不是 `Long?`：需要区分「刚新建的空清单」与
 * 「往已有清单补充」—— 前者要建卡片、后者不建，两条路径的副作用不同。
 */
internal sealed interface ComposeTarget {
    /** 点「+」新建：先建一张空清单，面板挂在它下面。 */
    data class Fresh(val listId: Long) : ComposeTarget

    /** 往已有清单补充：只开面板，不建卡片。 */
    data class Existing(val listId: Long) : ComposeTarget
}
