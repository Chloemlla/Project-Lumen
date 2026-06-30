package com.projectlumen.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entitlements")
data class EntitlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val source: String,
    val productId: String,
    val purchaseToken: String = "",
    val tier: String,
    val status: String,
    val purchasedAt: Long,
    val expiresAt: Long = 0L,
    val lastVerifiedAt: Long = 0L,
    val rawPayloadJson: String = "",
)
