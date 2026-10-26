package com.hdtv.live

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide

class MainActivity : Activity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var label: TextView
    private lateinit var splash: LinearLayout
    private lateinit var status: TextView
    private lateinit var panel: LinearLayout
    private lateinit var catList: ListView
    private lateinit var chList: ListView
    private val catAdapter = TextAdapter()
    private val chAdapter = ChannelRowAdapter()
    private var groupMap: Map<String, List<Channel>> = emptyMap()
    private var panelGroups: List<String> = emptyList()
    private var panelChannels: List<Channel> = emptyList()
    private var currentGi = 0

    private var loaded = false
    private var loading = false
    private var started = false   // 是否已经选过频道开始播放
    private var dialog: AlertDialog? = null

    private val handler = Handler(Looper.getMainLooper())
    private val hideLabel = Runnable { label.visibility = View.GONE }
    private val retry = Runnable { playCurrent() }

    // 播放时:按住确认键 3 秒 = 收藏 / 取消收藏当前频道
    private val playHold = OkHold { toggleCurrentFav() }

    // 列表里:按住确认键 3 秒 = 收藏 / 取消收藏选中的频道
    private val panelHold = OkHold {
        val p = chList.selectedItemPosition
        if (p in panelChannels.indices) toggleFavInPanel(p)
    }

    private inner class TextAdapter : BaseAdapter() {
        var items: List<String> = emptyList()
        var marked = -1
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tv = (convertView as? TextView) ?: TextView(this@MainActivity).apply {
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

    private inner class ChannelRowAdapter : BaseAdapter() {
        var items: List<Channel> = emptyList()
        var marked = -1
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(6), dp(10), dp(6))
                addView(ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }, LinearLayout.LayoutParams(dp(52), dp(38)))
                addView(TextView(context).apply {
                    textSize = 18f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(dp(12), 0, 0, 0)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            val img = row.getChildAt(0) as ImageView
            val tv = row.getChildAt(1) as TextView
            val ch = items[position]
            tv.text = ch.label()
            tv.setTextColor(if (position == marked) Color.parseColor("#29B6F6") else Color.WHITE)
            if (ch.logo.isBlank()) {
                Glide.with(this@MainActivity).clear(img)
                img.setImageDrawable(InitialsDrawable(ch.name))
            } else {
                Glide.with(this@MainActivity).load(ch.logo)
                    .placeholder(InitialsDrawable(ch.name))
                    .error(InitialsDrawable(ch.name))
                    .into(img)
            }
            return row
        }
    }

    private fun makeList(): ListView = ListView(this).apply {
        divider = null
        selector = ColorDrawable(Color.parseColor("#661E9BF0"))
        isFocusable = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Favs.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        view = PlayerView(this).apply {
            useController = false
            setBackgroundColor(Color.BLACK)
        }

        // 启动画面:Logo + 加载状态
        splash = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        splash.addView(
            ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) },
            LinearLayout.LayoutParams(dp(120), dp(120))
        )
        status = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        }
        splash.addView(status)

        label = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = rounded(Color.parseColor("#99000000"), dp(10).toFloat())
            visibility = View.GONE
        }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#F00F1923"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            visibility = View.GONE
        }
        catList = makeList().apply { adapter = catAdapter }
        chList = makeList().apply { adapter = chAdapter }
        panel.addView(catList, LinearLayout.LayoutParams(dp(220), -1))
        panel.addView(chList, LinearLayout.LayoutParams(dp(420), -1).also {
            it.setMargins(dp(12), 0, 0, 0)
        })

        catList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (pos != currentGi) fillChannels(pos)
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        catList.setOnItemClickListener { _, _, _, _ -> chList.requestFocus() }
        catList.setOnKeyListener { _, k, e ->
            if (k == KeyEvent.KEYCODE_DPAD_RIGHT && e.action == KeyEvent.ACTION_DOWN) {
                chList.requestFocus(); true
            } else false
        }

        chList.setOnItemClickListener { _, _, pos, _ -> pickChannel(pos) }
        chList.setOnKeyListener { _, k, e ->
            when (k) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (e.action == KeyEvent.ACTION_DOWN) catList.requestFocus()
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    when (e.action) {
                        KeyEvent.ACTION_DOWN -> panelHold.down(e.repeatCount)
                        KeyEvent.ACTION_UP -> if (!panelHold.up()) {
                            val p = chList.selectedItemPosition
                            if (p in panelChannels.indices) pickChannel(p)
                        }
                    }
                    true
                }
                else -> false
            }
        }

        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        root.addView(splash, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            label,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).also {
                it.setMargins(0, dp(24), dp(32), 0)
            }
        )
        root.addView(panel, FrameLayout.LayoutParams(dp(700), -1, Gravity.START))
        setContentView(root)

        load()
    }

    // ---- 加载播放列表 ----
    private fun load() {
        if (loading) return
        loading = true
        status.text = "加载中…"
        Thread {
            try {
                val list = M3u.fetch(Config.PLAYLIST_URL)
                runOnUiThread { onLoaded(list) }
            } catch (e: Exception) {
                runOnUiThread {
                    loading = false
                    status.text = "加载失败:${e.message}\n按确认键重试"
                }
            }
        }.start()
    }

    private fun onLoaded(list: List<Channel>) {
        loading = false
        if (list.isEmpty()) {
            status.text = "没有找到直播频道\n按确认键重试"
            return
        }
        loaded = true
        Store.all = list
        status.visibility = View.GONE
        // 列表占左边,Logo 放在右边剩下的空间中间
        splash.setPadding(dp(700), 0, 0, 0)
        showPanel()
    }

    // ---- 播放器 ----
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
        if (started) playCurrent()
    }

    override fun onStop() {
        super.onStop()
        dialog?.dismiss()
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
        val ch = current() ?: return
        player?.apply {
            setMediaItem(MediaItem.fromUri(ch.url))
            prepare()
            playWhenReady = true
        }
        label.text = ch.label()
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

    private fun toast(ch: Channel, added: Boolean) {
        Toast.makeText(
            this,
            if (added) "已加入收藏:${ch.name}" else "已取消收藏:${ch.name}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleCurrentFav() {
        val ch = current() ?: return
        toast(ch, Favs.toggle(ch))
        label.text = ch.label()
        showLabel()
    }

    // ---- 分类 / 频道列表 ----
    private fun showPanel() {
        val cur = if (started) current() else null
        groupMap = Store.all.groupBy { it.group }
        panelGroups = listOf(ALL_NAME, FAV_NAME) + groupMap.keys
        catAdapter.items = panelGroups
        val gi = panelGroups.indexOf(cur?.group).let { if (it < 0) 0 else it }
        currentGi = -1
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
        currentGi = gi
        panelChannels = when (val name = panelGroups[gi]) {
            ALL_NAME -> Store.all
            FAV_NAME -> Favs.list(Store.all)
            else -> groupMap[name].orEmpty()
        }
        chAdapter.items = panelChannels
        chAdapter.marked = if (started) panelChannels.indexOf(current()) else -1
        chAdapter.notifyDataSetChanged()
        catAdapter.marked = gi
        catAdapter.notifyDataSetChanged()
    }

    private fun toggleFavInPanel(p: Int) {
        val ch = panelChannels[p]
        toast(ch, Favs.toggle(ch))
        if (panelGroups.getOrNull(currentGi) == FAV_NAME) {
            fillChannels(currentGi)
            if (panelChannels.isNotEmpty()) chList.setSelection(minOf(p, panelChannels.size - 1))
        } else {
            chAdapter.notifyDataSetChanged()
        }
    }

    private fun pickChannel(pos: Int) {
        if (pos !in panelChannels.indices) return
        Store.list = panelChannels
        Store.index = pos
        started = true
        splash.visibility = View.GONE
        panel.visibility = View.GONE
        playCurrent()
    }

    // ---- 退出确认 ----
    private fun confirmExit() {
        if (dialog?.isShowing == true) return
        val watching = started
        dialog = AlertDialog.Builder(this)
            .setTitle("Hdtv")
            .setMessage(if (watching) "确定要退出,还是继续观看?" else "确定要退出吗?")
            .setPositiveButton(if (watching) "继续观看" else "取消") { d, _ -> d.dismiss() }
            .setNegativeButton("退出") { _, _ -> finish() }
            .show()
    }

    // ---- 遥控器 ----
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (panel.visibility == View.VISIBLE && started) {
                panel.visibility = View.GONE   // 回到正在看的频道
            } else {
                confirmExit()
            }
            return true
        }
        if (panel.visibility == View.VISIBLE) {
            return super.onKeyDown(keyCode, event)
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { if (started) step(-1); true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (started) step(1); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                playHold.down(event?.repeatCount ?: 0)
                true
            }
            KeyEvent.KEYCODE_MENU -> { if (loaded) showPanel(); true }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (panel.visibility != View.VISIBLE &&
            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            // 短按:打开列表(加载失败时重试);按住 3 秒已经在 playHold 里处理了收藏
            if (!playHold.up()) {
                if (loaded) showPanel() else load()
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
