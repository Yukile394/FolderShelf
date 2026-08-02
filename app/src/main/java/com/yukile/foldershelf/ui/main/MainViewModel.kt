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
        _isReady.value = permissionsOk && FloatingOverlayService.isRunning
    }
}
