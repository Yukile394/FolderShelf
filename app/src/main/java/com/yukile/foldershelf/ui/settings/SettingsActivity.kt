package com.yukile.foldershelf.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.yukile.foldershelf.BuildConfig
import com.yukile.foldershelf.R
import com.yukile.foldershelf.data.repository.ShelfRepository
import com.yukile.foldershelf.databinding.ActivitySettingsBinding
import com.yukile.foldershelf.overlay.FloatingOverlayService
import com.yukile.foldershelf.util.Constants
import com.yukile.foldershelf.util.PreferenceHelper
import kotlinx.coroutines.launch

/**
 * SettingsActivity
 *
 * Balon boyutu, tema (acik/koyu/sistem), gizli/gorunur durumu ve tum
 * verileri temizleme secenegini barindiran ayarlar ekrani.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferenceHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceHelper(this)

        applyEdgeToEdgeInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupBubbleSizeSlider()
        setupThemeSelector()
        setupHideToggle()
        setupClearData()

        binding.textVersion.text = getString(
            R.string.settings_version_format,
            BuildConfig.VERSION_NAME
        )
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupBubbleSizeSlider() {
        binding.sliderBubbleSize.valueFrom = Constants.MIN_BUBBLE_SIZE_DP.toFloat()
        binding.sliderBubbleSize.valueTo = Constants.MAX_BUBBLE_SIZE_DP.toFloat()
        binding.sliderBubbleSize.value = prefs.bubbleSizeDp.toFloat().coerceIn(
            Constants.MIN_BUBBLE_SIZE_DP.toFloat(), Constants.MAX_BUBBLE_SIZE_DP.toFloat()
        )
        binding.sliderBubbleSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val sizeDp = value.toInt()
                prefs.bubbleSizeDp = sizeDp
                // Servis calisiyorsa boyutu anlik olarak da uygulamasi icin
                // bir Intent ile bilgilendirme yerine, servis her
                // baslatildiginda PreferenceHelper'dan okudugundan yeni
                // boyut bir sonraki gosterimde otomatik uygulanir. Servis
                // su an calisiyorsa da bildirim ikonuna dokunmadan da
                // gorsel olarak degisebilmesi icin servisi yeniden
                // tetikliyoruz.
                if (com.yukile.foldershelf.util.PermissionUtils.canDrawOverlays(this)) {
                    val intent = Intent(this, FloatingOverlayService::class.java).apply {
                        action = Constants.ACTION_SHOW_BUBBLE
                    }
                    startService(intent)
                }
            }
        }
    }

    private fun setupThemeSelector() {
        val group = binding.radioGroupTheme
        when (prefs.themeMode) {
            PreferenceHelper.THEME_LIGHT -> group.check(binding.radioThemeLight.id)
            PreferenceHelper.THEME_DARK -> group.check(binding.radioThemeDark.id)
            else -> group.check(binding.radioThemeSystem.id)
        }

        group.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.radioThemeLight.id -> PreferenceHelper.THEME_LIGHT
                binding.radioThemeDark.id -> PreferenceHelper.THEME_DARK
                else -> PreferenceHelper.THEME_SYSTEM
            }
            prefs.themeMode = mode
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    PreferenceHelper.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    PreferenceHelper.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }

    private fun setupHideToggle() {
        binding.switchHideBubble.isChecked = prefs.isBubbleHidden
        binding.switchHideBubble.setOnCheckedChangeListener { _, isChecked ->
            val action = if (isChecked) Constants.ACTION_HIDE_BUBBLE else Constants.ACTION_SHOW_BUBBLE
            val intent = Intent(this, FloatingOverlayService::class.java).apply {
                this.action = action
            }
            startService(intent)
        }
    }

    private fun setupClearData() {
        binding.buttonClearData.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_clear_data_title)
                .setMessage(R.string.settings_clear_data_message)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    lifecycleScope.launch {
                        val repository = ShelfRepository.getInstance(applicationContext)
                        repository.ensureInitialized()
                        val ids = repository.items.value.map { it.id }.toSet()
                        repository.deleteItems(ids)
                    }
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }
}
