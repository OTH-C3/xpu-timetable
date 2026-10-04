#!/usr/bin/env bash
# measure-startup-ab.sh —— 真机冷启动 A/B 测量（Baseline Profile 有/无）
#
# 为什么要有这个脚本：`assets/dexopt/baseline.prof` 是否进包可以用解包核对，
# 但"它到底带来多少启动提速"只能实测，且必须**同设备、同 APK、只改编译模式**才有意义。
# `adb shell am start -W` 的 TotalTime 精度不足（实测模拟器上 393ms vs 382ms 分辨不出），
# 故用 Macrobenchmark 的 StartupBenchmarks：10 次迭代，输出 TTID 的 min/median/max。
#
# 用法：./scripts/measure-startup-ab.sh <device-serial>
#
# ⚠️⚠️ 血泪教训：**不要用 `gradle :baselineprofile:connectedNonMinifiedReleaseAndroidTest` 跑**！
#   该任务在结束时（无论用例跑没跑）会**卸载被测 App**，卸载即删除应用数据 ——
#   本项目实测把真机上导入好的真实课表 + 学期设置全部清空，不可恢复（只能重新教务导入）。
#   故本脚本改用**手动 instrumentation**（am instrument），完全不经过 AGP 的装卸流程。
#
# ⚠️ 另两个必须知道的点：
#   1. 命令行直跑必须显式传 `androidx.benchmark.enabledRules=Macrobenchmark`。
#      MacrobenchmarkRule 第一件事是
#        Assume.assumeTrue(Arguments.getEnabledRules().contains(RuleType.Macrobenchmark))
#      不传 → 集合为空 → **所有用例被 assume 静默跳过**，
#      表现为「BUILD SUCCESSFUL / OK 但一个测量都没有、耗时十几秒」。
#   2. 测的是 nonMinifiedRelease 变体（Macrobenchmark 要求 app profileable），
#      所以要先 `:app:assembleNonMinifiedRelease` 并确认 APK 里带的是**目标 profile**
#      （解包看 `assets/dexopt/baseline.prof` 大小）。
set -euo pipefail

SERIAL="${1:?用法: $0 <device-serial>（先用 adb devices 查）}"
SDK="${ANDROID_SDK_ROOT:-/home/othc3/Android/Sdk}"
ADB="$SDK/platform-tools/adb"
GRADLE="${GRADLE_BIN:-/home/othc3/opt/gradle-9.7.1/bin/gradle}"
export JAVA_HOME="${JAVA_HOME:-/home/othc3/opt/jdk-21b}"
PKG="com.gould.xputimetable"
TESTPKG="com.gould.xputimetable.baselineprofile"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
BENCH_CLASS="com.gould.xputimetable.baselineprofile.StartupBenchmarks"
APP_APK="app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk"
TEST_APK="baselineprofile/build/outputs/apk/nonMinifiedRelease/baselineprofile-nonMinifiedRelease.apk"

echo "== 0. 前置检查 =="
"$ADB" -s "$SERIAL" get-state >/dev/null || { echo "设备 $SERIAL 不可用"; exit 1; }
FG=$("$ADB" -s "$SERIAL" shell dumpsys window | grep mCurrentFocus | head -1 || true)
echo "  当前前台: $FG"
case "$FG" in
  *launcher*|*"$PKG"*|*NotificationShade*) : ;;
  *) echo "  ⚠ 前台既不是桌面也不是本 App → 可能正在使用手机，已中止（项目纪律：先停手并汇报）"; exit 2 ;;
esac

echo "== 1. 构建两份 APK =="
"$GRADLE" :app:assembleNonMinifiedRelease :baselineprofile:assembleNonMinifiedRelease --console=plain > /tmp/ab-build.log 2>&1 \
  || { echo "  构建失败，见 /tmp/ab-build.log"; exit 1; }
python3 - <<'PY'
import zipfile
z = zipfile.ZipFile('app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk')
sizes = {n: z.getinfo(n).file_size for n in z.namelist() if 'dexopt' in n}
print('  APK 内 profile:', sizes)
if not sizes:
    raise SystemExit('  ✗ APK 里没有 profile —— 先跑 :app:generateBaselineProfile')
PY

echo "== 2. 只读核对动画缩放（本脚本【不修改任何设备设置】） =="
# ⚠️ 项目纪律（2026-10-01 事故后确立）：**禁止修改用户手机的 settings**。
#    起因：为跑基准我 `settings put system screen_off_timeout` 后收尾用了 `settings delete`，
#    把用户自定义的熄屏时间抹成系统默认且无法还原（原值未记录）。
#    因此本脚本对设备**只读**：若前置条件不满足就中止，交由用户决定要不要自己改。
#    Macrobenchmark 要求三个动画缩放为 0，实测该库自身不会去改（7 个 jar 检索无动画设置键）。
NEED_ZERO=0
for k in $KEYS; do
  v=$("$ADB" -s "$SERIAL" shell settings get global "$k" | tr -d '\r')
  printf '  %-26s = %s\n' "$k" "$v"
  [ "$v" = "0" ] || NEED_ZERO=1
done
if [ "$NEED_ZERO" != "0" ]; then
  cat <<'MSG'
  ⚠ 前置条件不满足：Macrobenchmark 要求三个动画缩放均为 0。
  本脚本**不会替你改设备设置**（项目纪律：不改用户手机的任何 settings）。
  如果你愿意自己开：开发者选项 → 「窗口动画缩放 / 过渡动画缩放 / Animator 时长缩放」全部设为「关闭」，
  跑完测量后再自己改回原值。改完重跑本脚本即可。
MSG
  exit 4
fi
echo "  ✓ 动画缩放已为 0（未做任何写入）"

echo "== 3. 安装两份 APK（-r 覆盖安装，**不卸载**，保住应用数据） =="
"$ADB" -s "$SERIAL" install -r "$APP_APK"  | tail -1
"$ADB" -s "$SERIAL" install -r "$TEST_APK" | tail -1

echo "== 4. 手动跑 A/B（am instrument，绕开 AGP 的卸载） =="
# -w 等结果；-e 传参；最后是 <测试包>/<runner>
"$ADB" -s "$SERIAL" shell am instrument -w \
  -e class "$BENCH_CLASS" \
  -e androidx.benchmark.enabledRules=Macrobenchmark \
  "$TESTPKG/$RUNNER" 2>&1 | tee /tmp/startup-ab.log

echo
echo "== 5. 结果 =="
grep -E "timeToInitialDisplay|StartupBenchmarks_" /tmp/startup-ab.log || echo "  未匹配到结果行"
echo
echo "== 6. 自检：确认没被 assume 静默跳过 =="
if grep -q "assumption failed\|AssumptionViolatedException" /tmp/startup-ab.log; then
  echo "  ⚠ 有用例被 assume 跳过 —— 检查 enabledRules 是否为 Macrobenchmark"
else
  echo "  ✓ 未被跳过"
fi
