package com.yukile.foldershelf.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import com.yukile.foldershelf.util.CrashHandler
import com.yukile.foldershelf.util.PermissionUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
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
        maybeShowCrashDialog()
    }

    private fun maybeShowCrashDialog() {
        val crashed = intent?.getBooleanExtra(CrashHandler.EXTRA_CRASHED, false) == true
        if (!crashed) return
        intent.removeExtra(CrashHandler.EXTRA_CRASHED)

        val log = CrashHandler.latestCrashLog(this) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_dialog_title)
            .setMessage(log.take(4000))
            .setPositiveButton(R.string.action_copy) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("crash_log", log))
                Toast.makeText(this, R.string.crash_copied_toast, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.action_share) { _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, log)
                }
                try {
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton(R.string.action_ok, null)
            .setCancelable(true)
            .show()
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
            binding.root.postDelayed({ viewModel.refreshStatus() }, 400L)
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
