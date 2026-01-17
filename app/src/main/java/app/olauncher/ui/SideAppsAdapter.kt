package app.olauncher.ui

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.AdapterSideAppBinding

class SideAppsAdapter(
    private val prefs: Prefs,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<SideAppsAdapter.ViewHolder>() {

    private data class SideAppEntry(val index: Int, val packageName: String)

    private var sideApps = listOf<SideAppEntry>()

    init {
        sideApps = getCurrentSideApps()
    }

    private fun getCurrentSideApps(): List<SideAppEntry> {
        return prefs.sideAppsOrder
            .split(",")
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
            .map { SideAppEntry(it, prefs.getSideAppPackage(it)) }
    }

    fun updateData() {
        val newList = getCurrentSideApps()
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = sideApps.size
            override fun getNewListSize() = newList.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                return sideApps[oldPos].index == newList[newPos].index
            }

            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                return sideApps[oldPos] == newList[newPos]
            }
        })
        sideApps = newList
        diffResult.dispatchUpdatesTo(this)
    }

    fun onItemMoved(fromPosition: Int, toPosition: Int) {
        val mutableList = sideApps.toMutableList()
        val item = mutableList.removeAt(fromPosition)
        mutableList.add(toPosition, item)
        sideApps = mutableList

        prefs.sideAppsOrder = sideApps.joinToString(",") { it.index.toString() }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AdapterSideAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = sideApps[position]
        holder.bind(entry.index, entry.packageName)
    }

    override fun getItemCount(): Int = sideApps.size

    inner class ViewHolder(private val binding: AdapterSideAppBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(appIndex: Int, packageName: String) = with(binding) {
            val lowerPackageName = packageName.lowercase()

            ivAppIcon.colorFilter = WHITE_FILTER
            
            // WICHTIG: Das Icon wird jetzt anhand des appIndex (Slot 1-7) gewählt,
            // nicht mehr anhand der Position in der Liste.
            val defaultIconRes = DEFAULT_ICONS[(appIndex - 1) % DEFAULT_ICONS.size]

            val customIconRes = if (lowerPackageName.isNotEmpty()) {
                ICON_KEYWORDS.entries.find { keywordEntry ->
                    keywordEntry.key.any { keyword -> lowerPackageName.contains(keyword) }
                }?.value
            } else null

            ivAppIcon.setImageResource(customIconRes ?: defaultIconRes)
            
            root.setOnClickListener { onClick(appIndex) }
            root.setOnLongClickListener {
                onLongClick(appIndex)
                true
            }
        }
    }

    companion object {
        private val WHITE_FILTER = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

        private val DEFAULT_ICONS = listOf(
            R.drawable.ic_side_web,      // Slot 1
            R.drawable.ic_side_gallery,  // Slot 2
            R.drawable.ic_side_chat,     // Slot 3
            R.drawable.ic_side_email,    // Slot 4
            R.drawable.ic_side_phone,    // Slot 5
            R.drawable.ic_check,         // Slot 6
            R.drawable.ic_side_train     // Slot 7
        )

        private val ICON_KEYWORDS = mapOf(
            listOf("chrome", "browser", "internet") to R.drawable.ic_side_web,
            listOf("gallery", "photos") to R.drawable.ic_side_gallery,
            listOf("messaging", "whatsapp", "telegram", "signal", "messenger", "orca") to R.drawable.ic_side_chat,
            listOf("mail", "outlook", "gmail") to R.drawable.ic_side_email,
            listOf("dialer", "phone", "contact") to R.drawable.ic_side_phone,
            listOf("task", "notes", "tick", "todo", "calendar") to R.drawable.ic_check,
            listOf("maps", "transit", "transport", "train", "bus") to R.drawable.ic_side_train
        )
    }
}
