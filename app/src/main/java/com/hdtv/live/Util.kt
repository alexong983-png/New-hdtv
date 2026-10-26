package com.hdtv.live

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun rounded(
    fill: Int,
    radiusPx: Float,
    strokeColor: Int = Color.TRANSPARENT,
    strokePx: Int = 0
): GradientDrawable = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radiusPx
    if (strokePx > 0) setStroke(strokePx, strokeColor)
}

object Store {
    var all: List<Channel> = emptyList()
    var list: List<Channel> = emptyList()
    var index: Int = 0
}

const val ALL_NAME = "全部"
const val FAV_NAME = "★ 收藏"

/** 收藏:保存在本机,用频道链接作为唯一标识 */
object Favs {
    private var prefs: SharedPreferences? = null
    private var set: MutableSet<String> = HashSet()
    var version = 0
        private set

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences("hdtv", Context.MODE_PRIVATE)
        prefs = p
        set = HashSet(p.getStringSet("fav", emptySet()) ?: emptySet())
    }

    fun isFav(ch: Channel): Boolean = set.contains(ch.url)

    /** 返回 true 表示已加入收藏,false 表示已取消 */
    fun toggle(ch: Channel): Boolean {
        val added = if (set.contains(ch.url)) {
            set.remove(ch.url)
            false
        } else {
            set.add(ch.url)
            true
        }
        version++
        prefs?.edit()?.putStringSet("fav", HashSet(set))?.apply()
        return added
    }

    fun list(all: List<Channel>): List<Channel> = all.filter { set.contains(it.url) }
}

fun Channel.label(): String = (if (Favs.isFav(this)) "★ " else "") + name

/** 检测遥控器确认键长按(默认 3 秒) */
class OkHold(private val holdMs: Long = 3000, private val onHold: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var fired = false
    private var active = false
    private val run = Runnable {
        fired = true
        onHold()
    }

    fun down(repeatCount: Int) {
        if (repeatCount == 0 && !active) {
            active = true
            fired = false
            handler.postDelayed(run, holdMs)
        }
    }

    /** 返回 true 表示刚才是长按(已处理);false 表示短按 */
    fun up(): Boolean {
        handler.removeCallbacks(run)
        val wasHold = fired
        active = false
        fired = false
        return wasHold
    }
}

/** 没有台标时,显示频道名首字母的色块 */
class InitialsDrawable(name: String) : Drawable() {
    private val text: String
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    init {
        val t = name.trim()
        val words = t.split(' ').filter { it.isNotEmpty() }
        val raw = if (words.size >= 2 && words[0][0].code < 128 && words[1][0].code < 128) {
            "${words[0][0]}${words[1][0]}"
        } else {
            t.take(2)
        }
        text = raw.uppercase()
        val palette = intArrayOf(
            0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFF00897B.toInt(),
            0xFFF4511E.toInt(), 0xFF6D4C41.toInt(), 0xFF3949AB.toInt()
        )
        bgPaint.color = palette[(t.hashCode() and 0x7fffffff) % palette.size]
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        canvas.drawRoundRect(RectF(b), 12f, 12f, bgPaint)
        textPaint.textSize = b.height() * 0.38f
        val y = b.exactCenterY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, b.exactCenterX(), y, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        textPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
