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

@Database(
    entities = [
        AppSettingsEntity::class,
        RuntimeStateEntity::class,
        DailyEyeStatsEntity::class,
        DailyPomodoroStatsEntity::class,
        TipTemplateEntity::class,
    ],
    version = 2,
    exportSchema = false,
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

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "project_lumen_mobile.db",
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
