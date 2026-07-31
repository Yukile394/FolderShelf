package com.yukile.foldershelf.ui.list

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.yukile.foldershelf.R
import com.yukile.foldershelf.data.model.ItemType
import com.yukile.foldershelf.databinding.BottomSheetItemDetailBinding
import com.yukile.foldershelf.util.Constants
import kotlinx.coroutines.launch

/**
 * ItemDetailBottomSheet
 *
 * "Bilgileri Goster" aksiyonuyla acilan alt sayfa (bottom sheet). Secili
 * ogenin adini, turunu, boyutunu, dosya sayisini ve son degistirilme
 * tarihini gosterir. Veri, ekrani paylasan ShelfListViewModel uzerinden
 * canli olarak (StateFlow ile) izlenir; boylece boyut hesaplamasi devam
 * ederken sayfa acikken bile sonuc geldiginde otomatik guncellenir.
 */
class ItemDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetItemDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ShelfListViewModel by activityViewModels()
    private var itemId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        itemId = arguments?.getLong(Constants.EXTRA_ITEM_ID) ?: -1L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetItemDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.items.collect { items ->
                    val item = items.firstOrNull { it.id == itemId }
                    if (item == null) {
                        dismissAllowingStateLoss()
                        return@collect
                    }

                    binding.textDetailName.text = item.displayName
                    binding.textDetailType.text = if (item.type == ItemType.FOLDER) {
                        getString(R.string.type_folder)
                    } else {
                        getString(R.string.type_file)
                    }
                    binding.textDetailAdded.text = getString(
                        R.string.detail_added_on,
                        ShelfItemAdapter.formatDate(item.addedAt)
                    )

                    if (item.hasStats) {
                        binding.textDetailSize.text = Formatter.formatShortFileSize(
                            requireContext(), item.cachedSizeBytes
                        )
                        binding.textDetailFileCount.text = item.cachedFileCount.toString()
                        binding.textDetailModified.text = ShelfItemAdapter.formatDate(item.cachedLastModified)
                        binding.progressDetail.visibility = View.GONE
                    } else {
                        binding.textDetailSize.text = getString(R.string.detail_calculating)
                        binding.textDetailFileCount.text = getString(R.string.detail_calculating)
                        binding.textDetailModified.text = getString(R.string.detail_calculating)
                        binding.progressDetail.visibility = View.VISIBLE
                        viewModel.refreshStats(item)
                    }
                }
            }
        }

        binding.buttonCloseDetail.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ItemDetailBottomSheet"

        fun newInstance(itemId: Long): ItemDetailBottomSheet {
            return ItemDetailBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(Constants.EXTRA_ITEM_ID, itemId)
                }
            }
        }
    }
}
