package com.khizar.camerapulse

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    private val items = ArrayList<Reading>()
    private val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

    fun submit(readings: List<Reading>) {
        items.clear()
        items.addAll(readings)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reading, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val reading = items[position]
        val context = holder.itemView.context

        holder.bpmText.text = holder.itemView.context.getString(R.string.bpm_value, reading.bpm)
        holder.dateText.text = dateFormat.format(Date(reading.timestamp))
        holder.zoneText.text = zoneLabel(context, reading.bpm)

        val color = zoneColor(reading.bpm)
        holder.bpmText.setTextColor(color)
        holder.zoneText.setTextColor(color)
        val chip = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 40f * holder.itemView.resources.displayMetrics.density
            setColor((0x30 and 0xFF shl 24) or (color and 0x00FFFFFF))
        }
        holder.zoneText.background = chip
    }

    private fun zoneColor(bpm: Int): Int = when {
        bpm in 60..100 -> 0xFF4ADE80.toInt()
        bpm in 45..59 || bpm in 101..120 -> 0xFFFBBF24.toInt()
        else -> 0xFFF87171.toInt()
    }

    private fun zoneLabel(context: Context, bpm: Int): String = when {
        bpm < 60 -> context.getString(R.string.zone_resting)
        bpm <= 100 -> context.getString(R.string.zone_normal)
        bpm <= 120 -> context.getString(R.string.zone_elevated)
        else -> context.getString(R.string.zone_high)
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val bpmText: TextView = view.findViewById(R.id.readingBpm)
        val dateText: TextView = view.findViewById(R.id.readingDate)
        val zoneText: TextView = view.findViewById(R.id.readingZone)
    }
}
