package com.yukile.foldershelf.util

/**
 * Uygulama genelinde kullanilan sabitler. Bildirim kanallari, servis
 * aksiyonlari, Intent extra anahtarlari ve balon (bubble) ile ilgili
 * varsayilan degerler burada toplanir.
 */
object Constants {

    // Bildirimler
    const val NOTIFICATION_CHANNEL_ID = "folder_shelf_overlay_channel"
    const val NOTIFICATION_ID = 1001
    const val DRAG_NOTICE_ID = 1002
    const val ADD_NOTICE_ID = 1003

    // FloatingOverlayService aksiyonlari
    const val ACTION_HIDE_BUBBLE = "com.yukile.foldershelf.action.HIDE_BUBBLE"
    const val ACTION_SHOW_BUBBLE = "com.yukile.foldershelf.action.SHOW_BUBBLE"
    const val ACTION_STOP_SERVICE = "com.yukile.foldershelf.action.STOP_SERVICE"
    const val ACTION_UPDATE_BUBBLE_SIZE = "com.yukile.foldershelf.action.UPDATE_BUBBLE_SIZE"

    // Intent extra anahtarlari
    const val EXTRA_PICK_FOLDER = "extra_pick_folder"
    const val EXTRA_SORT_RECENT = "extra_sort_recent"
    const val EXTRA_ITEM_ID = "extra_item_id"

    // Surukleme / kenara yapisma
    const val DRAG_THRESHOLD_PX = 14
    const val SNAP_ANIMATION_DURATION_MS = 260L
    const val MENU_ANIMATION_DURATION_MS = 180L
    const val MENU_VERTICAL_OFFSET_DP = 12

    // Balon boyutu (dp)
    const val DEFAULT_BUBBLE_SIZE_DP = 56
    const val MIN_BUBBLE_SIZE_DP = 40
    const val MAX_BUBBLE_SIZE_DP = 96

    // SharedPreferences
    const val PREFS_NAME = "folder_shelf_prefs"
}
