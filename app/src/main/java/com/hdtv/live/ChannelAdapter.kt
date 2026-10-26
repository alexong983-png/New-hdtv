package com.hdtv.live

import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ChannelAdapter(private val onClick: (Channel) -> Unit) :
    RecyclerView.Adapter<ChannelAdapter.VH>() {

    private var items: List<Channel> = emptyList()

    fun submit(list: List<Channel>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val root: LinearLayout, val img: ImageView, val label: TextView) :
        RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8)) }
            setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
            isFocusable = true
            isClickable = true
            background = rounded(Color.parseColor("#1A2733"), ctx.dp(10).toFloat())
        }
        val img = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6))
            background = rounded(Color.parseColor("#0F1923"), ctx.dp(6).toFloat())
        }
        root.addView(img, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(90)))
        val label = TextView(ctx).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(0, ctx.dp(8), 0, 0)
        }
        root.addView(label)

        root.setOnFocusChangeListener { v: View, focused: Boolean ->
            v.animate().scaleX(if (focused) 1.08f else 1f)
                .scaleY(if (focused) 1.08f else 1f).setDuration(120).start()
            v.translationZ = if (focused) ctx.dp(8).toFloat() else 0f
            v.background = if (focused)
                rounded(Color.parseColor("#26465F"), ctx.dp(10).toFloat(), Color.parseColor("#29B6F6"), ctx.dp(3))
            else rounded(Color.parseColor("#1A2733"), ctx.dp(10).toFloat())
        }
        return VH(root, img, label)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val ch = items[position]
        h.label.text = ch.name
        if (ch.logo.isBlank()) {
            h.img.setImageResource(R.mipmap.ic_launcher)
        } else {
            Glide.with(h.img).load(ch.logo)
                .error(R.mipmap.ic_launcher)
                .into(h.img)
        }
        h.root.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p >= 0) onClick(items[p])
        }
    }

    override fun getItemCount(): Int = items.size
}
