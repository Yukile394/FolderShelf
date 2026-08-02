package com.yukile.foldershelf.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yukile.foldershelf.data.model.ShelfItem
import com.yukile.foldershelf.data.repository.ShelfRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ShelfListViewModel
 *
 * "Yonetim" ekraninin durumunu tutar: raftaki ogelerin listesi ve hangi
 * ogelerin su anda boyut/dosya sayisi hesaplamasi (refresh) sirasinda
 * oldugu. Her hesaplama kendi iptal edilebilir Job'una sahiptir -
 * "buyuk klasorlerde islem iptal edilebilsin" gereksinimini karsilar.
 */
class ShelfListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShelfRepository.getInstance(application)

    val items: StateFlow<List<ShelfItem>> = repository.items

    private val _refreshingIds = MutableStateFlow<Set<Long>>(emptySet())
    val refreshingIds: StateFlow<Set<Long>> = _refreshingIds.asStateFlow()

    private val refreshJobs = mutableMapOf<Long, Job>()

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
        }
    }

    fun refreshStats(item: ShelfItem) {
        // Ayni oge icin zaten devam eden bir hesaplama varsa tekrar baslatma.
        if (refreshJobs.containsKey(item.id)) return

        val job = viewModelScope.launch {
            _refreshingIds.value = _refreshingIds.value + item.id
            try {
                repository.refreshStats(getApplication(), item)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _refreshingIds.value = _refreshingIds.value - item.id
                refreshJobs.remove(item.id)
            }
        }
        refreshJobs[item.id] = job
    }

    fun cancelRefresh(itemId: Long) {
        refreshJobs[itemId]?.cancel()
        refreshJobs.remove(itemId)
        _refreshingIds.value = _refreshingIds.value - itemId
    }

    fun rename(id: Long, newName: String) {
        viewModelScope.launch {
            repository.renameItem(id, newName)
        }
    }

    fun delete(id: Long) {
        cancelRefresh(id)
        viewModelScope.launch {
            repository.deleteItem(id)
        }
    }

    fun deleteMultiple(ids: Set<Long>) {
        ids.forEach { cancelRefresh(it) }
        viewModelScope.launch {
            repository.deleteItems(ids)
        }
    }

    /**
     * Baska bir uygulamadan (dosya yoneticisi vb.) surukle-birak ile
     * gelen bir URI'yi rafa ekler. Turu (dosya/klasor) kesin bilinmedigi
     * icin FOLDER tahmini gonderilir; repository gercek turu URI'nin
     * kendisinden (docFile.isDirectory) guvenli sekilde cozer.
     *
     * @param onResult Ekleme sonucunu (yeni eklendi mi, zaten var miydi)
     *   UI katmanina bildirmek icin cagirilir; boylece dogru Toast mesaji
     *   gosterilebilir.
     */
    fun addFromDrop(
        uri: android.net.Uri,
        onResult: (com.yukile.foldershelf.data.repository.AddItemResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val result = repository.addItemFromUri(
                    getApplication(),
                    uri,
                    com.yukile.foldershelf.data.model.ItemType.FOLDER
                )
                onResult(result)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJobs.values.forEach { it.cancel() }
        refreshJobs.clear()
    }
}
