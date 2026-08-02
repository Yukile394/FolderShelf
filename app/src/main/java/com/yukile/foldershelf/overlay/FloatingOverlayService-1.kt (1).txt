package com.yukile.foldershelf.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.core.app.NotificationCompat
import com.yukile.foldershelf.R
import com.yukile.foldershelf.data.model.ItemType
import com.yukile.foldershelf.data.repository.ShelfRepository
import com.yukile.foldershelf.databinding.OverlayBubbleBinding
import com.yukile.foldershelf.databinding.OverlayMenuBinding
import com.yukile.foldershelf.ui.list.ShelfListActivity
import com.yukile.foldershelf.ui.picker.PickerActivity
import com.yukile.foldershelf.ui.settings.SettingsActivity
import com.yukile.foldershelf.util.Constants
import com.yukile.foldershelf.util.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PreferenceHelper
    private lateinit var repository: ShelfRepository

    private var bubbleBinding: OverlayBubbleBinding? = null
    private var menuBinding: OverlayMenuBinding? = null
    private var isBubbleAdded = false
    private var isMenuOpen = false

    // startForeground daha önce başarıyla çağrıldı mı?
    private var isForegroundStarted = false

    private lateinit var bubbleParams: WindowManager.LayoutParams

    // Servisin kendi context'inde uygulama teması (Theme.FolderShelf)
    // uygulanmamış olabilir; bu yüzden MaterialCardView gibi Material
    // Components görünümleri inflate edilirken "Error inflating class"
    // hatası oluşuyordu. Bu themed context, doğru temayı taşıyarak
    // inflate işlemini güvenli hale getirir.
    private val themedInflaterContext: Context by lazy {
        ContextThemeWrapper(this, R.style.Theme_FolderShelf)
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var downX = 0
    private var downY = 0
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var isDragging = false

    // -------------------------------------------------------------------------
    // Yaşam döngüsü
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = PreferenceHelper(applicationContext)
        repository = ShelfRepository.getInstance(applicationContext)
        createNotificationChannel()

        // isRunning'i onCreate'de set et; startForeground çağrısından ÖNCE
        // bile MainActivity'nin refreshStatus() çağıracağını biliyoruz.
        // Bu erken set sayesinde hızlı art arda tıklamalar ikinci bir
        // startForegroundService çağrısına yol açmaz.
        isRunning = true
        Log.d(TAG, "onCreate: servis oluşturuldu")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            // startForeground yalnızca bir kez çağrılmalı; sonraki
            // onStartCommand çağrılarında (aksiyon intent'leri) tekrar
            // çağırmak bazı cihazlarda "Already called startForeground"
            // hatası verir.
            if (!isForegroundStarted) {
                startForegroundSafely()
                isForegroundStarted = true
            }

            if (!Settings.canDrawOverlays(this)) {
                Log.w(TAG, "Overlay izni yok, servis durduruluyor")
                recordServiceError("overlayPermission", IllegalStateException("Settings.canDrawOverlays() false döndü"))
                isRunning = false
                stopSelf()
                return START_NOT_STICKY
            }

            when (intent?.action) {
                Constants.ACTION_HIDE_BUBBLE -> {
                    hideBubble()
                    return START_STICKY
                }
                Constants.ACTION_SHOW_BUBBLE -> {
                    showBubble()
                    return START_STICKY
                }
                Constants.ACTION_UPDATE_BUBBLE_SIZE -> {
                    applyBubbleSize(prefs.bubbleSizeDp)
                    return START_STICKY
                }
                Constants.ACTION_STOP_SERVICE -> {
                    isRunning = false
                    stopSelf()
                    return START_NOT_STICKY
                }
            }

            // Normal başlatma: balonu ekle (henüz eklenmemişse)
            if (!isBubbleAdded) {
                addBubble()
            }

            START_STICKY
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand hata", e)
            recordServiceError("onStartCommand", e)
            safeStopForeground()
            isRunning = false
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun startForegroundSafely() {
        try {
            startForeground(Constants.NOTIFICATION_ID, buildNotification())
            Log.d(TAG, "startForeground başarılı")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground hata", e)
            recordServiceError("startForeground", e)
            throw e   // Üst catch bloğu yakalasın ve servis durdurulsun
        }
    }

    private fun safeStopForeground() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.e(TAG, "stopForeground hata", e)
        }
    }

    // -------------------------------------------------------------------------
    // Bildirim
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val toggleAction = if (prefs.isBubbleHidden) Constants.ACTION_SHOW_BUBBLE else Constants.ACTION_HIDE_BUBBLE
        val toggleLabel = if (prefs.isBubbleHidden) {
            getString(R.string.notification_action_show)
        } else {
            getString(R.string.notification_action_hide)
        }

        val togglePendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_TOGGLE,
            Intent(this, FloatingOverlayService::class.java).setAction(toggleAction),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, FloatingOverlayService::class.java).setAction(Constants.ACTION_STOP_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_running_text))
            .setSmallIcon(R.drawable.ic_add)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, toggleLabel, togglePendingIntent)
            .addAction(0, getString(R.string.notification_action_close), stopPendingIntent)
            .build()
    }

    private fun refreshNotification() {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(Constants.NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "refreshNotification hata", e)
        }
    }

    // -------------------------------------------------------------------------
    // Balon ekleme / kaldırma / gizleme
    // -------------------------------------------------------------------------

    private fun addBubble() {
        val binding = OverlayBubbleBinding.inflate(LayoutInflater.from(themedInflaterContext))
        bubbleBinding = binding

        val sizePx = dpToPx(prefs.bubbleSizeDp)

        bubbleParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.bubbleX
            y = prefs.bubbleY
        }

        binding.root.setOnTouchListener { view, event -> handleBubbleTouch(view, event) }
        binding.root.setOnDragListener { view, event -> handleExternalDrag(view, event) }

        try {
            windowManager.addView(binding.root, bubbleParams)
            isBubbleAdded = true
            if (prefs.isBubbleHidden) {
                binding.root.visibility = View.GONE
            }
            setBubbleStatus(ok = true)
            Log.d(TAG, "Balon eklendi")
        } catch (e: Exception) {
            Log.e(TAG, "addBubble hata", e)
            recordServiceError("addBubble", e)
            bubbleBinding = null
            isRunning = false
            stopSelf()
        }
    }

    private fun setBubbleStatus(ok: Boolean) {
        val binding = bubbleBinding ?: return
        try {
            val colorRes = if (ok) R.color.primary_light else R.color.error_light
            binding.root.setCardBackgroundColor(
                androidx.core.content.ContextCompat.getColor(this, colorRes)
            )
        } catch (e: Exception) {
            Log.e(TAG, "setBubbleStatus hata", e)
        }
    }

    private fun flashBubbleError() {
        setBubbleStatus(ok = false)
        bubbleBinding?.root?.postDelayed({ setBubbleStatus(ok = true) }, 1500L)
    }

    private fun removeBubble() {
        closeMenuIfOpen()
        bubbleBinding?.let {
            try {
                windowManager.removeView(it.root)
                Log.d(TAG, "Balon kaldırıldı")
            } catch (e: Exception) {
                Log.e(TAG, "removeBubble hata", e)
            }
        }
        bubbleBinding = null
        isBubbleAdded = false
    }

    private fun hideBubble() {
        prefs.isBubbleHidden = true
        closeMenuIfOpen()
        bubbleBinding?.root?.visibility = View.GONE
        refreshNotification()
    }

    private fun showBubble() {
        prefs.isBubbleHidden = false
        if (!isBubbleAdded) {
            if (Settings.canDrawOverlays(this)) addBubble()
        } else {
            bubbleBinding?.root?.visibility = View.VISIBLE
        }
        refreshNotification()
    }

    fun applyBubbleSize(newSizeDp: Int) {
        prefs.bubbleSizeDp = newSizeDp
        if (!isBubbleAdded || !::bubbleParams.isInitialized) return
        val sizePx = dpToPx(prefs.bubbleSizeDp)
        bubbleParams.width = sizePx
        bubbleParams.height = sizePx
        safeUpdateBubbleLayout()
    }

    // -------------------------------------------------------------------------
    // Dokunma / sürükleme / kenara yapışma
    // -------------------------------------------------------------------------

    private fun handleBubbleTouch(view: View, event: MotionEvent): Boolean {
        return try {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = bubbleParams.x
                    downY = bubbleParams.y
                    downTouchX = event.rawX
                    downTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downTouchX).toInt()
                    val dy = (event.rawY - downTouchY).toInt()
                    if (!isDragging && (abs(dx) > Constants.DRAG_THRESHOLD_PX || abs(dy) > Constants.DRAG_THRESHOLD_PX)) {
                        isDragging = true
                        closeMenuIfOpen()
                    }
                    if (isDragging) {
                        bubbleParams.x = downX + dx
                        bubbleParams.y = downY + dy
                        safeUpdateBubbleLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isDragging) toggleMenu() else snapToEdge()
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleBubbleTouch hata", e)
            false
        }
    }

    private fun safeUpdateBubbleLayout() {
        bubbleBinding?.let {
            try {
                windowManager.updateViewLayout(it.root, bubbleParams)
            } catch (e: Exception) {
                Log.e(TAG, "updateViewLayout hata", e)
            }
        }
    }

    private fun snapToEdge() {
        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleWidth = bubbleParams.width
        val goLeft = (bubbleParams.x + bubbleWidth / 2) < (screenWidth / 2)
        val targetX = if (goLeft) 0 else (screenWidth - bubbleWidth)

        ValueAnimator.ofInt(bubbleParams.x, targetX).apply {
            interpolator = OvershootInterpolator(0.9f)
            duration = Constants.SNAP_ANIMATION_DURATION_MS
            addUpdateListener { animation ->
                bubbleParams.x = animation.animatedValue as Int
                safeUpdateBubbleLayout()
            }
            start()
        }

        prefs.bubbleX = targetX
        prefs.bubbleY = bubbleParams.y
        prefs.isBubbleOnLeftEdge = goLeft
    }

    // -------------------------------------------------------------------------
    // Dış uygulamalardan sürükle-bırak
    // -------------------------------------------------------------------------

    private fun handleExternalDrag(view: View, event: DragEvent): Boolean {
        return try {
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    bubbleBinding?.root?.alpha = 0.55f; true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    bubbleBinding?.root?.alpha = 1f; true
                }
                DragEvent.ACTION_DROP -> {
                    bubbleBinding?.root?.alpha = 1f
                    handleDrop(event)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    bubbleBinding?.root?.alpha = 1f; true
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleExternalDrag hata", e)
            false
        }
    }

    private fun handleDrop(event: DragEvent) {
        val clipData = event.clipData
        if (clipData == null || clipData.itemCount == 0) {
            showDragUnsupportedNotice()
            flashBubbleError()
            return
        }
        var addedAny = false
        for (i in 0 until clipData.itemCount) {
            val uri = clipData.getItemAt(i).uri ?: continue
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                serviceScope.launch {
                    try {
                        repository.addItemFromUri(applicationContext, uri, ItemType.FOLDER)
                    } catch (e: Exception) {
                        Log.e(TAG, "addItemFromUri hata", e)
                        flashBubbleError()
                    }
                }
                addedAny = true
            } catch (se: SecurityException) {
                Log.w(TAG, "Kalıcı URI izni alınamadı: $uri")
            } catch (e: Exception) {
                Log.e(TAG, "handleDrop hata", e)
            }
        }
        if (!addedAny) {
            showDragUnsupportedNotice()
            flashBubbleError()
        }
    }

    private fun showDragUnsupportedNotice() {
        try {
            val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_add)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.drag_unsupported_message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java)?.notify(Constants.DRAG_NOTICE_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "showDragUnsupportedNotice hata", e)
        }
    }

    // -------------------------------------------------------------------------
    // Açılır menü
    // -------------------------------------------------------------------------

    private fun toggleMenu() {
        if (isMenuOpen) closeMenuIfOpen() else openMenu()
    }

    private fun openMenu() {
        if (menuBinding != null) return
        val binding = OverlayMenuBinding.inflate(LayoutInflater.from(themedInflaterContext))
        menuBinding = binding

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleParams.x
            y = (bubbleParams.y - dpToPx(Constants.MENU_VERTICAL_OFFSET_DP) - dpToPx(160)).coerceAtLeast(0)
        }

        binding.root.alpha = 0f
        binding.root.scaleX = 0.85f
        binding.root.scaleY = 0.85f

        try {
            windowManager.addView(binding.root, menuParams)
        } catch (e: Exception) {
            Log.e(TAG, "openMenu addView hata", e)
            menuBinding = null
            return
        }

        binding.root.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(Constants.MENU_ANIMATION_DURATION_MS)
            .start()
        isMenuOpen = true

        binding.menuSelectFile.setOnClickListener {
            launchPicker(isFolder = false)
            closeMenuIfOpen()
        }
        binding.menuSelectFolder.setOnClickListener {
            launchPicker(isFolder = true)
            closeMenuIfOpen()
        }
        binding.menuRecent.setOnClickListener {
            openShelfList()
            closeMenuIfOpen()
        }
        binding.menuSettings.setOnClickListener {
            openSettings()
            closeMenuIfOpen()
        }
        binding.menuClose.setOnClickListener {
            closeMenuIfOpen()
            isRunning = false
            stopSelf()
        }
    }

    private fun closeMenuIfOpen() {
        val binding = menuBinding ?: return
        binding.root.animate()
            .alpha(0f).scaleX(0.85f).scaleY(0.85f)
            .setDuration(Constants.MENU_ANIMATION_DURATION_MS)
            .withEndAction {
                try {
                    windowManager.removeView(binding.root)
                } catch (e: Exception) {
                    Log.e(TAG, "closeMenu removeView hata", e)
                }
            }
            .start()
        menuBinding = null
        isMenuOpen = false
    }

    private fun launchPicker(isFolder: Boolean) {
        try {
            val intent = Intent(this, PickerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Constants.EXTRA_PICK_FOLDER, isFolder)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "launchPicker hata", e)
        }
    }

    private fun openShelfList() {
        try {
            val intent = Intent(this, ShelfListActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Constants.EXTRA_SORT_RECENT, true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openShelfList hata", e)
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openSettings hata", e)
        }
    }

    // -------------------------------------------------------------------------
    // Yardımcılar
    // -------------------------------------------------------------------------

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * Servis içinde yakalanan hataları (önceden sadece Logcat'e yazılıyordu)
     * SharedPreferences'a kaydeder. MainActivity, servis başlatma denemesinden
     * sonra bu değeri okuyup kullanıcıya gösterir. Böylece "Başlat'a basınca
     * hiçbir şey olmuyor" durumunda artık gerçek hata mesajı görülebilir
     * (örn. bazı Xiaomi/Oppo/Vivo/Huawei cihazlarında ek "arka planda
     * açılır pencere" izni kapalı olduğunda WindowManager.addView SecurityException fırlatır).
     */
    private fun recordServiceError(where: String, e: Throwable) {
        try {
            val msg = "$where: ${e.javaClass.simpleName}: ${e.message}"
            val p = PreferenceHelper(applicationContext)
            p.lastServiceError = msg
            p.lastServiceErrorAt = System.currentTimeMillis()
        } catch (ignored: Exception) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy çağrıldı")
        removeBubble()
        serviceScope.cancel()
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingOverlayService"
        private const val REQUEST_CODE_TOGGLE = 10
        private const val REQUEST_CODE_STOP = 11

        /**
         * Servisin o an çalışıp çalışmadığını gösteren flag.
         * onCreate'de true, onDestroy'da false yapılır.
         * @Volatile garantisi: farklı thread'lerden okunan değer
         * her zaman en güncel hali yansıtır.
         */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
