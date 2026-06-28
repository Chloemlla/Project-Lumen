package com.projectlumen.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.projectlumen.app.core.database.daos.AppSettingsDao
import com.projectlumen.app.core.database.daos.DailyEyeStatsDao
import com.projectlumen.app.core.database.daos.DailyPomodoroStatsDao
import com.projectlumen.app.core.database.daos.RuntimeStateDao
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity

@Database(
    entities = [
        AppSettingsEntity::class,
        RuntimeStateEntity::class,
        DailyEyeStatsEntity::class,
        DailyPomodoroStatsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun runtimeStateDao(): RuntimeStateDao
    abstract fun dailyEyeStatsDao(): DailyEyeStatsDao
    abstract fun dailyPomodoroStatsDao(): DailyPomodoroStatsDao

    companion object {
        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "project_lumen_mobile.db",
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
