package com.hdtv.live

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var label: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val hideLabel = Runnable { label.visibility = View.GONE }
    private val retry = Runnable { playCurrent() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        view = PlayerView(this).apply {
            useController = false
            setBackgroundColor(Color.BLACK)
        }
        label = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(Color.parseColor("#99000000"), dp(10).toFloat())
        }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            label,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).also {
                it.setMargins(dp(32), dp(24), 0, 0)
            }
        )
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0")
        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                label.text = "${current()?.name ?: ""}\n播放失败,3秒后重试(上下键换台)"
                showLabel()
                handler.postDelayed(retry, 3000)
            }
        })
        view.player = p
        player = p
        playCurrent()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        view.player = null
        player?.release()
        player = null
    }

    private fun current(): Channel? {
        val list = Store.list
        if (list.isEmpty()) return null
        return list[Store.index.coerceIn(0, list.size - 1)]
    }

    private fun playCurrent() {
        handler.removeCallbacks(retry)
        val ch = current()
        if (ch == null) {
            finish()
            return
        }
        player?.apply {
            setMediaItem(MediaItem.fromUri(ch.url))
            prepare()
            playWhenReady = true
        }
        label.text = ch.name
        showLabel()
    }

    private fun showLabel() {
        label.visibility = View.VISIBLE
        handler.removeCallbacks(hideLabel)
        handler.postDelayed(hideLabel, 3000)
    }

    private fun step(delta: Int) {
        val n = Store.list.size
        if (n == 0) return
        Store.index = (Store.index + delta + n) % n
        playCurrent()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { step(-1); true }
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(1); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
            label.text = current()?.name ?: ""
            showLabel()
            true
        }
        else -> super.onKeyDown(keyCode, event)
    }
}
