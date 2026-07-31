package com.yukile.foldershelf.ui.picker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yukile.foldershelf.data.model.ItemType
import com.yukile.foldershelf.data.repository.ShelfRepository
import com.yukile.foldershelf.util.Constants
import kotlinx.coroutines.launch

/**
 * PickerActivity
 *
 * FloatingOverlayService bir Service oldugu icin sistemin dosya/klasor
 * secme ekranini (Storage Access Framework) dogrudan acip sonucunu
 * alamaz; bu, bir Activity'nin registerForActivityResult mekanizmasini
 * gerektirir. Bu gorunmez (seffaf temali) Activity tam olarak bu koprüyü
 * kurar: acilir, ilgili sistem seciciyi gosterir, secilen ogeyi
 * ShelfRepository'ye kaydeder ve hemen kapanir - kullanici hangi
 * uygulamadaysa oraya geri doner.
 */
class PickerActivity : AppCompatActivity() {

    private lateinit var repository: ShelfRepository
    private var pickFolder = true

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            takePersistablePermission(uri, writable = true)
            saveAndFinish(listOf(uri), ItemType.FOLDER)
        } else {
            finish()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { takePersistablePermission(it, writable = false) }
            saveAndFinish(uris, ItemType.FILE)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ShelfRepository.getInstance(applicationContext)
        pickFolder = intent.getBooleanExtra(Constants.EXTRA_PICK_FOLDER, true)

        if (savedInstanceState == null) {
            if (pickFolder) {
                folderPickerLauncher.launch(null)
            } else {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
        }
    }

    private fun takePersistablePermission(uri: Uri, writable: Boolean) {
        try {
            val flags = if (writable) {
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            } else {
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * Secilen tum ogeleri TEK bir coroutine icinde sirayla kaydeder ve
     * ancak hepsi tamamlandiktan SONRA Activity'yi kapatir. Bu, birden
     * fazla secimde erken finish() cagrisinin lifecycleScope'u iptal
     * edip bazi kayitlari kaybetmesini onler.
     */
    private fun saveAndFinish(uris: List<Uri>, type: ItemType) {
        lifecycleScope.launch {
            uris.forEach { uri ->
                repository.addItemFromUri(applicationContext, uri, type)
            }
            finish()
        }
    }
}
