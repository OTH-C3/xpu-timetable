/*
 * AppContainer.kt —— 手动依赖注入容器（装配数据库与仓库）
 *
 * 作用：按架构 ADR-004，本项目不引入 Hilt / Koin，而是用一个简单的容器集中持有
 * 数据库、DAO 与仓库实例。它在 Application / Activity 创建一次，向下层（ViewModel）提供数据访问能力。
 * 这样数据的创建方式只在一处，方便测试时替换为内存实现。
 *
 * Room 3 装配要点（与 Room 2 教程不同，已实测核对）：
 *   1. 用 androidx.room3.Room.databaseBuilder 创建（包名 androidx.room3）；
 *   2. Room 3 已移除 SupportSQLite，**必须显式 setDriver**，这里用 sqlite-bundled 提供的
 *      BundledSQLiteDriver()（类名 androidx.sqlite.driver.bundled.BundledSQLiteDriver）；
 *   3. MVP 期（1.x）允许破坏性迁移（数据可由重新导入恢复，见 Spec §6），故用
 *      fallbackToDestructiveMigration() 兜住"本地库结构变化"的场景，避免开发期崩溃。
 *
 * 关于 builder 真实签名（实测核对）：Room 3 的静态入口是
 *   databaseBuilder(context, klass: Class<T>, name) —— 没有 "reified<T>(context, name)" 这种
 *   Kotlin 扩展，故这里用显式的 AppDatabase::class.java 写法。
 */
package com.gould.xputimetable.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.gould.xputimetable.data.db.AppDatabase
import com.gould.xputimetable.data.prefs.BackgroundPrefs
import com.gould.xputimetable.data.prefs.UiPrefs
import com.gould.xputimetable.data.db.MIGRATION_1_2
import com.gould.xputimetable.data.db.MIGRATION_2_3
import com.gould.xputimetable.data.repository.RoomTransactionRunner
import com.gould.xputimetable.data.repository.TimetableRepositoryImpl
import com.gould.xputimetable.data.repository.TodoRepositoryImpl
import com.gould.xputimetable.domain.repository.TimetableRepository
import com.gould.xputimetable.domain.repository.TodoRepository
import com.gould.xputimetable.importer.file.JsonFileImporter
import com.gould.xputimetable.importer.xpu.XpuWebImporter
import com.gould.xputimetable.widget.DailyRefreshScheduler
import com.gould.xputimetable.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** 应用级协程作用域（小组件刷新等与界面生命周期无关的挂起任务；随进程存活）。 */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 应用数据库实例（惰性创建，首次访问 DAO 时才真正建库）。 */
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "xpu_timetable.db",
        )
            .setDriver(BundledSQLiteDriver())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)   // 显式迁移优先于破坏性迁移，保留用户数据
            .fallbackToDestructiveMigration()
            .build()
    }

    private val courseDao get() = database.courseDao()

    private val courseSessionDao get() = database.courseSessionDao()

    private val termDao get() = database.termDao()

    private val timeSlotDao get() = database.timeSlotDao()

    private val importLogDao get() = database.importLogDao()

    /** 组合根：ViewModel 只依赖 TimetableRepository 接口，不认识 Room。 */
    val repository: TimetableRepository by lazy {
        TimetableRepositoryImpl(
            courseDao = courseDao,
            courseSessionDao = courseSessionDao,
            termDao = termDao,
            timeSlotDao = timeSlotDao,
            importLogDao = importLogDao,
            tx = RoomTransactionRunner(database),
        )
    }

    /** 界面偏好（M11：「显示老师姓名」等与课程数据无关的展示开关）。 */
    val uiPrefs: UiPrefs by lazy { UiPrefs.create(appContext) }

    /** 背景偏好（M12 需求二：图片 URI / 不透明度 / 作用范围）。 */
    val backgroundPrefs: BackgroundPrefs by lazy { BackgroundPrefs.create(appContext) }

    /** 待办仓库（M12 需求五）。与课表仓库并列，ViewModel 只认接口。 */
    val todoRepository: TodoRepository by lazy { TodoRepositoryImpl(database.todoDao()) }

    /** 教务直连通道（M2-B）：semesterId 由拦截到的 URL 决定，importer 本身无状态。 */
    val xpuWebImporter: XpuWebImporter by lazy {
        XpuWebImporter(repository)
    }

    /** JSON 文件/二维码导入通道（M6）：与 CSV 各自独立，避免共用 importer 串味。 */
    val jsonFileImporter: JsonFileImporter by lazy {
        JsonFileImporter(repository)
    }

    /**
     * 数据变更组合回调（M3 §4.5，AC-18）：导入 commit / 课程保存 / 删除 / 按来源清理 /
     * 学期保存等"数据类"触发点统一调这里 —— 重排小组件的跨天闹钟 + 立即刷新桌面小组件。
     * 两段各自 runCatching：小组件刷新失败绝不能让导入/保存失败，反之亦然。
     *
     * M7：课前提醒功能已整体移除，原先的 reminderManager.reschedule() 调用随之删除。
     */
    /**
     * 精确闹钟授权检查（M7）：课前提醒移除后，SCHEDULE_EXACT_ALARM 只服务桌面小组件的
     * 跨天刷新(4003)与课程结束刷新(4002)；未授权时它们降级为不精确（最长延后约 1 小时）。
     * 设置页据此显示引导。
     */
    fun canScheduleExactAlarms(): Boolean = DailyRefreshScheduler.canScheduleExact(appContext)

    val onDataChanged: suspend () -> Unit = {
        // 数据变更后重排跨天刷新闹钟（幂等）
        runCatching { DailyRefreshScheduler.schedule(appContext) }
        runCatching { WidgetRefresher.refreshAll(appContext) }
    }
}
