package com.yukile.foldershelf.ui.list

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
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
        observeViewModel()
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
