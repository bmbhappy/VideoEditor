package com.example.videoeditor.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.videoeditor.databinding.ItemFilterBinding
import com.example.videoeditor.models.FilterOption

class FilterAdapter(
    private val filters: List<FilterOption>,
    private val onFilterSelected: (FilterOption) -> Unit
) : RecyclerView.Adapter<FilterAdapter.FilterViewHolder>() {
    
    private var selectedFilterId: String = "original"
    
    inner class FilterViewHolder(private val binding: ItemFilterBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(filter: FilterOption) {
            binding.ivFilterIcon.setImageResource(filter.iconResId)
            binding.tvFilterName.text = filter.name
            
            // 設定選中狀態
            val isSelected = filter.id == selectedFilterId
            binding.root.isSelected = isSelected
            
            binding.root.setOnClickListener {
                selectedFilterId = filter.id
                onFilterSelected(filter)
                notifyDataSetChanged()
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilterViewHolder {
        val binding = ItemFilterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FilterViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: FilterViewHolder, position: Int) {
        holder.bind(filters[position])
    }
    
    override fun getItemCount(): Int = filters.size
    
    fun setSelectedFilter(filterId: String) {
        selectedFilterId = filterId
        notifyDataSetChanged()
    }
}
