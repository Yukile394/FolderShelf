package com.yukile.foldershelf.ui.list

import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yukile.foldershelf.R
import com.yukile.foldershelf.data.model.ItemType
import com.yukile.foldershelf.data.model.ShelfItem
import com.yukile.foldershelf.databinding.ItemShelfBinding
import java.text.DateFormat
import java.util.Date

/**
 * ShelfItemAdapter
 *
 * Raftaki klasor/dosyalari listeleyen RecyclerView adaptoru. DiffUtil
 * kullanarak yalnizca degisen satirlari yeniden cizer (performans icin
 * onemlidir, "Performans Optimizasyonu" gereksinimine hizmet eder).
 */
class ShelfItemAdapter(
    private val onInfoClick: (ShelfItem) -> Unit,
    private val onShareClick: (ShelfItem) -> Unit,
    private val onRenameClick: (ShelfItem) -> Unit,
    private val onDeleteClick: (ShelfItem) -> Unit,
    private val onCancelRefreshClick: (ShelfItem) -> Unit
) : ListAdapter<ShelfItem, ShelfItemAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var refreshingIds: Set<Long> = emptySet()

    fun setRefreshingIds(ids: Set<Long>) {
        val previous = refreshingIds
        refreshingIds = ids
        // Yalnizca durumu degisen satirlari yeniden ciz.
        for (i in 0 until itemCount) {
            val id = getItem(i).id
            if (previous.contains(id) != ids.contains(id)) {
                notifyItemChanged(i)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemShelfBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), refreshingIds.contains(getItem(position).id))
    }

    inner class ViewHolder(private val binding: ItemShelfBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShelfItem, isRefreshing: Boolean) {
            val context = binding.root.context

            binding.textName.text = item.displayName
            binding.iconType.setImageResource(
                if (item.type == ItemType.FOLDER) R.drawable.ic_folder else R.drawable.ic_file
            )

            binding.textSubtitle.text = if (item.hasStats) {
                val sizeText = Formatter.formatShortFileSize(context, item.cachedSizeBytes)
                context.getString(R.string.shelf_item_subtitle, sizeText, item.cachedFileCount)
            } else {
                context.getString(R.string.shelf_item_subtitle_pending)
            }

            binding.progressRefreshing.visibility = if (isRefreshing) View.VISIBLE else View.GONE
            binding.buttonCancelRefresh.visibility = if (isRefreshing) View.VISIBLE else View.GONE
            binding.buttonCancelRefresh.setOnClickListener { onCancelRefreshClick(item) }

            binding.buttonMore.setOnClickListener { anchor ->
                showPopupMenu(anchor, item)
            }

            binding.root.setOnClickListener {
                onInfoClick(item)
            }
        }

        private fun showPopupMenu(anchor: View, item: ShelfItem) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.menuInflater.inflate(R.menu.menu_shelf_item, popup.menu)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_info -> onInfoClick(item)
                    R.id.action_share -> onShareClick(item)
                    R.id.action_rename -> onRenameClick(item)
                    R.id.action_delete -> onDeleteClick(item)
                }
                true
            }
            popup.show()
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ShelfItem>() {
            override fun areItemsTheSame(oldItem: ShelfItem, newItem: ShelfItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ShelfItem, newItem: ShelfItem) =
                oldItem == newItem
        }

        fun formatDate(timestampMillis: Long): String {
            if (timestampMillis <= 0L) return ""
            return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(Date(timestampMillis))
        }
    }
}
