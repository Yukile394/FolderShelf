package com.yukile.foldershelf.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.yukile.foldershelf.R
import com.yukile.foldershelf.data.local.ShelfStorage
import com.yukile.foldershelf.data.model.ItemType
import com.yukile.foldershelf.data.model.ShelfItem
import com.yukile.foldershelf.util.DocumentTreeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * ShelfRepository
 *
 * Uygulamanin tek gercek veri kaynagi (single source of truth). MVVM
 * mimarisinde ViewModel katmani yalnizca bu sinifla konusur; veri nereden
 * geliyor (SharedPreferences+JSON) bilmez/bilmesi gerekmez.
 *
 * Process genelinde tek instance (singleton) olarak kullanilir ki hem
 * MainActivity/ShelfListActivity/SettingsActivity hem de
 * FloatingOverlayService ve PickerActivity ayni veriyi gorsun.
 */
/**
 * addItemFromUri() cagrisinin sonucu. Cagiran taraf (FloatingOverlayService,
 * ShelfListActivity, PickerActivity) buna gore farkli geri bildirim
 * gosterebilir (orn. "Eklendi" / "Zaten ekliydi, guncellendi").
 */
enum class AddItemResult {
    ADDED,
    ALREADY_EXISTS
}

class ShelfRepository private constructor(
    private val storage: ShelfStorage
) {

    private val _items = MutableStateFlow<List<ShelfItem>>(emptyList())
    val items: StateFlow<List<ShelfItem>> = _items.asStateFlow()

    private var initialized = false

    suspend fun ensureInitialized() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            _items.value = storage.readAll().sortedByDescending { it.addedAt }
            initialized = true
        }
    }

    suspend fun addItemFromUri(context: Context, uri: Uri, type: ItemType): AddItemResult {
        return withContext(Dispatchers.IO) {
            // ONEMLI: "type" caller tarafindan bir tahmindir (ozellikle
            // surukle-birak akisinda). Surukle-birakla gelen URI'ler cogu
            // zaman gercek bir "tree" URI degil, duz bir "document" URI'dir;
            // boyle bir URI'yi DocumentFile.fromTreeUri() ile acmaya
            // calismak IllegalArgumentException ("Uri lacks 'tree' segment")
            // firlatir ve tum uygulamayi cokertirdi. Bu yuzden burada once
            // guvenli sekilde deneriz, olmazsa tekil belge olarak dusup
            // gercek turu docFile.isDirectory'den ogreniriz.
            val (docFile, resolvedType) = resolveDocumentSafely(context, uri, type)

            val name = docFile?.name
                ?: uri.lastPathSegment
                ?: context.getString(R.string.unnamed_item)

            val uriString = uri.toString()
            val current = storage.readAll()

            // Ayni klasor/dosya (ayni URI) zaten raftaysa tekrar eklemek
            // yerine mevcut kaydi "en ustte" gorunecek sekilde tazeleyip
            // (addedAt guncellenir) cagirana ALREADY_EXISTS donuyoruz.
            val existing = current.firstOrNull { it.uri == uriString }
            if (existing != null) {
                val refreshed = existing.copy(
                    displayName = name,
                    addedAt = System.currentTimeMillis()
                )
                val updated = current.map { if (it.id == existing.id) refreshed else it }
                storage.writeAll(updated)
                _items.value = updated.sortedByDescending { it.addedAt }
                return@withContext AddItemResult.ALREADY_EXISTS
            }

            val newItem = ShelfItem(
                id = storage.nextId(),
                uri = uriString,
                displayName = name,
                type = resolvedType,
                addedAt = System.currentTimeMillis()
            )
            val updated = current + newItem
            storage.writeAll(updated)
            _items.value = updated.sortedByDescending { it.addedAt }
            AddItemResult.ADDED
        }
    }

    /**
     * type == FOLDER ise once gercekten bir tree URI olup olmadigini
     * guvenli sekilde dener; basarisiz olursa (surukle-birakta cok sik
     * rastlanir) tekil belge olarak acar ve isDirectory kontroluyle
     * gercek turu belirler. Hicbir asamada exception disariya sizmaz.
     */
    private fun resolveDocumentSafely(
        context: Context,
        uri: Uri,
        type: ItemType
    ): Pair<DocumentFile?, ItemType> {
        if (type == ItemType.FOLDER) {
            val treeDoc = try {
                DocumentFile.fromTreeUri(context, uri)
            } catch (e: Exception) {
                null
            }
            if (treeDoc != null && treeDoc.exists()) {
                return treeDoc to ItemType.FOLDER
            }
        }
        val singleDoc = try {
            DocumentFile.fromSingleUri(context, uri)
        } catch (e: Exception) {
            null
        }
        val actualType = if (singleDoc?.isDirectory == true) ItemType.FOLDER else ItemType.FILE
        return singleDoc to actualType
    }

    suspend fun renameItem(id: Long, newName: String) {
        withContext(Dispatchers.IO) {
            val trimmed = newName.trim()
            if (trimmed.isEmpty()) return@withContext
            val updated = storage.readAll().map { existing ->
                if (existing.id == id) existing.copy(displayName = trimmed) else existing
            }
            storage.writeAll(updated)
            _items.value = updated.sortedByDescending { it.addedAt }
        }
    }

    suspend fun deleteItem(id: Long) {
        withContext(Dispatchers.IO) {
            val updated = storage.readAll().filterNot { it.id == id }
            storage.writeAll(updated)
            _items.value = updated.sortedByDescending { it.addedAt }
        }
    }

    suspend fun deleteItems(ids: Set<Long>) {
        withContext(Dispatchers.IO) {
            val updated = storage.readAll().filterNot { ids.contains(it.id) }
            storage.writeAll(updated)
            _items.value = updated.sortedByDescending { it.addedAt }
        }
    }

    /**
     * Bir ogenin boyutunu, dosya sayisini ve son degistirilme tarihini
     * (yeniden) hesaplar. Buyuk klasorlerde zaman alabilecegi icin cagiran
     * ViewModel bunu iptal edilebilir bir coroutine Job'u icinde
     * calistirmalidir ("Islem iptal edilebilsin" gereksinimi).
     */
    suspend fun refreshStats(context: Context, item: ShelfItem): ShelfItem {
        return withContext(Dispatchers.IO) {
            val stats = DocumentTreeUtils.computeStats(context, Uri.parse(item.uri), item.type)
            val updatedItem = item.copy(
                cachedSizeBytes = stats.totalSizeBytes,
                cachedFileCount = stats.fileCount,
                cachedLastModified = stats.lastModified
            )
            val updated = storage.readAll().map { existing ->
                if (existing.id == item.id) updatedItem else existing
            }
            storage.writeAll(updated)
            _items.value = updated.sortedByDescending { it.addedAt }
            updatedItem
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ShelfRepository? = null

        fun getInstance(context: Context): ShelfRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShelfRepository(ShelfStorage(context.applicationContext)).also {
                    INSTANCE = it
                }
            }
        }
    }
}
