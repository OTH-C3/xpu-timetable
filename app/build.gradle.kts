// app 模块构建文件 —— 首次构建的核心验证点在这份文件里
// 依据：Spec §4（版本锚定）、§10（工程约束）、ADR-001（Compose）、ADR-009（版本基线）

plugins {
    alias(libs.plugins.android.application)
    // Kotlin 2.0 起启用 compose 必须应用 Compose 编译器插件（2026-09-16 首次构建实测确认）
    alias(libs.plugins.kotlin.compose)
    // M2-B P0-B：教务 JSON 解析（版本目录已声明，本次接线；AGP 9 内建 Kotlin，勿 apply kotlin.android）
    alias(libs.plugins.kotlin.serialization)
    // Room 3 只支持 KSP（KAPT/JavaAP 已在 3.0 移除）
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
    // M7：Baseline Profile 消费侧 —— 插件负责把 :baselineprofile 采集到的 profile
    // 合并进 release 变体（并生成 R8 keep 规则，避免被裁掉热点方法）。
    // 官方要求 app 与生成器模块都应用它，故此处也声明。
    alias(libs.plugins.androidx.baselineprofile)
    // 注意（AGP 9 关键行为，Spec §4）：AGP 9 内建 Kotlin 支持，不得再 apply org.jetbrains.kotlin.android
}

// Baseline Profile 消费侧配置。
// automaticGenerationDuringBuild 显式关掉：否则每次 assemble 都会尝试连真机跑采集，
// 构建将强依赖一台已连接设备（CI/无设备环境直接失败）。profile 只由显式任务生成。
baselineProfile {
    automaticGenerationDuringBuild = false
}

// Room 3 的 schema 导出目录（架构 §7.4：Schema JSON 必须入库）
room3 {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.gould.xputimetable"   // 默认假设：以发起人常用昵称命名；首次发布前可改（改名需全局重构）
    compileSdk = 37                        // ADR-009：Compose 1.12 / BOM 2026.08 强制

    defaultConfig {
        applicationId = "com.gould.xputimetable"
        minSdk = 26                        // ADR-009：Android 8.0
        targetSdk = 36                     // ADR-009：Android 16
        versionCode = 2
        versionName = "0.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true                     // 启用 Compose；编译器插件接入方式在首次构建时验证
    }

    buildTypes {
        release {
            isMinifyEnabled = true         // Spec §10 约束 14：R8 必开
            isShrinkResources = true       // 体积目标 ≤15MB 依赖此项（架构 §4.5）
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // M7：release 暂用 debug 密钥签名。
            // 两个原因：① baselineProfile 插件会派生 nonMinifiedRelease 变体并安装到真机采集，
            // 未签名的 APK 装不上；② 与本机既有安装同签名 → 覆盖安装可保留数据。
            // ⚠️ 发布到 GitHub Releases 前必须换成正式 keystore（换签名会导致无法覆盖安装、需卸载重装）。
            // 用 getByName("debug") 而不是硬编码路径：AGP 会在缺失时自动生成 ~/.android/debug.keystore，
            // 换机器也能构建。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose（版本由 BOM 统一管理，故不写版本号）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // 基础组件
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // 图标（ADR-002：全项目唯一图标库）
    implementation(libs.lucide.icons)

    // 本地存储（ADR-003）：Room 3 + 必需 SQLite 驱动
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)

    // 序列化（M2-B P0-B）：教务直连 JSON 解析；学校接口随时加字段，解析端 ignoreUnknownKeys 容忍
    implementation(libs.kotlinx.serialization.json)

    // 二维码（M6 需求 6-B）：生成/识别纯逻辑；零新权限（导出展示+保存，导入走 SAF 选图）
    implementation(libs.zxing.core)

    // 偏好存储（M2-D）：提醒开关与提前分钟；版本目录既定选型（ADR-003 随 Room 里程碑接入），本次接线
    implementation(libs.androidx.datastore.preferences)

    // Baseline Profile（M7）：运行时安装器负责在"系统不支持云 profile"的场景下
    // 把 APK 内的 baseline.prof 落地给 ART（API 31 以下 / 无 GMS 设备）。
    // 它虽已由 Compose 传递引入（1.4.0），但官方明确要求显式声明 —— 本功能依赖它，
    // 不能依赖别家库的传递关系（那随时可能变）。
    implementation(libs.androidx.profileinstaller)

    // 桌面小组件（M3）：Glance 渲染 + WorkManager 跨天刷新（WorkManager 非 Glance 传递依赖，须显式声明）
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)

    // 调试期工具
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Baseline Profile（M7）：profile 的来源模块（com.android.test，只含采集器）。
    // 官方接线方式；插件据此把生成结果合并进 release 变体。
    baselineProfile(project(":baselineprofile"))

    // 单元测试
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
