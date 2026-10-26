package com.hdtv.live

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
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
    private lateinit var panel: LinearLayout
    private lateinit var catList: ListView
    private lateinit var chList: ListView
    private val catAdapter = TextAdapter()
    private val chAdapter = TextAdapter()
    private var groupMap: Map<String, List<Channel>> = emptyMap()
    private var panelGroups: List<String> = emptyList()
    private var panelChannels: List<Channel> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private val hideLabel = Runnable { label.visibility = View.GONE }
    private val retry = Runnable { playCurrent() }

    private inner class TextAdapter : BaseAdapter() {
        var items: List<String> = emptyList()
        var marked = -1
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = (convertView as? TextView) ?: TextView(this@PlayerActivity).apply {
                textSize = 18f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            tv.text = items[position]
            tv.setTextColor(if (position == marked) Color.parseColor("#29B6F6") else Color.WHITE)
            return tv
        }
    }

    private fun makeList(): ListView = ListView(this).apply {
        divider = null
        selector = ColorDrawable(Color.parseColor("#661E9BF0"))
        isFocusable = true
    }

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

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#E60F1923"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            visibility = View.GONE
        }
        catList = makeList().apply { adapter = catAdapter }
        chList = makeList().apply { adapter = chAdapter }
        panel.addView(catList, LinearLayout.LayoutParams(dp(220), -1))
        panel.addView(chList, LinearLayout.LayoutParams(dp(380), -1).also {
            it.setMargins(dp(12), 0, 0, 0)
        })

        catList.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                fillChannels(pos)
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        catList.setOnItemClickListener { _, _, _, _ -> chList.requestFocus() }
        catList.setOnKeyListener { _, k, e ->
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT && e.action == KeyEvent.ACTION_DOWN) {
                chList.requestFocus(); true
            } else false
        }
        chList.setOnKeyListener { _, k, e ->
            if (k == KeyEvent.KEYCODE_DPAD_LEFT && e.action == KeyEvent.ACTION_DOWN) {
                catList.requestFocus(); true
            } else false
        }
        chList.setOnItemClickListener { _, _, pos, _ ->
            Store.list = panelChannels
            Store.index = pos
            hidePanel()
            playCurrent()
        }

        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            label,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).also {
                it.setMargins(dp(32), dp(24), 0, 0)
            }
        )
        root.addView(panel, FrameLayout.LayoutParams(dp(640), -1, Gravity.START))
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

    // ---- 分类列表(按 OK 键) ----
    private fun showPanel() {
        val cur = current()
        groupMap = Store.all.groupBy { it.group }
        panelGroups = listOf(ALL) + groupMap.keys
        catAdapter.items = panelGroups
        val gi = panelGroups.indexOf(cur?.group).let { if (it < 0) 0 else it }
        catAdapter.marked = gi
        catAdapter.notifyDataSetChanged()
        label.visibility = View.GONE
        panel.visibility = View.VISIBLE
        fillChannels(gi)
        catList.setSelection(gi)
        chList.requestFocus()
        val ci = panelChannels.indexOf(cur)
        if (ci >= 0) chList.setSelection(ci)
    }

    private fun fillChannels(gi: Int) {
        if (gi < 0 || gi >= panelGroups.size) return
        val name = panelGroups[gi]
        panelChannels = if (name == ALL) Store.all else groupMap[name].orEmpty()
        chAdapter.items = panelChannels.map { it.name }
        chAdapter.marked = panelChannels.indexOf(current())
        chAdapter.notifyDataSetChanged()
        catAdapter.marked = gi
        catAdapter.notifyDataSetChanged()
    }

    private fun hidePanel() {
        panel.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (panel.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                hidePanel()
                return true
            }
            return super.onKeyDown(keyCode, event)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { step(-1); true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { step(1); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU -> { showPanel(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    companion object {
        private const val ALL = "全部"
    }
}
