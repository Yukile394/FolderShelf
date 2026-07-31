package com.yukile.foldershelf.util

import android.content.Intent
import android.net.Uri

/**
 * ShareUtils
 *
 * Eklenen klasor/dosyalari, Android'in standart paylasim akisini
 * (ACTION_SEND / ACTION_SEND_MULTIPLE) kullanarak baska uygulamalara
 * (destekleyen dosya secme ekranlari, mesajlasma uygulamalari,
 * bulut depolama vb.) gonderir. Tum URI'ler SAF uzerinden gelen icerik
 * URI'leri oldugundan gecici okuma izni (FLAG_GRANT_READ_URI_PERMISSION)
 * ile paylasilir; kaynak dosyalar hicbir sekilde degistirilmez.
 */
object ShareUtils {

    fun buildShareIntent(uris: List<Uri>): Intent {
        return if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun chooserIntent(uris: List<Uri>, title: String): Intent {
        return Intent.createChooser(buildShareIntent(uris), title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
