package com.yukile.foldershelf.data.model

/**
 * Rafa eklenen bir ogenin turu.
 * FOLDER: "Klasor Sec" veya surukle-birak ile eklenen bir klasor (SAF agac URI'si).
 * FILE:   "Dosya Sec" ile eklenen tekil bir dosya.
 */
enum class ItemType {
    FOLDER,
    FILE
}

/**
 * ShelfItem
 *
 * Kullanicinin floating menu uzerinden eklendigi her klasor/dosyayi
 * temsil eden degismez (immutable) veri modeli. "uri" alani, Storage
 * Access Framework tarafindan verilen kalici (persistable) content
 * URI'sini metin olarak tutar; gercek dosya sistemi yolu asla saklanmaz,
 * boylece klasor yapisi ve dosya isimleri sistem tarafindan
 * degistirilmeden orijinal haliyle referans alinir.
 */
data class ShelfItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val type: ItemType,
    val addedAt: Long,
    val cachedSizeBytes: Long = -1L,
    val cachedFileCount: Int = -1,
    val cachedLastModified: Long = -1L
) {
    val hasStats: Boolean
        get() = cachedSizeBytes >= 0L
}
