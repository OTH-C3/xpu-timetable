// 根构建文件：只声明插件，不产生业务逻辑（Spec §10 工程约束）
//
// buildscript 中提升 KGP 版本的原因（2026-09-16 实测，务必保留）：
//   AGP 9.4.0 内建 Kotlin 的 KGP 基线为 2.2.10（见其 POM 的 runtime 依赖），
//   但 org.jetbrains.kotlin:compose-group-mapping:2.2.10 从未发布到 Maven Central（HTTP 404），
//   导致 Release 构建的 :app:produceReleaseComposeMapping 任务无法解析依赖而失败。
//   显式把 KGP 提到项目 Kotlin 版本（2.3.21，其映射构件已发布）即可修复。
//   依据：AGP 9.0 发布说明「升级到更高版本的 KGP」章节 + 本机实测（404 vs 200）。
//   注意 版本号须与 gradle/libs.versions.toml 的 kotlin 保持一致（buildscript 块内无法读取版本目录，故此处字面量）。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    // M7：Baseline Profile 生成器模块用 com.android.test。它与 com.android.application
    // 是同一个 AGP 实现类，必须在根统一声明版本（否则子模块请求会被判为
    // "已在 classpath 上但版本未知"）。
    alias(libs.plugins.android.test) apply false
}
