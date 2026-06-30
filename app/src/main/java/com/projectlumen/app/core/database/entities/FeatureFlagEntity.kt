package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feature_flags")
data class FeatureFlagEntity(
    @PrimaryKey val key: String,
    val enabled: Boolean,
    val payloadJson: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
