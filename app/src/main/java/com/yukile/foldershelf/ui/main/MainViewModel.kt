package com.yukile.foldershelf.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yukile.foldershelf.data.repository.ShelfRepository
import com.yukile.foldershelf.overlay.FloatingOverlayService
import com.yukile.foldershelf.util.PermissionUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * MainActivity ekranının durumunu tutar.
 *
 * isRunning → servis gerçekten çalışıyor MU?
 *   (izinler tamam VE FloatingOverlayService.isRunning == true)
 *
 * Eski isReady StateFlow'u isRunning olarak yeniden adlandırıldı çünkü
 * "izinler tamam ama servis çalışmıyor" durumu kullanıcı için anlamsız;
 * buton her zaman "Başlat" göstermeli, tıklanınca servis başlamalı.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShelfRepository.getInstance(application)

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    val itemCount: StateFlow<Int> = repository.items
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
        }
        refreshStatus()
    }

    /**
     * Servis durumunu anlık olarak okuyup UI'ı günceller.
     * MainActivity.onResume() ve startOverlayService() tarafından çağrılır.
     */
    fun refreshStatus() {
        val permissionsOk = PermissionUtils.allRequiredPermissionsGranted(getApplication())
        _isRunning.value = permissionsOk && FloatingOverlayService.isRunning
    }
}
