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
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

class MainActivity : Activity() {

    private lateinit var tabRow: LinearLayout
    private lateinit var grid: RecyclerView
    private lateinit var status: TextView
    private lateinit var emptyHint: TextView
    private val channelAdapter = ChannelAdapter({ open(it) }, { toggleFav(it) })
    private val tabViews = ArrayList<TextView>()
    private var all: List<Channel> = emptyList()
    private var groups: Map<String, List<Channel>> = emptyMap()
    private var selectedName = ALL_NAME
    private var seenFavVersion = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Favs.init(this)
        seenFavVersion = Favs.version

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

        emptyHint = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.parseColor("#9FB3C8"))
            setPadding(0, dp(24), 0, dp(24))
            text = "还没有收藏。选中频道后,按住遥控器确认键 3 秒即可加入收藏。"
            visibility = View.GONE
        }
        root.addView(emptyHint)

        val span = (resources.displayMetrics.widthPixels / (190 * resources.displayMetrics.density))
            .toInt().coerceAtLeast(2)
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, span)
            adapter = channelAdapter
            (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(8), 0, dp(24))
        }
        root.addView(grid, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        setContentView(root)
        load()
    }

    override fun onResume() {
        super.onResume()
        // 在播放页改过收藏,回来时刷新
        if (Favs.version != seenFavVersion) {
            seenFavVersion = Favs.version
            if (all.isNotEmpty()) select(selectedName)
        }
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
        for (name in listOf(ALL_NAME, FAV_NAME) + groups.keys) {
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
        select(ALL_NAME)
        tabViews.firstOrNull()?.requestFocus()
    }

    private fun select(name: String) {
        selectedName = name
        val list = when (name) {
            ALL_NAME -> all
            FAV_NAME -> Favs.list(all)
            else -> groups[name].orEmpty()
        }
        Store.list = list
        channelAdapter.submit(list)
        emptyHint.visibility = if (name == FAV_NAME && list.isEmpty()) View.VISIBLE else View.GONE
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

    private fun toggleFav(ch: Channel) {
        val added = Favs.toggle(ch)
        seenFavVersion = Favs.version
        Toast.makeText(
            this,
            if (added) "已加入收藏:${ch.name}" else "已取消收藏:${ch.name}",
            Toast.LENGTH_SHORT
        ).show()
        if (selectedName == FAV_NAME) {
            select(FAV_NAME)
            grid.post { grid.getChildAt(0)?.requestFocus() }
        }
    }

    private fun open(ch: Channel) {
        Store.index = Store.list.indexOf(ch).coerceAtLeast(0)
        startActivity(Intent(this, PlayerActivity::class.java))
    }
}
