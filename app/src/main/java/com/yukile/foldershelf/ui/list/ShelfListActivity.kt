package com.yukile.foldershelf.ui.list

import android.net.Uri
import android.os.Bundle
import android.view.DragEvent
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.yukile.foldershelf.R
import com.yukile.foldershelf.data.model.ShelfItem
import com.yukile.foldershelf.databinding.ActivityShelfListBinding
import com.yukile.foldershelf.util.DocumentTreeUtils
import com.yukile.foldershelf.util.ShareUtils
import kotlinx.coroutines.launch

/**
 * ShelfListActivity
 *
 * "Yonetim" ve "Son Eklenenler" ekrani. Raftaki tum klasor/dosyalari
 * listeler; her satirdan yeniden adlandirma, silme, bilgi goruntuleme
 * ve paylasim islemlerine ulasilabilir.
 */
class ShelfListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShelfListBinding
    private val viewModel: ShelfListViewModel by viewModels()
    private lateinit var adapter: ShelfItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShelfListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyEdgeToEdgeInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        setupRecyclerView()
        setupDragAndDrop()
        observeViewModel()
    }

    // -----------------------------------------------------------------------
    // Surukle-birak ("drop zone")
    // -----------------------------------------------------------------------
    //
    // GitHub'in dosya yukleme kutusuna benzer bir davranis: Baska bir
    // uygulamadan (dosya yoneticisi vb.) bir dosya/klasor bu ekranin
    // uzerine suruklendiginde, tum ekranin uzerinde kesikli kenarlikli,
    // "+" simgeli yari saydam bir "buraya birakin" katmani belirir.
    // Birakildiginda oge rafa eklenir ve katman kaybolur; eklenen oge
    // liste zaten reaktif oldugu icin (StateFlow) hemen listede gorunur
    // ve "orda kalir".

    private fun setupDragAndDrop() {
        // Kesikli (dashed) kenarlik donanim hizlandirmali katmanlarda
        // duzgun cizilmeyebilir; yazilim katmanina zorla.
        binding.dropZoneCard.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

        binding.root.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    // Sadece dosya/klasor URI'si tasiyan surukleme
                    // islemlerini kabul et.
                    val accepts = event.clipDescription?.hasMimeType("*/*") == true
                    if (accepts) showDropZone()
                    accepts
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    highlightDropZone(entered = true)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED -> {
                    highlightDropZone(entered = false)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    handleDrop(event)
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    hideDropZone()
                    true
                }
                else -> true
            }
        }
    }

    private fun showDropZone() {
        binding.dropZoneOverlay.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(150L).start()
        }
        highlightDropZone(entered = false)
    }

    private fun hideDropZone() {
        binding.dropZoneOverlay.animate()
            .alpha(0f)
            .setDuration(150L)
            .withEndAction { binding.dropZoneOverlay.visibility = View.GONE }
            .start()
    }

    private fun highlightDropZone(entered: Boolean) {
        // Uzerine gelindiginde kart biraz buyur ve daha az saydam olur;
        // tam olarak GitHub'daki mavi vurgulu kutuya benzer bir his verir.
        val targetScale = if (entered) 1.03f else 1f
        val targetAlpha = if (entered) 1f else 0.85f
        binding.dropZoneCard.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .alpha(targetAlpha)
            .setDuration(120L)
            .start()
    }

    private fun handleDrop(event: DragEvent) {
        val clipData = event.clipData
        if (clipData == null || clipData.itemCount == 0) {
            hideDropZone()
            return
        }
        var addedAny = false
        for (i in 0 until clipData.itemCount) {
            val uri = clipData.getItemAt(i).uri ?: continue
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                viewModel.addFromDrop(uri)
                addedAny = true
            } catch (e: SecurityException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (addedAny) {
            Toast.makeText(this, R.string.drop_zone_added_toast, Toast.LENGTH_SHORT).show()
        }
        hideDropZone()
    }

    private fun applyEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = ShelfItemAdapter(
            onInfoClick = { item -> showDetail(item) },
            onShareClick = { item -> shareItem(item) },
            onRenameClick = { item -> showRenameDialog(item) },
            onDeleteClick = { item -> showDeleteConfirmation(item) },
            onCancelRefreshClick = { item -> viewModel.cancelRefresh(item.id) }
        )
        binding.recyclerShelf.layoutManager = LinearLayoutManager(this)
        binding.recyclerShelf.adapter = adapter
        binding.recyclerShelf.setHasFixedSize(true)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collect { items ->
                        adapter.submitList(items)
                        binding.textEmptyState.visibility =
                            if (items.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.refreshingIds.collect { ids ->
                        adapter.setRefreshingIds(ids)
                    }
                }
            }
        }
    }

    private fun showDetail(item: ShelfItem) {
        ItemDetailBottomSheet.newInstance(item.id)
            .show(supportFragmentManager, ItemDetailBottomSheet.TAG)
    }

    private fun showRenameDialog(item: ShelfItem) {
        val input = EditText(this).apply {
            setText(item.displayName)
            setSelection(text.length)
        }
        val paddingPx = (16 * resources.displayMetrics.density).toInt()
        val container = android.widget.FrameLayout(this).apply {
            setPadding(paddingPx, paddingPx / 2, paddingPx, 0)
            addView(input)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.action_rename)
            .setView(container)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val newName = input.text?.toString().orEmpty()
                if (newName.isNotBlank()) {
                    viewModel.rename(item.id, newName)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(item: ShelfItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.confirm_delete_message, item.displayName))
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.delete(item.id)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun shareItem(item: ShelfItem) {
        lifecycleScope.launch {
            val uris: List<Uri> = DocumentTreeUtils.collectFileUris(
                this@ShelfListActivity, Uri.parse(item.uri), item.type
            )
            if (uris.isEmpty()) {
                AlertDialog.Builder(this@ShelfListActivity)
                    .setMessage(R.string.share_empty_message)
                    .setPositiveButton(R.string.action_ok, null)
                    .show()
                return@launch
            }
            startActivity(ShareUtils.chooserIntent(uris, item.displayName))
        }
    }
}
