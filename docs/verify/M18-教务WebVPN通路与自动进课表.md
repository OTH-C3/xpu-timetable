# M18 验证 — 教务 WebVPN 通路与自动进课表

> 版本 0.3.3（versionCode 6）｜2026-11-11
> 触发：真机反馈「登录后还是不行，跳到 webvpn 的账号登录页；退出这个页面再点教务导入才能识别」

---

## 一、真机证据

| 截图 | 诊断卡片显示 |
|------|--------------|
| 登录后 | `当前页面：sz.xpu.edu.cn/auth2/login`<br>`当前页面：webvpn.xpu.edu.cn/https/77726476706e69737468656265737421fae046.../student/login` |
| 点「从统一身份认证平台登录」后 | `.../auth2/login`<br>`.../student/home` |

那串 hex 的前 16 字节解出来是 `wrdvpnisthebest!`（深信服 WebVPN 定长标记），
后面是加密过的内网地址 —— 也就是说，**门户「我的课表」这个入口走的是 VPN 代理**，
不是 App 里写死的 `jwglxt.xpu.edu.cn` 直连地址。

联网只读探测（未携带任何凭据）进一步确认前缀有效：

| 请求 | 结果 |
|------|------|
| `webvpn.xpu.edu.cn/` | 302 → `/login` |
| `webvpn.xpu.edu.cn/https/<hex>/student/home` | 302 → `/login` |
| `webvpn.xpu.edu.cn/https/<hex>/student/for-std/course-table` | 302 → `/login` |
| `jwglxt.xpu.edu.cn/student/for-std/course-table` | 302 → `/student/login?refer=...` |

网关接受了这段前缀（不是 404），只是要求先登录；`jwglxt` 本身公网可达，
只是会踢回自己的登录页。

## 二、三个根因

| 现象 | 根因 |
|------|------|
| 登录成功却不自动进课表，要退出再进一次 | `isPortalAfterLogin` 只认 `sz.xpu.edu.cn` 主机。登录后落地在 `webvpn.xpu.edu.cn/.../student/home`，判定不到"登录完成"，自动跳转永不触发 |
| 校外打不开 | 自动跳转的地址写死成直连课表页，只在校园网可达；学校真实通路是 VPN 代理 |
| 诊断卡片显示一长串乱码 | `diagnosticPath` / `diagnosticPage` 直接打印整条 path，把那 56 位加密段也铺了出来 |

另有一个连带缺陷：VPN 根登录页就叫 `/login`，旧判定只认 `/login?`，
因此它既不算登录页、又会被当成"已登录的门户页"。

## 三、改法

| 位置 | 改动 |
|------|------|
| `parser/xpu/WebVpnUrl.kt`（新增） | 纯函数解析 VPN 地址：`webVpnPrefixOf` 取出 `https/<hex>`，`effectivePath` 得到真实路径，`isCourseTablePage` 认课表页，`courseTableUrl(prefix)` 按前缀拼地址。加密段**不硬编码、不解密**，只从实际页面抄 |
| `XpuRequestPolicy.kt` | `shouldInspect` 改用 `effectivePath`（关键字被加密段隔开，看整条 path 会漏）；`isLoginPage` 增加「路径以 `/login` 结尾」，覆盖 VPN 根登录页与 `/student/login`；`isPortalAfterLogin` → `isLoginDone`（认任意校内主机、排除登录页与课表页本身、排除 VPN 网关根页）；新增 `isInsideJwglxt` 覆盖 SSO 静默换票 |
| `XpuDiagnostics.kt`（新增） | 诊断文本从判定文件拆出，`[VPN]` 标记替代 56 位 hex；同时让 `XpuRequestPolicy.kt` 回到 300 行门禁内（311 → 207） |
| `CaptureWebViewClient.kt` | `onPageFinished` 用新判定并携带**算好的目标地址**；主页面加载失败回调 `onMainFrameFailed`；课表页加载成功回调 `onCourseTableShown`；代发 `Referer` 改为跟随请求自身（跨站 Referer 会被网关判非法） |
| `CourseCaptureViewModel.kt` | `autoOpenCourseTable: Boolean` → `autoOpenUrl: String`（要带 VPN 前缀）；新增 `fallbackUrl` 一次性兜底，只退一次防死循环 |
| `CaptureEntry.kt`（新增） | 记住上次走通的课表地址（仅 URL，无凭据），下次直接进 |
| `CourseCaptureScreen.kt` | 起始页优先级：记住的入口 → 有会话时的直连课表页 → 门户首页 |

## 四、验证结果

| 项 | 命令/操作 | 结果 |
|----|-----------|------|
| 单测 | `gradle :app:testDebugUnitTest` | **365 用例 / 41 XML / 0 失败**（`> Task :app:testDebugUnitTest` 无 UP-TO-DATE） |
| 用例增量 | 360 → 365 | +5：VPN 登录完成判定、前缀拼接、伪装 hex 拒绝、VPN 下数据请求与诊断、SSO 静默换票 |
| release 构建 | `gradle :app:assembleRelease` | BUILD SUCCESSFUL，`versionCode=6` / `0.3.3` |
| 打包内容反查 | 解包 dex 搜字符串 | `capture_entry`、`[VPN] `、「连不上学校服务器」均在 `classes2.dex` —— 确认是新逻辑 |
| 模拟器安装 | `adb install -r` | Success；`dumpsys package` 报 versionName=0.3.3 |
| 模拟器冒烟 | 课表 → 导入课表 → 教务直连 | 门户登录页正常渲染；诊断卡片两行页面路径正常 |
| 崩溃检查 | `logcat` 搜 `FATAL` / `AndroidRuntime` | 无应用崩溃，进程存活 |
| 行数门禁 | — | `WebVpnUrl.kt` 82、`XpuDiagnostics.kt` 115、`XpuRequestPolicy.kt` 207、`CaptureWebViewClient.kt` 165、`CourseCaptureScreen.kt` 244、`CaptureEntry.kt` 36；两份测试文件按同一口径拆成 263 / 66 行。全项目无 >300 行文件 |

## 五、仍需要真机确认的一步

模拟器过不了滑块验证码，所以「登录后自动跳 + 从 VPN 抓到课表」这两条只能在真机验：

1. 装 0.3.3，进「课表 → 导入课表 → 教务直连」，登录一次
2. 正常情况：登录后自动跳到课表页并弹出预览确认，**不需要退出重进**
3. 若停在某页没动，把诊断卡片整块截图发来 —— 现在卡片会标 `[VPN]`，不再有那串 hex

## 六、隐私说明

本次改动全程不接触凭据：VPN 前缀是 URL 的一部分；记住的入口只存 URL；
诊断卡片只输出路径与字段名。Cookie 仍只由系统 WebView 持有，App 不读取内容、不上传。
