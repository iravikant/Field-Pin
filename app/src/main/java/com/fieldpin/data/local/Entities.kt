package com.fieldpin.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sites", indices = [Index("status"), Index("name")])
data class SiteEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val status: String,
    val description: String,
    val boundaryJson: String,
    val areaSqm: Double,
    val perimeterM: Double,
    val centroidLat: Double,
    val centroidLng: Double,
    val lastInspectionAt: Long?,
    /** Server version this row is based on; 0 for a site created offline. */
    val version: Int,
    /** Comma separated names of locally edited fields; empty = in sync. */
    val dirtyFields: String,
    val editedAt: Long,
)

@Entity(tableName = "inspections", indices = [Index("siteId"), Index("synced")])
data class InspectionEntity(
    @PrimaryKey val id: String,
    val siteId: String,
    val siteName: String,
    val checkInAt: Long,
    val checkInLat: Double,
    val checkInLng: Double,
    val checkInAccuracyM: Float,
    val condition: String?,
    val notes: String,
    val photosJson: String,
    val flagNeedsRepair: Boolean,
    /** Null while the inspection is still a draft. */
    val submittedAt: Long?,
    val synced: Boolean,
)

data class SiteRow(
    @Embedded val site: SiteEntity,
    val pendingInspections: Int,
)
