package com.example.decluttr.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "archived_apps")
data class AppEntity(
    @PrimaryKey
    val packageId: String,
    val name: String,
    val category: String?,
    val tags: String, // Comma separated tags
    val notes: String?,
    val iconBytes: ByteArray?,
    val archivedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppEntity

        if (packageId != other.packageId) return false
        if (name != other.name) return false
        if (category != other.category) return false
        if (tags != other.tags) return false
        if (notes != other.notes) return false
        if (iconBytes != null) {
            if (other.iconBytes == null) return false
            if (!iconBytes.contentEquals(other.iconBytes)) return false
        } else if (other.iconBytes != null) return false
        if (archivedAt != other.archivedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = packageId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (category?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + (notes?.hashCode() ?: 0)
        result = 31 * result + (iconBytes?.contentHashCode() ?: 0)
        result = 31 * result + archivedAt.hashCode()
        return result
    }
}
