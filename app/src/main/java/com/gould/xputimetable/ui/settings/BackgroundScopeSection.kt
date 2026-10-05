/*
 * BackgroundScopeSection.kt —— 设置页「作用范围」这一栏（多选，M12 需求二.3 改）
 *
 * 原来是一列单选行（选了"全局"就别无其他），现在改成**四个可多勾**的档位：
 * 「全局 / 课表 / 我的 / 便签」都是普通选项，勾谁就把谁放进集合，互不影响。
 *
 * 为什么「全局」是一条普通档位而不是"全选"开关：
 * 需求原文是"便签，课表，我的，全局"这四个并列的名字。做成"全选"的话，
 * 它就变成了四档勾选状态之上的一层元状态，用户勾了"全局"再想改成"课表+便签"，
 * 得先解除全选 —— 多一步还可能留下"全选和四档不一致"这种永远同步不完的脏状态。
 * 存下来就是一个集合里含不含 GLOBAL，判定逻辑也只有一行（见 PageBackground）。
 *
 * 这一栏单独成文件而不是留在 BackgroundScreen 里：那文件已经顶到行数上限，
 * 而"作用范围"的勾选/取消语义（选中态、分隔线、提示语）是一块能自圆其说的完整逻辑。
 */
package com.gould.xputimetable.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gould.xputimetable.ui.background.BackgroundScope
import com.gould.xputimetable.ui.background.label
import com.gould.xputimetable.ui.components.AppIcons
import com.gould.xputimetable.ui.components.GroupCard
import com.gould.xputimetable.ui.components.GroupHeader
import com.gould.xputimetable.ui.theme.ListRow

/** 分组标题。 */
private const val GROUP_SCOPE = "作用范围"
private const val CD_SELECTED = "已选择"
private const val HINT_SCOPE = "可以同时勾选多个页面；勾「全局」则所有页面都有背景。"

/**
 * 「作用范围」一栏：四个档位各自可勾选。
 *
 * @param scopes 当前已勾选的档位全集（含 GLOBAL 时表示所有页面）
 * @param onToggle 点某一行后**切换**该档位的勾选状态（由调用方拿旧集合算新集合）
 */
@Composable
internal fun BackgroundScopeSection(
    scopes: Set<BackgroundScope>,
    onToggle: (BackgroundScope) -> Unit,
) {
    GroupHeader(GROUP_SCOPE)
    GroupCard {
        BackgroundScope.entries.forEachIndexed { index, scope ->
            ScopeCheckboxRow(
                label = scope.label,
                selected = scopes.contains(scope),
                showDivider = index < BackgroundScope.entries.lastIndex,
                onToggle = { onToggle(scope) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = HINT_SCOPE,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 作用范围的一个可勾选项：方框 + 名称；选中态一律黑字（项目口径：不用强调色）。 */
@Composable
private fun ScopeCheckboxRow(
    label: String,
    selected: Boolean,
    showDivider: Boolean,
    onToggle: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ListRow.MinHeight)
                .clickable(onClick = onToggle)
                .padding(horizontal = ListRow.HorizontalPadding, vertical = ListRow.VerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(ListRow.CheckboxSize)
                    .clip(RoundedCornerShape(ListRow.CheckboxCorner))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (selected) 1f else ListRow.DividerAlpha,
                        ),
                        shape = RoundedCornerShape(ListRow.CheckboxCorner),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        painter = painterResource(AppIcons.check),
                        contentDescription = CD_SELECTED,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(ListRow.CheckboxIcon),
                    )
                }
            }
            Spacer(Modifier.width(ListRow.IconGap))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ListRow.DividerInset)
                    .height(1.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant
                            .copy(alpha = ListRow.DividerAlpha),
                    ),
            )
        }
    }
}
