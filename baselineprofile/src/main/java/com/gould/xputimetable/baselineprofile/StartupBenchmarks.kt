/*
 * StartupBenchmarks.kt —— 冷启动 A/B 基准（M8）
 *
 * 为什么需要它：`assets/dexopt/baseline.prof` 是否真的进包，可以用解包核对；
 * 但"它到底带来多少启动提速"**只能实测**。`adb shell am start -W` 的 TotalTime
 * 精度不足（±10% 抖动），且模拟器是软件渲染，测不出差异——本项目实测：
 * 含 profile 中位 393ms / 不含 382ms，无法据此下结论。
 *
 * 本类用 Macrobenchmark 的标准做法做同设备 A/B：
 *   - 同一台设备、同一份 APK，只改**编译模式**这一个变量；
 *   - 每个用例 10 次迭代，输出 TTID（timeToInitialDisplay）的 min/median/max；
 *   - Partial(BaselineProfileMode.Require) 强制使用 APK 内 profile；
 *     None() 明确不使用任何 profile/AOT（每次重置 ART 状态）。
 *
 * 注意 请在**真机**上跑（这是权威做法）。模拟器上 benchmark 库会判为无效环境，
 *    需要 `-e androidx.benchmark.suppressErrors=EMULATOR` 才能强行运行，结果仅供参考。
 *
 * 运行方式：
 *   ANDROID_SERIAL=<serial> ./gradlew :baselineprofile:connectedNonMinifiedReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.gould.xputimetable.baselineprofile.StartupBenchmarks
 * 结果会打印在 Gradle 日志里（形如 `StartupBenchmarks_coldStartupWithBaselineProfile
 * timeToInitialDisplayMs min 161.8, median 178.9, max 194.6`）。
 */
package com.gould.xputimetable.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ITERATIONS = 10

@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** A 组：使用 APK 内的 baseline profile。 */
    @Test
    fun coldStartupWithBaselineProfile() =
        startup(CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require))

    /** B 组：完全不用 profile / AOT，作为对照。 */
    @Test
    fun coldStartupWithoutBaselineProfile() = startup(CompilationMode.None())

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) {
        pressHome()
        startActivityAndWait()
    }
}
