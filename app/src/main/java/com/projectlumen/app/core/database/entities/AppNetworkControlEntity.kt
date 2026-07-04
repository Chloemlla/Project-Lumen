package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_network_controls")
data class AppNetworkControlEntity(
    @PrimaryKey val packageName: String,
    val uid: Int,
    val appType: String,
    val networkRestricted: Boolean,
    val uidPolicyApplied: Boolean,
    val delegatedGuardAttempted: Boolean,
    val delegatedGuardApplied: Boolean,
    val lastCommandOutput: String = "",
    val lastError: String = "",
    val restrictedAt: Long = 0L,
    val restoredAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
)
