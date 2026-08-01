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
 * MainActivity ekraninin durumunu tutar: gerekli izinlerin verilip
 * verilmedigi ve raftaki oge sayisi gibi kucuk, goruntulenebilir bilgiler.
 * Izin isteme akisinin kendisi (sonuc callback'leri) Android API'lerine
 * (Activity) baglandigi icin bilerek View katmaninda (MainActivity)
 * birakildi; bu, MVVM icinde kabul goren bir ayrimdir.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShelfRepository.getInstance(application)

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    val itemCount: StateFlow<Int> = repository.items
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
        }
        refreshStatus()
    }

    fun refreshStatus() {
        val permissionsOk = PermissionUtils.allRequiredPermissionsGranted(getApplication())
        // Izinler yeterli DEGIL - buton kesinlikle "Baslat" gostermeli.
        // Izinler yeterli AMA servis fiilen calismiyorsa da (ör. servis
        // beklenmedik sekilde durduysa) "Baslat" gostermeliyiz; aksi
        // halde kullanici butona basmadan hicbir sey olmuyormus gibi
        // gorunur ("calisiyor" yazar ama + ekranda yok).
        _isReady.value = permissionsOk && FloatingOverlayService.isRunning
    }
}
