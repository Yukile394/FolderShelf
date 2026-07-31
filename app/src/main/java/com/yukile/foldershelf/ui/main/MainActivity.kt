package com.yukile.foldershelf.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yukile.foldershelf.R
import com.yukile.foldershelf.databinding.ActivityMainBinding
import com.yukile.foldershelf.overlay.FloatingOverlayService
import com.yukile.foldershelf.ui.list.ShelfListActivity
import com.yukile.foldershelf.ui.settings.SettingsActivity
import com.yukile.foldershelf.util.PermissionUtils
import kotlinx.coroutines.launch

/**
 * MainActivity
 *
 * Uygulamanin ana ekrani. Tek is: kullaniciyi "Baslat" butonuyla
 * karsilamak, gerekli izinleri (once diger uygulamalarin uzerinde
 * gosterme, ardindan -Android 13+ icin- bildirim izni) sirayla ve
 * anlasilir aciklamalarla istemek, ve her ikisi de verildiginde
 * FloatingOverlayService'i baslatmak.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Kullanici ayarlar ekranindan donduginde tekrar kontrol et.
        proceedAfterOverlayCheck()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startOverlayService()
        } else {
            showInfoDialog(
                getString(R.string.permission_notification_title),
                getString(R.string.permission_notification_denied_message)
            )
        }
        viewModel.refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()

        binding.buttonStart.setOnClickListener { beginPermissionFlow() }
        binding.buttonManage.setOnClickListener {
            startActivity(Intent(this, ShelfListActivity::class.java))
        }
        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.isReady.collect { ready ->
                        binding.textStatus.text = if (ready) {
                            getString(R.string.status_ready)
                        } else {
                            getString(R.string.status_permissions_needed)
                        }
                        binding.buttonStart.text = if (ready) {
                            getString(R.string.action_running)
                        } else {
                            getString(R.string.action_start)
                        }
                    }
                }
                launch {
                    viewModel.itemCount.collect { count ->
                        binding.textItemCount.text = resources.getQuantityString(
                            R.plurals.shelf_item_count, count, count
                        )
                    }
                }
            }
        }
    }

    // region Izin akisi

    private fun beginPermissionFlow() {
        if (!PermissionUtils.canDrawOverlays(this)) {
            showOverlayRationale()
            return
        }
        proceedAfterOverlayCheck()
    }

    private fun showOverlayRationale() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_overlay_title)
            .setMessage(R.string.permission_overlay_message)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                try {
                    overlayPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                    showInfoDialog(
                        getString(R.string.error_generic_title),
                        getString(R.string.error_overlay_settings_unavailable)
                    )
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun proceedAfterOverlayCheck() {
        if (!PermissionUtils.canDrawOverlays(this)) {
            // Kullanici izni vermedi; zorlamiyoruz, sadece bilgilendiriyoruz.
            showInfoDialog(
                getString(R.string.permission_overlay_title),
                getString(R.string.permission_overlay_denied_message)
            )
            viewModel.refreshStatus()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtils.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startOverlayService()
    }

    private fun startOverlayService() {
        try {
            val intent = Intent(this, FloatingOverlayService::class.java)
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            e.printStackTrace()
            showInfoDialog(
                getString(R.string.error_generic_title),
                getString(R.string.error_service_start_failed)
            )
        } finally {
            viewModel.refreshStatus()
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    // endregion
}
