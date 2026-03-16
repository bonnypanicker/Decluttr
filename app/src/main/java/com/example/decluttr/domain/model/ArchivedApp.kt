package com.example.decluttr.domain.model

data class ArchivedApp(
    val packageId: String,
    val name: String,
    val isPlayStoreInstalled: Boolean = true,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val notes: String? = null,
    val iconBytes: ByteArray? = null,
    val archivedAt: Long = System.currentTimeMillis(),
    val lastTimeUsed: Long = 0L,
    val folderName: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArchivedApp

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
        if (folderName != other.folderName) return false

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
        result = 31 * result + (folderName?.hashCode() ?: 0)
        return result
    }
}
