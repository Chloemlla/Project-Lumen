package com.projectlumen.app

import android.app.Application
import com.projectlumen.app.core.database.AppDatabase

class ProjectLumenApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
}
