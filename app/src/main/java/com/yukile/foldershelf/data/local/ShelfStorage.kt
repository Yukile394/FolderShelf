package com.yukile.foldershelf.data.local

import android.content.Context
import com.yukile.foldershelf.data.model.ItemType
import com.yukile.foldershelf.data.model.ShelfItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * ShelfStorage
 *
 * Raftaki klasor/dosya kayitlarini basit bir JSON dizisi olarak
 * SharedPreferences icinde saklar.
 *
 * NOT (bilincli mimari tercih): Bu proje icin Room + KSP yerine bu hafif
 * JSON tabanli depolama tercih edildi. Veri modeli kucuk ve basit oldugu
 * icin tam bir SQL veritabani gerekmiyor; ayrica AGP 9'un dahili Kotlin
 * destegiyle KSP/Kotlin surum eslesmesi su anda hizla degistiginden
 * (bu proje yalnizca GitHub Actions CI'da derlenip yerel olarak test
 * edilemeyecegi icin), ekstra bir derleyici eklentisi eklemek gereksiz
 * risk tasir. Repository/ViewModel katmanlari degismedigi icin MVVM
 * mimarisi tam olarak korunur; yalnizca Repository'nin ic implementasyonu
 * sadelestirilmistir.
 */
class ShelfStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun readAll(): List<ShelfItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i -> fromJson(array.getJSONObject(i)) }
        } catch (e: Exception) {
            // Bozuk/eksik veri durumunda uygulamayi cokertmek yerine
            // guvenli sekilde bos liste dondur.
            e.printStackTrace()
            emptyList()
        }
    }

    @Synchronized
    fun writeAll(items: List<ShelfItem>) {
        val array = JSONArray()
        items.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    @Synchronized
    fun nextId(): Long {
        val next = prefs.getLong(KEY_NEXT_ID, 1L)
        prefs.edit().putLong(KEY_NEXT_ID, next + 1).apply()
        return next
    }

    private fun toJson(item: ShelfItem): JSONObject = JSONObject().apply {
        put("id", item.id)
        put("uri", item.uri)
        put("displayName", item.displayName)
        put("type", item.type.name)
        put("addedAt", item.addedAt)
        put("cachedSizeBytes", item.cachedSizeBytes)
        put("cachedFileCount", item.cachedFileCount)
        put("cachedLastModified", item.cachedLastModified)
    }

    private fun fromJson(obj: JSONObject): ShelfItem = ShelfItem(
        id = obj.getLong("id"),
        uri = obj.getString("uri"),
        displayName = obj.getString("displayName"),
        type = ItemType.valueOf(obj.getString("type")),
        addedAt = obj.getLong("addedAt"),
        cachedSizeBytes = obj.optLong("cachedSizeBytes", -1L),
        cachedFileCount = obj.optInt("cachedFileCount", -1),
        cachedLastModified = obj.optLong("cachedLastModified", -1L)
    )

    companion object {
        private const val PREFS_NAME = "folder_shelf_items"
        private const val KEY_ITEMS = "items_json"
        private const val KEY_NEXT_ID = "next_id"
    }
}
