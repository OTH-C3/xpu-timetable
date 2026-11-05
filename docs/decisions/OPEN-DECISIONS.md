# OPEN-DECISIONS — 悬而未决登记册

> 规则：只追加 + 就地关闭（OPEN → RESOLVED，补 Resolution 字段）。每次 Phase 开始时，未决项自动复现到工作上下文最前面逐条判断。
> 最后更新：2026-09-16（Phase 1 收口）

| # | Date | Source | Open Item | Related Constraints | Current Leaning | Blocked By | Resolves When | Status |
|---|------|--------|-----------|---------------------|-----------------|------------|---------------|--------|
| 1 | 2026-09-16 | Phase 0 | 开源 License 选择 | 适配器代码防闭源 fork、传播便利性 | **已决策：GPL-3.0**（项目维护者于 2026-09-16 拍板；理由：保护教务适配器代码的社区贡献不被闭源白嫖） | — | — | RESOLVED (2026-09-16) |
| 2 | 2026-09-16 | Phase 0 | 「我的课表」接口路径与 JSON 结构 | WebView 主通道编码的前置条件 | 策略 A（拦截 XHR/JSON）为主、策略 B（JS 注入抓 DOM）降级 | 需一次真实登录抓包（项目维护者账号） | 开发期抓包完成后 | OPEN |
| 3 | 2026-09-16 | Phase 0 | minSdk 26 的精确设备覆盖率 | 版本范围锁定依据 | 维持 26（与开源竞品 Sleepy 一致；通知渠道/自适应图标齐备） | 官方分布数据复核 | Phase 1.5 数据复核后 | OPEN |
| 4 | 2026-09-16 | 架构文档 §4.3 | 4 项版本【核对】条目：KSP 构建号 / Navigation 3 最新 patch / Glance 1.2.0 / coroutines patch | libs.versions.toml 为唯一事实源 | 以 §4.1 锚定表所列版本为准 | 首次构建时按 Gradle 报错实测修正 | 首次 `gradle sync` 后回写 §4.1 | PARTIAL（部分关闭）：Glance 1.2.0 与 WorkManager 2.11.2 已于 2026-09-17 实测核对（架构 §4.1 回写记录）；KSP 已在 M1 落实 2.3.12；Navigation 3 暂不引入（见本表 #6） |
| 5 | 2026-09-16 | Phase 1 | 图标库路线 | P0 规则：全项目唯一一套 | **已裁决**：Lucide（ADR-002，官方证据：material-icons 弃用冻结 1.7.8 并从最新 M3 移除） | — | — | RESOLVED (2026-09-16, ADR-002) |
| 6 | 2026-09-17 | M2 导航 | 是否引入 Navigation 3（ADR-008 触发条件「屏幕数 ≥3」已满足） | ADR-008；现状为 `MainActivity` 内 sealed 状态导航 | 暂不引入：navigation3 1.2.0 仍为 RC，且状态导航已够用 | navigation3 转稳定版 或 状态导航难以维护 | 出现第 4+ 页面且状态导航明显吃力时 | OPEN |
| 7 | 2026-09-17 | M2-B 教务直连 | jwglxt.xpu.edu.cn 是否需要用户单独登录一次（本次抓包会话已建立，未见登录跳转） | AC-09 凭据零接触；WebView 起始页选择 | 起始页设为课表页 URL，由 WebView 自行跳转登录页并由用户完成 | 需要一次真机 WebView 验证 | 首次真机跑通教务直连后 | OPEN |

## 已关闭项升格记录

| 关闭项 | 升格产物 | 日期 |
|--------|----------|------|
| 图标库路线 | `docs/decisions/ADR-002.md` | 2026-09-16 |
| 版本基线（Kotlin/compileSdk） | `docs/decisions/ADR-009.md` | 2026-09-16 |
| MVP 范围与分发渠道（Phase 0） | 见 `docs/01-开发环境与教务对接分析.md` §8/§10 | 2026-09-16 |
