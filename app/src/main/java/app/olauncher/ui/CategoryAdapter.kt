package app.olauncher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.databinding.AdapterCategoryItemBinding

class CategoryAdapter(
    private val categories: List<String>,
    private val onCategorySelected: (String) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    private var selectedCategory: String = categories.first()

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
            binding.categoryName.isSelected = category == selectedCategory
            binding.root.setOnClickListener {
                val oldSelected = selectedCategory
                selectedCategory = category
                notifyItemChanged(categories.indexOf(oldSelected))
                notifyItemChanged(categories.indexOf(selectedCategory))
                onCategorySelected(category)
            }
        }
    }
}
