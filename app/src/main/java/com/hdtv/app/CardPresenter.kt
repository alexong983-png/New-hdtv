package com.hdtv.app

import android.graphics.Color
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions

private const val CARD_WIDTH = 313
private const val CARD_HEIGHT = 176

class CardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = object : ImageCardView(parent.context) {}
        cardView.isFocusable = true
        cardView.isFocusableInTouchMode = true
        cardView.setBackgroundColor(Color.parseColor("#1b1b1b"))
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val channel = item as Channel
        val cardView = viewHolder.view as ImageCardView
        cardView.titleText = channel.title
        cardView.contentText = if (channel.isLive) "直播" else "点播"
        cardView.setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)

        if (!channel.logoUrl.isNullOrBlank()) {
            Glide.with(cardView.context)
                .load(channel.logoUrl)
                .apply(RequestOptions().centerInside())
                .into(cardView.mainImageView)
        } else {
            cardView.mainImageView.setImageDrawable(
                cardView.context.getDrawable(R.drawable.app_icon)
            )
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.badgeImage = null
        cardView.mainImage = null
    }
}
