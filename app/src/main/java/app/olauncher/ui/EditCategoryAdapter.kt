package app.olauncher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.databinding.AdapterCategoryItemBinding

class EditCategoryAdapter(
    private val categories: List<String>,
    initialSelected: Set<String>
) : RecyclerView.Adapter<EditCategoryAdapter.ViewHolder>() {

    private val selectedCategories = initialSelected.toMutableSet()

    fun getSelectedCategories(): Set<String> = selectedCategories

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AdapterCategoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    inner class ViewHolder(private val binding: AdapterCategoryItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(category: String) {
            binding.categoryName.text = category
            binding.categoryName.isSelected = selectedCategories.contains(category)
            
            binding.root.setOnClickListener {
                if (selectedCategories.contains(category)) {
                    selectedCategories.remove(category)
                } else {
                    selectedCategories.add(category)
                }
                notifyItemChanged(bindingAdapterPosition)
            }
        }
    }
}
