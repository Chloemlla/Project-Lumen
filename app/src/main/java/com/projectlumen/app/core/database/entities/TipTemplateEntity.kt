package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.projectlumen.app.core.enums.TemplateBackgroundType

@Entity(tableName = "tip_templates")
data class TipTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val isBuiltin: Boolean = true,
    val backgroundType: String = TemplateBackgroundType.SOLID.name,
    val backgroundValue: String = "#D4F2F0",
    val primaryColor: String = "#246B73",
    val titleText: String = "Time to rest",
    val subtitleText: String = "Look away from the screen and relax your eyes.",
    val showSkipButton: Boolean = true,
    val layoutJson: String = "{}",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
