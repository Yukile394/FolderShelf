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

/**
 * FloatingOverlayService
 *
 * Ekranin kenarina yapisan, suruklenebilir "+" balonunu ve acilir menuyu
 * WindowManager uzerinden yonetir. Bir Activity'ye degil dogrudan bu
 * Service'e bagli oldugu icin, kullanici "Kapat" demedigi surece (veya
 * sistem bellek nedeniyle servisi sonlandirmadikca) uygulama ekrani
 * kapatilsa bile calismaya devam eder.
 *
 * SURUKLE-BIRAK NOTU: Dosya yoneticisi gibi baska bir uygulamadan
 * dogrudan klasor surukleyip bu balonun uzerine birakma ozelligi,
 * Android'in genel (global/cross-app) surukle-birak altyapisina
 * dayanir ve kaynak uygulamanin bunu desteklemesini gerektirir. Butun
 * dosya yoneticileri bunu desteklemez; bu yuzden bu ozellik "best effort"
 * (en iyi caba) olarak sunulur ve calismadigi durumlarda kullaniciya
 * "Klasor Sec" / "Dosya Sec" (her zaman calisan sistem secici) onerilir.
 */
class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PreferenceHelper
    private lateinit var repository: ShelfRepository

    private var bubbleBinding: OverlayBubbleBinding? = null
    private var menuBinding: OverlayMenuBinding? = null
    private var isBubbleAdded = false
    private var isMenuOpen = false

    private lateinit var bubbleParams: WindowManager.LayoutParams

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Dokunma/surukleme takibi
    private var downX = 0
    private var downY = 0
    private var downTouchX = 0f
    private var downTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = PreferenceHelper(applicationContext)
        repository = ShelfRepository.getInstance(applicationContext)
        createNotificationChannel()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ONEMLI: startForeground() HER onStartCommand cagrisinda, action ne
        // olursa olsun, en basta cagrilmali. Eskiden SHOW/HIDE_BUBBLE
        // aksiyonlarinda bu satira hic ulasilmiyordu; bu da servisin bazen
        // "foreground" durumuna hic gecmemesine ve MainActivity "Calisiyor"
        // yazsa bile balonun (+ simgesi) gercekte hic eklenmemis olmasina
        // (ya da sessizce kapanmasina) yol aciyordu.
        startForeground(Constants.NOTIFICATION_ID, buildNotification())

        if (!Settings.canDrawOverlays(this)) {
            // Izin sonradan geri alinmis olabilir; guvenli sekilde dur.
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
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!isBubbleAdded) {
            addBubble()
        }

        return START_STICKY
    }

    // region Bildirim

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
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(Constants.NOTIFICATION_ID, buildNotification())
    }

    // endregion

    // region Balon ekleme / kaldirma / gizleme

    private fun addBubble() {
        val binding = OverlayBubbleBinding.inflate(LayoutInflater.from(this))
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
        } catch (e: Exception) {
            // Bazi OEM cihazlarda (ozellikle izin az once verilmisse) pencere
            // ekleme gecikmeli basarisiz olabilir; servisi guvenle durdur.
            e.printStackTrace()
            stopSelf()
        }
    }

    /**
     * Balonun arka plan rengini calisma durumuna gore ayarlar:
     * calisiyorsa mavi (colorPrimary), bir sorun olustuysa (izin kaybi,
     * desteklenmeyen surukle-birak, beklenmeyen hata) kirmizi (colorError).
     */
    private fun setBubbleStatus(ok: Boolean) {
        val binding = bubbleBinding ?: return
        val colorRes = if (ok) R.color.primary_light else R.color.error_light
        binding.root.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, colorRes))
    }

    /** Kisa sureligine kirmizi gosterip sonra normale (mavi) doner. */
    private fun flashBubbleError() {
        setBubbleStatus(ok = false)
        bubbleBinding?.root?.postDelayed({ setBubbleStatus(ok = true) }, 1500L)
    }

    private fun removeBubble() {
        closeMenuIfOpen()
        bubbleBinding?.let {
            try {
                windowManager.removeView(it.root)
            } catch (e: Exception) {
                e.printStackTrace()
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

    /** Balon boyutu ayarlar ekranindan degistirildiginde canli olarak uygular. */
    fun applyBubbleSize(newSizeDp: Int) {
        prefs.bubbleSizeDp = newSizeDp
        if (!isBubbleAdded || !::bubbleParams.isInitialized) {
            // Balon henuz eklenmemis; bir sonraki addBubble() cagrisi
            // zaten guncel boyutu prefs'ten okuyacak. Burada erken donmek
            // "lateinit property bubbleParams has not been initialized"
            // cokmesini engeller.
            return
        }
        val sizePx = dpToPx(prefs.bubbleSizeDp)
        bubbleParams.width = sizePx
        bubbleParams.height = sizePx
        safeUpdateBubbleLayout()
    }

    // endregion

    // region Dokunma / surukleme / kenara yapisma

    private fun handleBubbleTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = bubbleParams.x
                downY = bubbleParams.y
                downTouchX = event.rawX
                downTouchY = event.rawY
                isDragging = false
                return true
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
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) {
                    toggleMenu()
                } else {
                    snapToEdge()
                }
                return true
            }
        }
        return false
    }

    private fun safeUpdateBubbleLayout() {
        bubbleBinding?.let {
            try {
                windowManager.updateViewLayout(it.root, bubbleParams)
            } catch (e: Exception) {
                e.printStackTrace()
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

    // endregion

    // region Dis uygulamalardan surukle-birak (en iyi caba)

    private fun handleExternalDrag(view: View, event: DragEvent): Boolean {
        return try {
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> {
                    bubbleBinding?.root?.alpha = 0.55f
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    bubbleBinding?.root?.alpha = 1f
                    true
                }
                DragEvent.ACTION_DROP -> {
                    bubbleBinding?.root?.alpha = 1f
                    handleDrop(event)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    bubbleBinding?.root?.alpha = 1f
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
                        // Surukle-birak ile gelen bir URI genelde GERCEK
                        // bir "tree" URI degildir (duz belge URI'sidir).
                        // repository.addItemFromUri artik bunu guvenli
                        // sekilde tespit ediyor (once tree dener, olmazsa
                        // tekil belgeye duser) ve hicbir zaman exception
                        // firlatmaz; yine de burada ek bir guvenlik agi var.
                        repository.addItemFromUri(applicationContext, uri, ItemType.FOLDER)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        flashBubbleError()
                    }
                }
                addedAny = true
            } catch (se: SecurityException) {
                // Bu kaynak uygulama global surukleme izni vermiyor.
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (!addedAny) {
            showDragUnsupportedNotice()
            flashBubbleError()
        }
    }

    private fun showDragUnsupportedNotice() {
        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_add)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.drag_unsupported_message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(Constants.DRAG_NOTICE_ID, notification)
    }

    // endregion

    // region Acilir menu

    private fun toggleMenu() {
        if (isMenuOpen) closeMenuIfOpen() else openMenu()
    }

    private fun openMenu() {
        if (menuBinding != null) return
        val binding = OverlayMenuBinding.inflate(LayoutInflater.from(this))
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
            e.printStackTrace()
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
                    e.printStackTrace()
                }
            }
            .start()
        menuBinding = null
        isMenuOpen = false
    }

    private fun launchPicker(isFolder: Boolean) {
        val intent = Intent(this, PickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Constants.EXTRA_PICK_FOLDER, isFolder)
        }
        startActivity(intent)
    }

    private fun openShelfList() {
        val intent = Intent(this, ShelfListActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Constants.EXTRA_SORT_RECENT, true)
        }
        startActivity(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // endregion

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeBubble()
        serviceScope.cancel()
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_CODE_TOGGLE = 10
        private const val REQUEST_CODE_STOP = 11

        /**
         * Servisin gercekten canli olup olmadigini tutar. MainActivity
         * bunu kullanarak "Calisiyor" durumunu sadece izinlere gore degil,
         * balonun fiilen ekli olup olmadigina gore gosterebilir.
         */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
