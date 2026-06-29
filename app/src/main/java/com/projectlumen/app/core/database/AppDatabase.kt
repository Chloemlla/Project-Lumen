package com.projectlumen.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.projectlumen.app.core.database.daos.AppSettingsDao
import com.projectlumen.app.core.database.daos.DailyEyeStatsDao
import com.projectlumen.app.core.database.daos.DailyPomodoroStatsDao
import com.projectlumen.app.core.database.daos.RuntimeStateDao
import com.projectlumen.app.core.database.daos.TipTemplatesDao
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.BuildConfig

@Database(
    entities = [
        AppSettingsEntity::class,
        RuntimeStateEntity::class,
        DailyEyeStatsEntity::class,
        DailyPomodoroStatsEntity::class,
        TipTemplateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun runtimeStateDao(): RuntimeStateDao
    abstract fun dailyEyeStatsDao(): DailyEyeStatsDao
    abstract fun dailyPomodoroStatsDao(): DailyPomodoroStatsDao
    abstract fun tipTemplatesDao(): TipTemplatesDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoUpdateCheckEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN restSoundPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN preAlertIconPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN preAlertSoundEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN pomodoroWorkStartSoundEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN pomodoroWorkEndSoundEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN pomodoroWorkStartSoundPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN pomodoroWorkEndSoundPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN statsWorkImagePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN statsRestImagePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN statsSkipImagePath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tip_templates ADD COLUMN imagePath TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN keepAliveEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN proximityMonitoringEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN proximityBaselineEyeDistancePx REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN proximityDistanceMultiplierPercent INTEGER NOT NULL DEFAULT 130")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN proximityCheckIntervalMinutes INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN proximityCaptureSeconds INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN proximityFaceThresholdPercent INTEGER NOT NULL DEFAULT 38")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN proximityAlertCooldownSeconds INTEGER NOT NULL DEFAULT 120")
                db.execSQL("ALTER TABLE daily_eye_stats ADD COLUMN proximityWarningCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_eye_stats ADD COLUMN proximityCloseSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN lastStatsTickAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN proximityMonitoringActive INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN proximityTooClose INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN proximityLastFaceAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN proximityCloseStartedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN proximityCloseTickAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN proximityLastWarningAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN proximityLastRatioPercent INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "project_lumen_mobile.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            if (BuildConfig.DEBUG) {
                builder.fallbackToDestructiveMigration(dropAllTables = true)
            }
            return builder.build()
        }
    }
}
