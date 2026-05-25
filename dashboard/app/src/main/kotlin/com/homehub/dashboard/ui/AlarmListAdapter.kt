package com.homehub.dashboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.homehub.dashboard.data.AlarmEntity
import com.homehub.dashboard.databinding.ItemAlarmBinding
import java.util.Locale

class AlarmListAdapter(
    private val onToggle: (AlarmEntity, Boolean) -> Unit,
    private val onClick: (AlarmEntity) -> Unit
) : RecyclerView.Adapter<AlarmListAdapter.AlarmHolder>() {
    private val items = mutableListOf<AlarmEntity>()

    fun submitList(data: List<AlarmEntity>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmHolder {
        val binding = ItemAlarmBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlarmHolder(binding, onToggle, onClick)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: AlarmHolder, position: Int) {
        holder.bind(items[position])
    }

    class AlarmHolder(
        private val binding: ItemAlarmBinding,
        private val onToggle: (AlarmEntity, Boolean) -> Unit,
        private val onClick: (AlarmEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AlarmEntity) {
            binding.tvTime.text = String.format(Locale.getDefault(), "%02d:%02d", item.hour, item.minute)
            val label = item.label.trim()
            val meta = buildString {
                if (label.isNotBlank()) { append(label); append("  |  ") }
                append("fade ${item.fadeInSeconds}s  |  vol ${item.targetVolume}%")
            }
            binding.tvMeta.text = meta
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = item.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, checked -> onToggle(item, checked) }
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
