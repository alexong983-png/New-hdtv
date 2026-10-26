package com.hdtv.live

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

    private lateinit var tabRow: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var status: TextView
    private val channelAdapter = ChannelAdapter { open(it) }
    private val tabViews = ArrayList<TextView>()
    private var all: List<Channel> = emptyList()
    private var groups: Map<String, List<Channel>> = emptyMap()
    private var selectedName = ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1923"))
            setPadding(dp(32), dp(16), dp(32), 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            ImageView(this).apply { setImageResource(R.mipmap.ic_launcher) },
            LinearLayout.LayoutParams(dp(44), dp(44))
        )
        header.addView(TextView(this).apply {
            text = "Hdtv"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), 0, 0, 0)
        })
        root.addView(header)

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
        }
        tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(12))
        }
        scroll.addView(tabRow)
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        status = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, dp(24), 0, dp(24))
            isFocusable = true
            setOnClickListener { load() }
        }
        root.addView(status)

        val span = (resources.displayMetrics.widthPixels / (190 * resources.displayMetrics.density))
            .toInt().coerceAtLeast(2)
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, span)
            adapter = channelAdapter
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(24))
        }
        root.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        setContentView(root)
        load()
    }

    private fun load() {
        status.text = "加载中…"
        status.visibility = View.VISIBLE
        Thread {
            try {
                val list = M3u.fetch(Config.PLAYLIST_URL)
                runOnUiThread { show(list) }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "加载失败:${e.message}\n按确认键重试"
                    status.requestFocus()
                }
            }
        }.start()
    }

    private fun show(list: List<Channel>) {
        if (list.isEmpty()) {
            status.text = "没有找到直播频道\n按确认键重试"
            status.requestFocus()
            return
        }
        status.visibility = View.GONE
        all = list
        Store.all = list
        groups = list.groupBy { it.group }

        tabRow.removeAllViews()
        tabViews.clear()
        for (name in listOf(ALL) + groups.keys) {
            val tv = TextView(this).apply {
                text = name
                textSize = 18f
                setTextColor(Color.WHITE)
                setPadding(dp(20), dp(10), dp(20), dp(10))
                isFocusable = true
                setOnFocusChangeListener { _, focused ->
                    if (focused) select(name) else refreshTabs()
                }
                setOnClickListener { select(name) }
            }
            tabRow.addView(tv, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).also {
                it.setMargins(0, 0, dp(8), 0)
            })
            tabViews.add(tv)
        }
        select(ALL)
        tabViews.firstOrNull()?.requestFocus()
    }

    private fun select(name: String) {
        selectedName = name
        val list = if (name == ALL) all else groups[name].orEmpty()
        Store.list = list
        channelAdapter.submit(list)
        refreshTabs()
    }

    private fun refreshTabs() {
        for (tv in tabViews) {
            val color = when {
                tv.isFocused -> "#1E9BF0"
                tv.text.toString() == selectedName -> "#26465F"
                else -> "#00000000"
            }
            tv.background = rounded(Color.parseColor(color), dp(20).toFloat())
        }
    }

    private fun open(ch: Channel) {
        Store.index = Store.list.indexOf(ch).coerceAtLeast(0)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    companion object {
        private const val ALL = "全部"
    }
}
