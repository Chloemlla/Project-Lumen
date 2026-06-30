package com.projectlumen.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.projectlumen.app.core.database.daos.AppSettingsDao
import com.projectlumen.app.core.database.daos.DailyEyeStatsDao
import com.projectlumen.app.core.database.daos.DailyGoalsDao
import com.projectlumen.app.core.database.daos.DailyPomodoroStatsDao
import com.projectlumen.app.core.database.daos.EntitlementsDao
import com.projectlumen.app.core.database.daos.FeatureFlagsDao
import com.projectlumen.app.core.database.daos.ReminderPlansDao
import com.projectlumen.app.core.database.daos.RuntimeStateDao
import com.projectlumen.app.core.database.daos.TipTemplatesDao
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.database.entities.EntitlementEntity
import com.projectlumen.app.core.database.entities.FeatureFlagEntity
import com.projectlumen.app.core.database.entities.ReminderPlanEntity
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
        DailyGoalEntity::class,
        FeatureFlagEntity::class,
        EntitlementEntity::class,
        ReminderPlanEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun runtimeStateDao(): RuntimeStateDao
    abstract fun dailyEyeStatsDao(): DailyEyeStatsDao
    abstract fun dailyPomodoroStatsDao(): DailyPomodoroStatsDao
    abstract fun tipTemplatesDao(): TipTemplatesDao
    abstract fun dailyGoalsDao(): DailyGoalsDao
    abstract fun featureFlagsDao(): FeatureFlagsDao
    abstract fun entitlementsDao(): EntitlementsDao
    abstract fun reminderPlansDao(): ReminderPlansDao

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

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateCommerceAndPersonalization(db)
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN proximityBaselineFaceWidthPercent INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN blinkMonitoringEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN blinkNoBlinkThresholdSeconds INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN blinkAlertCooldownSeconds INTEGER NOT NULL DEFAULT 60")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN ambientLightMonitoringEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN ambientLightLowLuxThreshold INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoBrightnessEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoBrightnessMinPercent INTEGER NOT NULL DEFAULT 35")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoBrightnessMaxPercent INTEGER NOT NULL DEFAULT 85")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN globalOverlayEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN overlayRestDurationSeconds INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN overlayStrictDistancePercent INTEGER NOT NULL DEFAULT 160")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN blinkLastBlinkAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN blinkLastWarningAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN blinkLastEyeOpenProbabilityPercent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN ambientLastLux REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN ambientTooDark INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE runtime_state ADD COLUMN ambientLastWarningAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_eye_stats ADD COLUMN eyeDryWarningCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE daily_eye_stats ADD COLUMN lowLightWarningCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateCommerceAndPersonalization(db)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateEyeProtection(db)
            }
        }

        private fun migrateEyeProtection(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "app_settings", "blinkMonitoringEnabled", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "app_settings", "blinkNoBlinkThresholdSeconds", "INTEGER NOT NULL DEFAULT 10")
            addColumnIfMissing(db, "app_settings", "blinkAlertCooldownSeconds", "INTEGER NOT NULL DEFAULT 60")
            addColumnIfMissing(db, "app_settings", "ambientLightMonitoringEnabled", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "app_settings", "ambientLightLowLuxThreshold", "INTEGER NOT NULL DEFAULT 10")
            addColumnIfMissing(db, "app_settings", "autoBrightnessEnabled", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "app_settings", "autoBrightnessMinPercent", "INTEGER NOT NULL DEFAULT 35")
            addColumnIfMissing(db, "app_settings", "autoBrightnessMaxPercent", "INTEGER NOT NULL DEFAULT 85")
            addColumnIfMissing(db, "app_settings", "globalOverlayEnabled", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "app_settings", "overlayRestDurationSeconds", "INTEGER NOT NULL DEFAULT 20")
            addColumnIfMissing(db, "app_settings", "overlayStrictDistancePercent", "INTEGER NOT NULL DEFAULT 160")
            addColumnIfMissing(db, "runtime_state", "blinkLastBlinkAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "runtime_state", "blinkLastWarningAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "runtime_state", "blinkLastEyeOpenProbabilityPercent", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "runtime_state", "ambientLastLux", "REAL NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "runtime_state", "ambientTooDark", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "runtime_state", "ambientLastWarningAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "daily_eye_stats", "eyeDryWarningCount", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "daily_eye_stats", "lowLightWarningCount", "INTEGER NOT NULL DEFAULT 0")
        }

        private fun migrateCommerceAndPersonalization(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "app_settings", "deviceInstallationId", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "app_settings", "planTier", "TEXT NOT NULL DEFAULT 'FREE'")
            addColumnIfMissing(db, "app_settings", "entitlementExpiresAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "app_settings", "lastEntitlementSyncAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "app_settings", "vibrationEnabled", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "app_settings", "restStartSoundEnabled", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(db, "app_settings", "restStartSoundPath", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "app_settings", "preAlertVolumePercent", "INTEGER NOT NULL DEFAULT 70")
            addColumnIfMissing(db, "app_settings", "restStartVolumePercent", "INTEGER NOT NULL DEFAULT 70")
            addColumnIfMissing(db, "app_settings", "restEndVolumePercent", "INTEGER NOT NULL DEFAULT 70")
            addColumnIfMissing(db, "app_settings", "pomodoroWorkStartVolumePercent", "INTEGER NOT NULL DEFAULT 70")
            addColumnIfMissing(db, "app_settings", "pomodoroWorkEndVolumePercent", "INTEGER NOT NULL DEFAULT 70")
            addColumnIfMissing(db, "app_settings", "quietHoursEnabled", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "app_settings", "quietStartMinute", "INTEGER NOT NULL DEFAULT 1320")
            addColumnIfMissing(db, "app_settings", "quietEndMinute", "INTEGER NOT NULL DEFAULT 420")
            addColumnIfMissing(db, "app_settings", "quietMode", "TEXT NOT NULL DEFAULT 'PAUSE_TIMER'")
            addColumnIfMissing(db, "tip_templates", "isPremium", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "tip_templates", "remoteId", "TEXT NOT NULL DEFAULT ''")
            addColumnIfMissing(db, "tip_templates", "deletedAt", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "daily_eye_stats", "maxContinuousWorkSeconds", "INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_goals (
                    id INTEGER NOT NULL PRIMARY KEY,
                    restBreakGoal INTEGER NOT NULL DEFAULT 8,
                    maxContinuousWorkMinutes INTEGER NOT NULL DEFAULT 45,
                    pomodoroGoal INTEGER NOT NULL DEFAULT 4,
                    weeklyActiveDaysGoal INTEGER NOT NULL DEFAULT 5,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS feature_flags (
                    `key` TEXT NOT NULL PRIMARY KEY,
                    enabled INTEGER NOT NULL,
                    payloadJson TEXT NOT NULL DEFAULT '',
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS entitlements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    source TEXT NOT NULL,
                    productId TEXT NOT NULL,
                    purchaseToken TEXT NOT NULL DEFAULT '',
                    tier TEXT NOT NULL,
                    status TEXT NOT NULL,
                    purchasedAt INTEGER NOT NULL,
                    expiresAt INTEGER NOT NULL DEFAULT 0,
                    lastVerifiedAt INTEGER NOT NULL DEFAULT 0,
                    rawPayloadJson TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS reminder_plans (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    warnIntervalMinutes INTEGER NOT NULL DEFAULT 20,
                    restDurationSeconds INTEGER NOT NULL DEFAULT 20,
                    quietHoursEnabled INTEGER NOT NULL DEFAULT 0,
                    quietStartMinute INTEGER NOT NULL DEFAULT 1320,
                    quietEndMinute INTEGER NOT NULL DEFAULT 420,
                    quietMode TEXT NOT NULL DEFAULT 'PAUSE_TIMER',
                    sortOrder INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    deletedAt INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            definition: String,
        ) {
            if (columnExists(db, tableName, columnName)) return
            db.execSQL("ALTER TABLE $tableName ADD COLUMN $columnName $definition")
        }

        private fun columnExists(db: SupportSQLiteDatabase, tableName: String, columnName: String): Boolean {
            db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == columnName) return true
                }
            }
            return false
        }

        fun create(context: Context): AppDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "project_lumen_mobile.db",
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                )
            if (BuildConfig.DEBUG) {
                builder.fallbackToDestructiveMigration(dropAllTables = true)
            }
            return builder.build()
        }
    }
}
