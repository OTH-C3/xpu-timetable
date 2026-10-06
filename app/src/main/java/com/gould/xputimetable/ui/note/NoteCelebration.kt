/*
 * NoteCelebration.kt —— 待办页的庆祝状态与一次性动效（M12/M13/M14 需求二、三）
 *
 * 为什么单独成文件：这几段状态（彩带触发、已完成分区首次滑入、彩带 Overlay）
 * 是**同一条时间线**上的东西 —— "勾完全部待办 → 彩带 → 清单收进已完成"，
 * 拆散到页面文件里会看不出它们的先后顺序。收在一起，注释放得下，
 * 页面文件也守得住 300 行门禁。
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

    /**
     * 彩带**已经播完**的序号。
     *
     * ⚠️ 这个字段是修 BUG「新建清单后彩带重播」的关键（M14）。
     * 现象：喷一次彩带后，新建清单或添加待办 → 列表重排 → 卡片的
     * `onGloballyPositioned` 再次上报发射点 → 彩带又喷一次。
     *
     * 根因不在上报，而在**Overlay 的挂载条件**：原先是
     * `if (burstOrigin != null) { ConfettiBurst(...) }`，
     * 而 `ConfettiBurst` 播完（alpha 归 0）会 `return` 离开组合；
     * 此时 `burstOrigin` 仍是上次的值，只要它被重新赋值，
     * 就会"重新进组合" → `remember(trigger)` 重新初始化 → 从头再播一次。
     *
     * 现在改成显式记账：播过的序号记在这里，条件里比对，
     * 同一个序号**永远不再进组合**，与 `burstOrigin` 被重新上报无关。
     */
    var playedSeq: Long by mutableStateOf(0L)

    /**
     * 彩带发射点（页面根坐标，由卡片 `onGloballyPositioned` 上报）。
     *
     * M14：需求改为"从屏幕顶部一整条线撒落"（见小米便签参考截图），
     * 所以这里存的**不再是某个勾选框的中心**，而是一整条横向区间的取样点 ——
     * 真正怎么撒是 `Confetti.kt` 的事，这里只负责给出"顶部那条线在哪"。
     */
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

    /**
     * 上一轮「**所有**待办是否都完成了」。
     *
     * M14 需求：彩带只在**页面里再没有任何未完成的清单**时才喷
     * （原实现是"任意一张清单完成就喷"，于是勾一张喷一次，清单多了就乱）。
     * 存 Boolean 而非 Map：判据从"逐张比对"简化成"整页比对"，
     * 顺带消掉了"Map 里漏掉新建清单的 id"这类边界。
     */
    var prevAllSettled: Boolean by mutableStateOf(false)

    /** 上一轮清单数（首轮为 0 → 进页面不会误喷）。 */
    var prevListCount: Int by mutableStateOf(0)
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
    // M14 需求：「**所有**未完成代办全部完成才能喷彩带」。
    // 判据是整页：**一张"进行中"的清单都不剩**，才算全部完成。
    // 空清单不算（与 `allDone` 判空一致）—— 否则刚建一张空清单就喷彩带。
    //
    // ⚠️ 同时必须挡掉「刚进页面时页面本来就是空的」：进页面时 active 为空，
    // `settled` 为 true，但那时并没有"刚刚完成"这件事。所以要求
    // **上一轮有清单**（prevListCount > 0）且**这一轮有变化**。
    LaunchedEffect(sections) {
        val all = sections.active + sections.completed
        val settled = sections.active.isEmpty()
        val changed = state.prevListCount != all.size || state.prevAllSettled != settled
        if (shouldBurstConfetti(
                hadListsBefore = state.prevListCount > 0,
                wasSettled = state.prevAllSettled,
                settledNow = settled,
                allChanged = changed,
            )
        ) {
            state.confettiSeq += 1
        }
        state.prevListCount = all.size
        state.prevAllSettled = settled
    }

    // 彩带播完就记账：同一个序号不再进组合（BUG「新建后重播」的修法，见 playedSeq 注释）
    LaunchedEffect(state.confettiSeq) {
        if (state.confettiSeq > 0L) {
            delay(Note.ConfettiMillis.toLong() + 200L)
            state.playedSeq = state.confettiSeq
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
 *
 * @param topLineY 页面根坐标下"屏幕顶部那条撒落线"的 y。
 *                 页面级才知道状态栏有多高，所以由调用方算好传进来。
 */
@Composable
internal fun ConfettiOverlay(
    state: NoteCelebrationState,
    topLineY: Float,
    modifier: Modifier = Modifier,
) {
    val seq = state.confettiSeq
    // ⚠️ 三个条件缺一不可，顺序也有讲究：
    //   ① seq > 0        —— 没触发过就不画
    //   ② seq != played  —— 播过就**永远不再进组合**（这是 BUG 重播的修法）
    //   ③ burstOrigin    —— 等卡片报上来发射点
    // ② 必须在 ③ 之前：否则 origin 后到时仍会因 ① 通过而重播。
    if (seq <= 0L || seq == state.playedSeq) return
    ConfettiBurst(
        trigger = seq,
        topLineY = topLineY,
        modifier = modifier.fillMaxSize(),
    )
}
