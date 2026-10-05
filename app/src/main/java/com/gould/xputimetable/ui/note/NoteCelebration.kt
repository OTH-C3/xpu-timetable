/*
 * NoteCelebration.kt —— 待办页的庆祝状态与一次性动效（M12/M13 需求二、三）
 *
 * 为什么单独成文件：这三段状态（彩带触发、已完成分区首次滑入、彩带 Overlay）
 * 是**同一条时间线**上的东西 —— "勾完最后一项 → 彩带 → 卡片移进已完成"，
 * 拆散到页面文件里会让人看不出它们的先后顺序。收在一起，注释放得下，
 * 页面文件也守得住 300 行门禁（M13 加动效时 NoteScreen 一度超到 367 行）。
 */
package com.gould.xputimetable.ui.note

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.gould.xputimetable.domain.model.TodoSections
import com.gould.xputimetable.ui.theme.Note
import kotlinx.coroutines.delay

/**
 * 待办页的庆祝状态持有者。
 *
 * 用 `remember` 持有而非放进 ViewModel：这些是**一次性动画的进度**，
 * 播完就该忘掉；持久化它们只会带来"下次进页面又播一遍"的怪现象。
 */
class NoteCelebrationState {
    /** 彩带播放序号：单调递增，每次 +1 触发一次重播。 */
    var confettiSeq: Long by mutableStateOf(0L)

    /** 彩带发射点（页面根坐标，由卡片 onGloballyPositioned 上报）。 */
    var burstOrigin: Offset? by mutableStateOf(null)

    /** 「已完成」分区是否已经出现过 —— 决定要不要播"从上往下挪"的那一次。 */
    var doneGroupSeen: Boolean by mutableStateOf(false)

    /**
     * 「已完成」分区滑入的位移。
     *
     * ⚠️ **初值必须是 0（就位），不能是起点偏移**。若初值给 -offset，那么
     * "进页面时分组已存在"这种场景下（对应的 effect 会因为 `doneGroupSeen`
     * 已是 true 而不播动画）标题会**永久停在 -84px**，压住下面第一张卡片 ——
     * 2026-10-04 真机实测到这个现象。正确写法是静止时永远 0，要动的时候
     * 由 effect 先 `snapTo` 起点再 `animateTo(0)`。
     */
    val doneGroupSlide = Animatable(0f)

    /** 上一轮每张清单是否全部完成（首轮为空表 → 进页面不会误喷）。 */
    var prevAllDone: Map<Long, Boolean> by mutableStateOf(emptyMap())
}

/** 记住一个庆祝状态持有者。 */
@Composable
internal fun rememberCelebrationState(): NoteCelebrationState = remember { NoteCelebrationState() }

/**
 * 接线：监听清单变化，触发彩带与「已完成」首次滑入。
 *
 * @param sections 当前清单（唯一数据源）
 * @param state    庆祝状态
 */
@Composable
internal fun NoteCelebrationEffects(
    sections: TodoSections,
    state: NoteCelebrationState,
) {
    // ---- 彩带触发 ----
    //
    // ⚠️ 关键设计：彩带**不能**挂在卡片里。清单一旦整组完成就会（延迟彩带播完后）
    // 从"进行中"移进"已完成"，而后者默认是折叠的 —— 卡片当场离开组合，
    // 挂在它身上的彩带一帧都画不出来就消失了（2026-10-04 真机实测：
    // 勾完只看到清单消失，完全没有彩带）。
    // 所以改成：卡片把自己的中心**报上来**，彩带由页面级 Overlay 统一画，
    // 与卡片的存亡无关。
    LaunchedEffect(sections) {
        val all = sections.active + sections.completed
        val newlyDone = all.filter { list ->
            val was = state.prevAllDone[list.id]
            was == false && list.allDone    // was 存在且为 false 才是"刚刚完成"
        }
        state.prevAllDone = all.associate { it.id to it.allDone }
        if (newlyDone.isNotEmpty()) state.confettiSeq += 1
    }

    // 彩带播完就清空发射点，避免之后的每次重组都重新喷一遍
    LaunchedEffect(state.confettiSeq) {
        if (state.confettiSeq > 0L) {
            delay(Note.ConfettiMillis.toLong() + 200L)
            state.burstOrigin = null
        }
    }

    // ---- 「已完成」首次出现：一次性的"从上往下挪"。播完停在 0，之后永远就位。 ----
    //
    // 用 snapTo + animateTo 两步，而不是把初值设成偏移（原因见 doneGroupSlide 的注释）。
    LaunchedEffect(sections.completed.isNotEmpty()) {
        val hasDone = sections.completed.isNotEmpty()
        if (hasDone && !state.doneGroupSeen) {
            state.doneGroupSlide.snapTo(-Note.DoneGroupEnterOffsetPx)
            state.doneGroupSlide.animateTo(
                targetValue = 0f,
                animationSpec = tween(Note.DoneGroupEnterMillis),
            )
        }
        state.doneGroupSeen = hasDone
    }
}

/**
 * 彩带 Overlay：**必须挂在 Scaffold 之外**（与它同级的 Box 根）。
 *
 * 因为卡片用 `positionInRoot()` 上报的是**窗口根坐标**；若 Overlay 放在
 * Scaffold 的 padding 内部，两者原点差一个状态栏 + topBar 高度，
 * 彩带会整体下移一截。放同级 Box 才能保证坐标系完全一致。
 */
@Composable
internal fun ConfettiOverlay(
    state: NoteCelebrationState,
    modifier: Modifier = Modifier,
) {
    val origin = state.burstOrigin ?: return
    ConfettiBurst(
        trigger = state.confettiSeq,
        origin = origin,
        modifier = modifier.fillMaxSize(),
    )
}
